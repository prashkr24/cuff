package com.srineere.airbridge

import java.util.Calendar
import java.util.TimeZone

/** What a vibration does, independent of how hard it does it. */
internal enum class RelayPattern(val id: String, val pulses: Int?) {
    ONE("one", 1),
    TWO("two", 2),
    /** Repeats until the phone stops ringing, so it suits calls only. */
    RING("ring", null),
    SILENT("silent", 0);

    companion object {
        fun parse(id: String): RelayPattern = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown vibration pattern")
    }
}

/**
 * A pattern and the strength it plays at, stored as `pattern:strength`.
 *
 * The Air has exactly two strengths, [LOW_STRENGTH] and [FULL_STRENGTH], and
 * every pattern can use either, so the two are chosen separately rather than
 * enumerated as a combined list.
 */
internal data class RelayPreset(
    val pattern: RelayPattern,
    val strength: Int = FULL_STRENGTH,
) {
    init {
        require(strength == LOW_STRENGTH || strength == FULL_STRENGTH) {
            "The Air has two vibration strengths."
        }
    }

    val id: String get() = if (silent) RelayPattern.SILENT.id else "${pattern.id}:$strength"

    /** The strength to ask the Air for, or null when nothing is sent. */
    val intensity: Int? get() = if (silent) null else strength

    val silent: Boolean get() = pattern == RelayPattern.SILENT

    /** A repeat has nothing to stop it unless a call is still ringing. */
    val callsOnly: Boolean get() = pattern == RelayPattern.RING

    companion object {
        const val LOW_STRENGTH = 2
        const val FULL_STRENGTH = 3

        val MUTE = RelayPreset(RelayPattern.SILENT)
        val LOW = RelayPreset(RelayPattern.ONE, LOW_STRENGTH)
        val HIGH = RelayPreset(RelayPattern.ONE, FULL_STRENGTH)
        val DOUBLE = RelayPreset(RelayPattern.TWO, FULL_STRENGTH)
        val RING = RelayPreset(RelayPattern.RING, FULL_STRENGTH)

        /** Settings written before a vibration had a strength of its own. */
        private val legacy = mapOf(
            "single" to HIGH, "low" to LOW, "high" to HIGH,
            "double" to DOUBLE, "ring" to RING, "mute" to MUTE,
        )

        fun parse(id: String): RelayPreset {
            legacy[id]?.let { return it }
            val parts = id.split(':')
            val pattern = RelayPattern.parse(parts.firstOrNull().orEmpty())
            if (pattern == RelayPattern.SILENT) return MUTE
            val strength = parts.getOrNull(1)?.toIntOrNull()
                ?: throw IllegalArgumentException("Unknown vibration strength")
            return RelayPreset(pattern, strength)
        }
    }
}

/** [callPreset] null uses [RelayRuleConfig.defaultCallPreset] for this app's calls. */
internal data class RelayAppRule(
    val enabled: Boolean,
    val preset: RelayPreset? = null,
    val callPreset: RelayPreset? = null,
    /** Its own ring cadence; null uses the shared one. */
    val ring: RelayRingSettings? = null,
)
/**
 * A WhatsApp chat the user saved. [kind] is "shortcut" (stable conversation ID) or
 * "name" (exact chat name); [preset] null uses the app vibration. Labels stay on this phone.
 */
internal data class SavedChat(
    val packageName: String,
    val label: String,
    val kind: String = "shortcut",
    val preset: RelayPreset? = null,
)
/**
 * A caller the user saved, with the vibration their calls get. [preset] null
 * uses the app's call vibration; Silent is how a specific caller is muted.
 * [label] is a display name kept on this phone.
 */
internal data class SavedCaller(
    val packageName: String,
    val label: String,
    val preset: RelayPreset? = null,
    /** The number, so a contact with several can tell its rules apart. */
    val detail: String? = null,
    /** Its own ring cadence; null uses the app's, then the shared one. */
    val ring: RelayRingSettings? = null,
)

