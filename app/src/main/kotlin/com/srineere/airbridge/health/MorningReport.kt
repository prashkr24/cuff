package com.srineere.airbridge.health

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one pushed moment of the day, assembled from figures that already
 * exist.
 *
 * It computes nothing new. Every number here is already on the Health tab,
 * which is the whole reason it is cheap to build and safe to miss: a
 * morning you sleep through costs you a notification, not a reading.
 *
 * Last night leads, because it is the only thing that happened since you
 * last looked. Then the morning's own readings against their own bands, and
 * then yesterday, closed off — a day you can still change belongs on the
 * tab, and a day that is finished belongs here once and then never again.
 */
data class MorningReport(
    val day: Long,
    val night: Night?,
    val score: SleepScore?,
    val resting: Trend?,
    val hrv: Trend?,
    val yesterdaySteps: Long?,
    val weekAverage: Double?,
    val stepGoal: Int,
    /** The seven days ending yesterday, for the bars. */
    val week: List<Reading>,
) {
    val sleepLength: String? get() = night?.asleep?.let { asHoursMinutes(it.toMinutes()) }

    /** Whether yesterday reached the step goal, or null with nothing to judge. */
    val metStepGoal: Boolean?
        get() = yesterdaySteps?.let { steps -> stepGoal > 0 && steps >= stepGoal }

    /**
     * Where this morning sits against the usual, as a phrase.
     *
     * Not a score out of a hundred. The Vitals screen refuses to invent one
     * for exactly the reason it would be wrong here too: a single number on
     * top of a band is a clinical claim, and a band is what the readings
     * actually support.
     */
    val standing: String
        get() {
            val moved = listOfNotNull(
                resting?.takeIf { it.recentHasMoved }?.let { "resting rate" },
                hrv?.takeIf { it.recentHasMoved }?.let { "HRV" },
            )
            return when {
                resting == null && hrv == null -> "no morning readings yet"
                moved.isEmpty() -> "you are about your usual"
                else -> "your " + moved.joinToString(" and ") + " has moved"
            }
        }

    /** The notification's first line, which is the whole report for most days. */
    val headline: String
        get() = sleepLength?.let { "You slept $it, and $standing" }
            ?: "No night was recorded, and $standing"

    /** Its second line: the two morning figures, then yesterday. */
    val line: String
        get() {
            val readings = listOfNotNull(
                resting?.let { "Resting ${it.latest.roundToInt()}" },
                hrv?.let { "HRV ${it.latest.roundToInt()}" },
            ).joinToString(", ")
            val steps = yesterdaySteps?.let { "Yesterday was %,d steps.".format(it) }
            return listOfNotNull(readings.takeIf { it.isNotEmpty() }?.plus("."), steps)
                .joinToString(" ")
                .ifEmpty { "Nothing has been written for this morning yet." }
        }

    /** True when there is enough to be worth waking somebody's lock screen. */
    val worthSending: Boolean
        get() = night != null || resting != null || hrv != null || yesterdaySteps != null

    companion object {
        /** How a figure sits against its own band, in words. */
        fun against(trend: Trend?): String = when {
            trend == null -> "no band yet"
            trend.latestIsUsual -> "inside the band"
            else -> {
                val edge = if (trend.latest > trend.usual.high) trend.usual.high
                else trend.usual.low
                val gap = abs(trend.latest - edge).roundToInt()
                val way = if (trend.latest > trend.usual.high) "over" else "under"
                if (gap == 0) "at the edge" else "$gap $way usual"
            }
        }

        fun asHoursMinutes(minutes: Long): String =
            if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
    }
}

/**
 * Builds the morning's report out of a snapshot the tab could have read.
 *
 * Yesterday rather than today: the report is sent in the morning, when
 * today is a few hours old and has nothing in it worth reading.
 */
fun morningReport(
    snapshot: HealthSnapshot,
    goals: Goals,
    zone: ZoneId = ZoneId.systemDefault(),
): MorningReport {
    val today = LocalDate.now(zone).toEpochDay()
    val yesterday = today - 1
    val week = snapshot.steps.filter { it.day in (yesterday - 6)..yesterday }.sortedBy { it.day }
    return MorningReport(
        day = today,
        // The night that ended this morning, which Health Connect files
        // under the day it ended.
        night = snapshot.nights.maxByOrNull { it.day },
        score = snapshot.nights.maxByOrNull { it.day }
            ?.let { sleepScoreOf(it, snapshot.nights) },
        resting = trendOf(snapshot.restingHeartRate),
        hrv = trendOf(snapshot.hrv),
        yesterdaySteps = week.firstOrNull { it.day == yesterday }?.value?.roundToInt()?.toLong(),
        weekAverage = week.map { it.value }.takeIf { it.isNotEmpty() }?.average(),
        stepGoal = goals.steps,
        week = week,
    )
}
