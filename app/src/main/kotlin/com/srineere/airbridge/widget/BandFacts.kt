package com.srineere.airbridge.widget

import android.content.Context
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.RelayActivity
import com.srineere.airbridge.RelayHistory
import com.srineere.airbridge.RelayRuleStore
import java.util.Calendar
import java.util.TimeZone

/**
 * Everything a band widget draws, and not one reading more.
 *
 * [batteryAt] is as load-bearing as [battery]. The Air answers one BLE
 * session at a time and Google Health holds that session whenever it syncs,
 * so a widget that asked the band for a fresh charge every half hour would
 * quietly cost a day of steps and sleep. No widget opens a session: each
 * draws the last charge the *app* read, with its age beside it.
 */
/**
 * One line of a buzz list: who it was, when, and whether it arrived.
 *
 * [who] is already resolved -- a person from the rules, an app from the
 * package manager -- because nothing stored on disk is ever a name, and
 * the resolving happens where the widget is drawn.
 */
data class BuzzRow(
    val who: String,
    val atMillis: Long,
    val arrived: Boolean,
)

data class BandFacts(
    val battery: Int? = null,
    /** Epoch millis of that reading, or 0 for never. */
    val batteryAt: Long = 0,
    val relaying: Boolean = false,
    /** Epoch millis, or -1 for paused with no end, or 0 for not paused. */
    val pauseUntil: Long = 0,
    val quietEnabled: Boolean = false,
    val quietStartMinute: Int = 1320,
    val quietEndMinute: Int = 420,
    val lastBuzzAt: Long = 0,
    /**
     * Who or what raised it, worked out by [buzzWho] when this is read.
     *
     * Null only when there has been no buzz at all. It used to be null
     * whenever the entry had no saved caller behind it, which is every
     * ordinary app notification -- so the card fell through to the word
     * "Message" and the one thing anybody wanted off it, which app, was
     * the one thing it would not say.
     */
    val lastBuzzWho: String? = null,
    val lastBuzzWasCall: Boolean = false,
    /**
     * Whether the app raised this one itself, rather than relaying it.
     *
     * A step goal and an hourly nudge are neither a call nor a message, and
     * [lastBuzzWho] already names them: the card drops the kind word for
     * these rather than filing a goal crossed at ten in the morning under
     * "Message".
     */
    val lastBuzzWasOwn: Boolean = false,
    val buzzesToday: Int = 0,
    /**
     * The last few entries, newest first, for the faces with room to list
     * them.
     *
     * Attempts as well as arrivals, which is why this is not simply the
     * delivered ones [buzzesToday] counts. A list that showed only what
     * got through would make the count look like the whole story, and the
     * rows somebody actually wants to see on a home screen are the ones
     * that did not: a buzz that failed is news, a buzz that worked is a
     * wrist that already told you.
     *
     * Kept short deliberately. The tallest face shows six, and a widget
     * holds its whole state in a `RemoteViews` the launcher has to carry,
     * so there is no sense resolving forty app labels to draw six rows.
     */
    val recent: List<BuzzRow> = emptyList(),
) {
    fun pausedAt(now: Long): Boolean = pauseUntil == PAUSED_INDEFINITELY || pauseUntil > now

    /** What the state dot says, in the order the dot has to decide it. */
    fun state(now: Long): BandState = when {
        !relaying -> BandState.Off
        pausedAt(now) -> BandState.Paused
        quietNow(now) -> BandState.Quiet
        else -> BandState.Relaying
    }

    fun quietNow(now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (!quietEnabled) return false
        val local = Calendar.getInstance(zone).apply { timeInMillis = now }
        val minute = local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE)
        return if (quietStartMinute < quietEndMinute) {
            minute >= quietStartMinute && minute < quietEndMinute
        } else {
            minute >= quietStartMinute || minute < quietEndMinute
        }
    }

    /**
     * True once the charge is old enough that it should be drawn dim.
     *
     * A day, because the Air lasts about a week: a reading from this morning
     * is still true to within a percent or two, and one from Tuesday is a
     * number with a date on it rather than a number.
     */
    fun batteryStale(now: Long): Boolean =
        batteryAt == 0L || now - batteryAt > STALE_MILLIS

    companion object {
        const val PAUSED_INDEFINITELY = -1L
        const val STALE_MILLIS = 24L * 60 * 60 * 1000
    }
}