internal data class RelayMatch(val preset: RelayPreset, val specificity: Int)
internal data class RelaySilence(
    val pauseUntil: Long = 0,
    val quietEnabled: Boolean = false,
    val startMinute: Int = 1320,
    val endMinute: Int = 420,
    /**
     * Calls still reach the wrist inside the quiet window; everything else
     * waits for morning.
     *
     * The window exists so a night of messages cannot wake you, and that is
     * a different wish from not wanting to know the phone is ringing. A pause
     * is untouched by this: it is an answer to "not now" given by hand a few
     * minutes ago, and a window set once months ago does not outrank it.
     */
    val callsStillRing: Boolean = false,
) {
    init {
        require(pauseUntil >= -1 && startMinute in 0..1439 && endMinute in 0..1439)
        require(!quietEnabled || startMinute != endMinute) { "Choose different start and end times" }
    }
    /**
     * Why the band must stay still, or null when it may buzz. [isCall] is the
     * one thing [callsStillRing] can let through the quiet window.
     */
    fun reason(now: Long, zone: TimeZone = TimeZone.getDefault(), isCall: Boolean = false): String? {
        if (pauseUntil == -1L || pauseUntil > now) return "paused"
        if (!quietEnabled) return null
        val local = Calendar.getInstance(zone).apply { timeInMillis = now }
        val minute = local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE)
        val quiet = if (startMinute < endMinute) minute >= startMinute && minute < endMinute
            else minute >= startMinute || minute < endMinute
        if (!quiet) return null
        return if (isCall && callsStillRing) null else "quiet_hours"
    }

    /**
     * When [reason] could next answer differently of its own accord, or null
     * when it never will.
     *
     * The relay used to look every minute for the rest of its life, whether
     * or not there was anything to find. Nothing here changes without a
     * boundary to change at: a pause ends at its own end, a quiet window has
     * two edges a day, and an indefinite pause and no quiet hours have none
     * between them.
     *
     * Only the notification's wording rides on this. Every decision about
     * whether to buzz asks [reason] against the clock at the time, so a
     * wake-up that arrives late -- the handler's delay does not run down in
     * deep sleep -- leaves the band behaving correctly and the text stale.
     */
    fun nextChange(now: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        // A pause outranks the quiet, so while one runs the only thing that
        // can change the answer is the pause reaching its end.
        if (pauseUntil == -1L) return null
        if (pauseUntil > now) return pauseUntil
        if (!quietEnabled) return null
        return minOf(nextAt(now, zone, startMinute), nextAt(now, zone, endMinute))
    }

    /**
     * The next time it is [minuteOfDay] locally. Stepped by a day rather than
     * by 24 hours, so the two clock changes a year do not move the boundary
     * an hour off the wall clock the window was set against.
     */
    private fun nextAt(now: Long, zone: TimeZone, minuteOfDay: Int): Long {
        val at = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (at.timeInMillis <= now) at.add(Calendar.DAY_OF_MONTH, 1)
        return at.timeInMillis
    }
}

