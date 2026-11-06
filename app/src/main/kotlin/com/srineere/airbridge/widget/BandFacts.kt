package com.srineere.airbridge.widget

import android.content.Context
import com.srineere.airbridge.CalendarRelayService
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
    /** The caller's saved label, or null when nothing named them. */
    val lastBuzzWho: String? = null,
    val lastBuzzWasCall: Boolean = false,
    val buzzesToday: Int = 0,
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
            // The history holds a one-way caller id, never a name. The name
            // is looked up from the rules when the widget is drawn, exactly
            // as the screens do it, so nothing stored here is a person.
            lastBuzzWho = last?.callerId?.let { config.callers[it]?.label },
            // Stored on the entry now. Reading it off the preset called a
            // silenced caller's double buzz a message, and an app whose
            // messages are set to Ring a call.
            lastBuzzWasCall = last?.wasCall == true,
            buzzesToday = delivered.count { it.atMillis >= midnight },
        )
    }

    private const val VERIFIED = "verified"
}