enum class BandState { Relaying, Quiet, Paused, Off }

/**
 * The last charge the app read, kept so a widget never has to ask the band.
 *
 * Written from wherever the app genuinely reads the Air -- today that is the
 * Device tab -- and read from everywhere else. Deliberately not a cache with
 * its own refresh: nothing here is allowed to take the session.
 */
object BandStore {
    private const val PREFS = "calendar_relay"
    private const val KEY_PERCENT = "band_battery"
    private const val KEY_AT = "band_battery_at"

    /** Called when a real read of the Air came back. */
    fun rememberBattery(context: Context, percent: Int, atMillis: Long = System.currentTimeMillis()) {
        if (percent !in 0..100) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_PERCENT, percent)
            .putLong(KEY_AT, atMillis)
            .apply()
    }

    fun read(context: Context): BandFacts {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val config = RelayRuleStore.read(context)
        val silence = config.silence
        val history = runCatching { RelayHistory.read(context) }.getOrDefault(emptyList())

        // "verified" is the stage the band acknowledged. Queued and failed
        // entries are attempts, and a widget that counted them would report
        // buzzes that nobody's wrist ever felt.
        val delivered = history.filter { it.stage == VERIFIED }
        val last = delivered.maxByOrNull { it.atMillis }
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return BandFacts(
            battery = prefs.getInt(KEY_PERCENT, -1).takeIf { it in 0..100 },
            batteryAt = prefs.getLong(KEY_AT, 0L),
            relaying = CalendarRelayService.isWanted(context),
            pauseUntil = silence.pauseUntil,
            quietEnabled = silence.quietEnabled,
            quietStartMinute = silence.startMinute,
            quietEndMinute = silence.endMinute,
            lastBuzzAt = last?.atMillis ?: 0L,
            // The history holds a one-way caller id, never a name, and a
            // package name, never an app's. Both are resolved here, when
            // the widget is drawn -- the person from the rules, the app
            // from the package manager -- exactly as the screens do it, so
            // nothing stored anywhere is a person or an app's own name.
            lastBuzzWho = last?.let { entry ->
                buzzWho(
                    callerLabel = entry.callerId?.let { config.callers[it]?.label },
                    sourcePackage = entry.sourcePackage,
                    commandId = entry.commandId,
                    appLabel = { appLabelOf(context, it) },
                )
            },
            // Stored on the entry now. Reading it off the preset called a
            // silenced caller's double buzz a message, and an app whose
            // messages are set to Ring a call.
            lastBuzzWasCall = last?.wasCall == true,
            // No package behind it is what the app's own three buzzes look
            // like: nothing relayed them, so neither word applies.
            lastBuzzWasOwn = last != null && last.sourcePackage == null,
            buzzesToday = delivered.count { it.atMillis >= midnight },
            recent = buzzRows(history, MOST_ROWS) { entry ->
                buzzWho(
                    callerLabel = entry.callerId?.let { config.callers[it]?.label },
                    sourcePackage = entry.sourcePackage,
                    commandId = entry.commandId,
                    appLabel = { appLabelOf(context, it) },
                )
            },
        )
    }

    /** What the tallest face asks for, so nothing resolves more than that. */
    private const val MOST_ROWS = 6
}

/** The stage the band acknowledged. Everything else is an attempt. */
internal const val VERIFIED = "verified"