internal data class RelayRuleConfig(
    // The Air has two strengths; a notification you want to feel uses the
    // stronger one, and a call never uses anything else.
    val defaultPreset: RelayPreset = RelayPreset.HIGH,
    /** The phone's own ring. */
    val defaultCallPreset: RelayPreset = RelayPreset.RING,
    /**
     * What an app's call rings like when the app has said nothing. Separate
     * from the phone's because a Discord call and a phone call are not the
     * same event; before this they shared one value, so telling them apart
     * meant setting every app by hand.
     */
    val defaultAppCallPreset: RelayPreset = RelayPreset.RING,
    /**
     * The two buzzes with no notification behind them.
     *
     * Separate from [defaultPreset] because they are not an app getting
     * through: a goal crossed once a day and an hourly reminder are the
     * app's own doing, and wanting them to feel different from a message
     * is the whole reason to have them at all.
     */
    val stepGoalPreset: RelayPreset = RelayPreset.DOUBLE,
    val hourlyNudgePreset: RelayPreset = RelayPreset.LOW,
    val apps: Map<String, RelayAppRule> = emptyMap(),
    val chats: Map<String, SavedChat> = emptyMap(),
    val silence: RelaySilence = RelaySilence(),
    val ring: RelayRingSettings = RelayRingSettings(),
    val callers: Map<String, SavedCaller> = emptyMap(),
    /** Only saved callers ring; everyone else is silent. */
    val onlySavedCallers: Boolean = false,
    val everyoneElseSilent: Boolean = false,
) {
    val selectedPackages: Set<String> get() = apps.filterValues { it.enabled }.keys

    /**
     * How these calls repeat, most specific first. A caller's own cadence
     * outranks the app's for the same reason their vibration does: it is the
     * more specific rule, and a cadence is how one of them is told from
     * another by feel alone, without looking at the phone.
     */
    fun ringFor(packageName: String, callerId: String? = null): RelayRingSettings =
        callerId?.let { callers[it]?.ring } ?: apps[packageName]?.ring ?: ring

    fun match(
        packageName: String,
        identities: Set<String>,
        kind: RelayAlertKind = RelayAlertKind.MESSAGE,
        callerId: String? = null,
        // Which package answers the phone is a runtime fact, so it is passed
        // in rather than assumed: plenty of people do not use Google's dialer.
        dialer: String = RelayRuleStore.DIALER,
    ): RelayMatch? {
        val app = apps[packageName]?.takeIf { it.enabled } ?: return null
        if (kind == RelayAlertKind.CALL) {
            // A call is from whoever is calling: saved-chat, burst and tone rules
            // all describe messages and never apply here.
            val caller = callerId?.let { callers[it] }
            // "Only saved callers" cannot silence a call it could not attribute
            // to anyone: that would drop every call from an app that does not
            // say who is calling, which is not what the setting promises.
            if (onlySavedCallers && caller == null && callerId != null) return null
            val preset = caller?.preset ?: app.callPreset
                ?: if (packageName == dialer) defaultCallPreset else defaultAppCallPreset
            // A caller's own rule outranks the app's.
            return if (preset.silent) null else RelayMatch(preset, if (caller != null) 3 else 2)
        }
        val whatsapp = packageName in RelayConversationPolicy.packages
        val saved = if (whatsapp) identities.mapNotNull { chats[it] } else emptyList()
        // "Everyone else: Silent" lets only saved chats through.
        if (whatsapp && everyoneElseSilent && saved.isEmpty()) return null
        val overrides = saved.mapNotNull { it.preset }
        // A saved mute always wins if Android supplies multiple identity forms.
        if (overrides.any { it.silent }) return null
        val preset = overrides.firstOrNull() ?: app.preset ?: defaultPreset
        if (preset.silent) return null
        return RelayMatch(preset, if (overrides.isEmpty()) 0 else 1)
    }
}

/** Latest wins ties; a person-specific rule outranks an app default. */
internal data class RelayBurstChoice(
    val match: RelayMatch,
    val receivedAt: Long,
    /** Kept so a burst waiting out its cooldown still knows if a call is in it. */
    val kind: RelayAlertKind = RelayAlertKind.MESSAGE,
) {
    fun prefer(other: RelayBurstChoice): RelayBurstChoice =
        if (other.match.specificity > match.specificity ||
            (other.match.specificity == match.specificity && other.receivedAt >= receivedAt)) other else this
}

/** Migration also fails closed after backup/transfer omitted private rule settings. */
internal fun migrateRelayRules(packages: Set<String>, style: String, migratedBefore: Boolean): RelayRuleConfig =
    if (migratedBefore) RelayRuleConfig() else RelayRuleConfig(
        defaultPreset = if (style == "double") RelayPreset.DOUBLE else RelayPreset.HIGH,
        apps = packages.associateWith { RelayAppRule(true) },
    )
