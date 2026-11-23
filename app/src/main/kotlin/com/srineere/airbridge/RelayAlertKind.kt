package com.srineere.airbridge

import android.app.Notification
import android.os.Build

/** What a posted notification is, as far as the relay cares. */
internal enum class RelayAlertKind { CALL, MESSAGE }

/**
 * Recognizes a ringing call from metadata Android already exposes; no message
 * text is read.
 *
 * Only a [RelayAlertKind.CALL] may ring, and a ring repeats until the call ends,
 * so a false positive buzzes the wrist for the whole ring timeout. A sticky
 * notification alone is therefore never enough: an app must also mark the alert
 * as a call, ask for the full screen, or post it on a call channel.
 */
internal object RelayAlertClassifier {
    /** Channel IDs apps use while a call is still ringing. */
    private val ringingChannel = Regex("incoming|voip|ring", RegexOption.IGNORE_CASE)

    /**
     * Channel IDs for calls that are no longer ringing. Matching "call" alone
     * is not enough: a dialer's answered call sits on `phone_ongoing_call` and
     * a missed one on `phone_missed_call`, and buzzing the wrist through a
     * conversation you are already having is worse than missing the call.
     */
    private val settledChannel =
        Regex("ongoing|active|missed|voicemail|history", RegexOption.IGNORE_CASE)

    /**
     * Whether this channel is a call that has stopped ringing.
     *
     * Exposed because [RelayRingWatch.endsRing] asks the same question for
     * the opposite reason: this classifier uses it to refuse to ring, and
     * the ring watch uses it to stop one. Two copies of the same list
     * would be two chances to disagree about what a missed call looks
     * like.
     */
    fun settles(channelId: String): Boolean = settledChannel.containsMatchIn(channelId)

    fun classify(notification: Notification, ongoing: Boolean): RelayAlertKind = classify(
        category = notification.category,
        ongoing = ongoing,
        fullScreen = notification.fullScreenIntent != null,
        channelId = if (Build.VERSION.SDK_INT >= 26) notification.channelId else null,
    )

    internal fun classify(
        category: String?,
        ongoing: Boolean,
        fullScreen: Boolean,
        channelId: String?,
    ): RelayAlertKind {
        // A phone that is still ringing holds its notification up; a missed
        // call is an event, and an answered one is already in your hand.
        if (channelId != null && settledChannel.containsMatchIn(channelId)) return RelayAlertKind.MESSAGE
        if (!ongoing) return RelayAlertKind.MESSAGE
        val ringing = category == Notification.CATEGORY_CALL || fullScreen ||
            (channelId != null && ringingChannel.containsMatchIn(channelId))
        return if (ringing) RelayAlertKind.CALL else RelayAlertKind.MESSAGE
    }
}

/**
 * How a call repeats on the Air.
 *
 * The ring holds one secure session and posts a pulse every [gapMillis], so
 * the gap is a real pulse rhythm rather than the cost of a fresh connection.
 * The Air plays one pulse per change it notices, so a gap shorter than a pulse
 * merges buzzes into fewer, longer ones.
 */
internal data class RelayRingSettings(
    val gapMillis: Int = DEFAULT_GAP_MILLIS,
    val maxSeconds: Int = DEFAULT_MAX_SECONDS,
) {
    init {
        require(gapMillis in MINIMUM_GAP_MILLIS..5_000) {
            "Pulses must be $MINIMUM_GAP_MILLIS-5000 ms apart."
        }
        // Bounded below the relay's own 240 s command watchdog.
        require(maxSeconds in 10..180) { "Ring for 10-180 seconds." }
    }

    val maxMillis: Long get() = maxSeconds * 1_000L

    companion object {
        const val MINIMUM_GAP_MILLIS = 250
        const val DEFAULT_GAP_MILLIS = 700
        const val DEFAULT_MAX_SECONDS = 45
    }
}

/**
 * Whether a call is still ringing, decided from the audio mode rather than
 * from notifications.
 *
 * A notification is a poor witness: a locked phone replaces the ringing
 * notification with its full-screen call UI, which looks exactly like the call
 * going away. `MODE_RINGTONE` says the phone is ringing, and nothing else does.
 *
 * **Except on a phone with the ringer off, where nothing says it.** Telecom
 * takes ringing audio focus in order to play a ringtone; with no ringtone to
 * play it never asks, and the mode stays `MODE_NORMAL` for the whole call.
 * Every check below then reads a ringing phone as a phone that is not
 * ringing — and the symptom is precise: the wrist stays still through the
 * call and buzzes once, at the end, for the missed-call notification.
 * Reported from a Pixel 9a, and the reporter found the shape of it before
 * we did — setting any audible ringtone made it work.
 *
 * So the ringer is passed in wherever the mode alone would decide, and when
 * it is silenced the mode is treated as the non-witness it is.
 */
internal object RelayRingWatch {
    const val MODE_NORMAL = 0
    const val MODE_RINGTONE = 1
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3

    fun inCall(mode: Int) = mode == MODE_IN_CALL || mode == MODE_IN_COMMUNICATION

    /**
     * The ring may end only once the phone itself has stopped ringing.
     *
     * With the ringer silenced there is no `MODE_RINGTONE` to wait for, so
     * this would answer yes the instant a locked phone swapped the ringing
     * notification for its call UI — which is the whole bug. There, only
     * being in a call is proof the ringing is over; anything else leaves
     * the ring running, to be ended by [reason] when the call is answered,
     * by the missed-call notification, or by its own maximum.
     *
     * When the ringer is audible nothing about this changed. The path that
     * works today is the path that worked yesterday.
     */
    fun stopOnRemoval(mode: Int, ringerAudible: Boolean = true) =
        if (ringerAudible) mode != MODE_RINGTONE else inCall(mode)

    /**
     * Whether a notification arriving now means the call is over.
     *
     * The second witness the silenced case needs. A missed call and an
     * answered one each post their own notification -- `phone_missed_call`
     * and `phone_ongoing_call` on a Pixel -- and [RelayAlertClassifier]
     * already knows those channels, because it is careful not to ring for
     * them. The same knowledge ends a ring that has nothing else to end
     * it: without this, a declined call on a silent phone would buzz on
     * for the rest of its forty-five seconds.
     *
     * Only from the package that is ringing. A message arriving during a
     * call is not the call ending.
     */
    fun endsRing(ringingPackage: String, postedPackage: String, channelId: String?): Boolean =
        ringingPackage == postedPackage &&
            channelId != null &&
            RelayAlertClassifier.settles(channelId)

    /**
     * Why an in-progress ring should end, or null to keep ringing.
     *
     * [startMode] guards against apps that already hold the audio session
     * while they ring: only a change into an in-call mode is an answer.
     */
    fun reason(startMode: Int, sawRingtone: Boolean, mode: Int): String? = when {
        // The phone was ringing and has stopped: answered, declined, missed or
        // cancelled, it makes no difference to the wrist. Whichever state it
        // moved to, it is no longer a ringing phone.
        sawRingtone && mode != MODE_RINGTONE -> if (inCall(mode)) "answered" else "ended"
        // An app that rings while already holding the audio session never sets
        // MODE_RINGTONE, so for those only a move into a call counts.
        inCall(mode) && !inCall(startMode) -> "answered"
        else -> null
    }
}