/**
 * The stages that mean a buzz was tried and did not arrive.
 *
 * Not every stage that is not [VERIFIED]. A queued entry has not finished
 * happening yet, and a duplicate or a cancelled one is the relay deciding
 * not to buzz rather than failing to — showing those as failures on a home
 * screen would report trouble where the app did exactly its job.
 */
private val ATTEMPTED = setOf("failed", "expired")

/**
 * The newest few entries worth showing, newest first.
 *
 * Arrivals and failures together, which is the point of the list: the
 * figure above it counts what got through, and the rows worth a glance
 * are the ones that did not. A buzz that worked has already told you.
 *
 * [who] resolves a name, and is called at most [limit] times — the
 * filtering and the sort happen first so that a hundred stored entries
 * never cost a hundred package-manager lookups to draw six rows.
 */
internal fun buzzRows(
    history: List<RelayActivity>,
    limit: Int,
    who: (RelayActivity) -> String,
): List<BuzzRow> = history
    .asSequence()
    .filter { it.stage == VERIFIED || it.stage in ATTEMPTED }
    .sortedByDescending { it.atMillis }
    .take(limit)
    .map { BuzzRow(who(it), it.atMillis, arrived = it.stage == VERIFIED) }
    .toList()

/**
 * Who or what raised a buzz, in the order `ActivityRow` decides it.
 *
 * The caller first, because a name is the most specific thing known about a
 * buzz; then the app that carried it; and then, for the three the app
 * raises itself, what it was. Nothing here is ever null: a widget that
 * cannot name a buzz still has to say something, and the package's last
 * dot-segment is a word where a blank is a bug.
 *
 * **This is written twice on purpose, and the two copies have to agree.**
 * `RelayState.label(entry)` in `ui/` is the same decision for the Alerts
 * screen, and it cannot be shared: that class is built around a config and
 * an installed-app list the screens hold open, and a widget redraw has
 * neither. A widget and a screen naming the same buzz differently is a bug
 * nobody reports, because each of them looks right on its own -- so if the
 * prefixes or the words below change, they change in both places.
 *
 * [appLabel] is passed in rather than looked up here so that the decision
 * stays a pure function: see [appLabelOf] for what the widget hands it.
 */
internal fun buzzWho(
    callerLabel: String?,
    sourcePackage: String?,
    commandId: String,
    appLabel: (String) -> String,
): String = when {
    callerLabel != null -> callerLabel
    sourcePackage != null -> appLabel(sourcePackage)
    // The two the app raises on its own schedule. `CalendarRelayService`
    // builds these ids as "$reason-$at", and `ActivityScreen` badges them
    // off the same two prefixes.
    commandId.startsWith(GOAL_PREFIX) -> "Step goal"
    commandId.startsWith(NUDGE_PREFIX) -> "Hourly nudge"
    // What is left is the Buzz button, whose ids begin "band-".
    else -> "Test vibration"
}

private const val GOAL_PREFIX = "goal-"
private const val NUDGE_PREFIX = "nudge-"

/**
 * One app's name, from the package manager and nothing else.
 *
 * `InstalledApps` in `ui/` answers the same question and is the wrong tool
 * here twice over: it builds every launchable app on the phone and
 * rasterises an icon for each, which is a hundred binder calls to put one
 * word on a card, and it is empty until this app has been opened -- which
 * a phone whose widget is redrawing at four in the morning has not been.
 *
 * Called from [BandStore.read], which the snapshot runs off the main
 * thread; a widget face must not do this itself. A package that has been
 * uninstalled, or that the manifest's `<queries>` does not let this app
 * see, falls back to its last dot-segment, which is the same answer
 * `RelayState.label` gives for an app it cannot find.
 */
@Suppress("DEPRECATION")
internal fun appLabelOf(context: Context, packageName: String): String = runCatching {
    val manager = context.packageManager
    manager.getApplicationInfo(packageName, 0).loadLabel(manager).toString()
}.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName.substringAfterLast('.')
