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
    /**
     * The morning's readiness, when there is one.
     *
     * Handed in rather than worked out here: it is remembered once a
     * morning, and a report that computed its own would be the second
     * answer this app gives to the same question before breakfast.
     */
    val readiness: Readiness? = null,
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
            // The figure when it has earned one, the sentence when it has
            // not. A readiness that is still calibrating must not become a
            // silence here: the moved-or-not phrase was the whole report
            // before readiness existed and is still true.
            readiness?.score?.let { return "readiness is $it" }
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
    readiness: Readiness? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): MorningReport {
    val today = LocalDate.now(zone).toEpochDay()
    val yesterday = today - 1
    val week = snapshot.steps.filter { it.day in (yesterday - 6)..yesterday }.sortedBy { it.day }
    // The night that ended this morning, which Health Connect files under
    // the day it ended -- and only that one. The newest night on file is not
    // the same question: with the band two days out of sync it is the night
    // before last, and the report would open "You slept 7h 12m" about a
    // night the reader has already lived two days past. HealthData.lastNight
    // makes the same check for the same reason.
    val night = snapshot.nights.maxByOrNull { it.day }?.takeIf { it.day == today }
    return MorningReport(
        day = today,
        night = night,
        score = night?.let { sleepScoreOf(it, snapshot.nights) },
        resting = trendOf(snapshot.restingHeartRate),
        hrv = trendOf(snapshot.hrv),
        yesterdaySteps = week.firstOrNull { it.day == yesterday }?.value?.roundToInt()?.toLong(),
        weekAverage = week.map { it.value }.takeIf { it.isNotEmpty() }?.average(),
        stepGoal = goals.steps,
        week = week,
        readiness = readiness,
    )
}
