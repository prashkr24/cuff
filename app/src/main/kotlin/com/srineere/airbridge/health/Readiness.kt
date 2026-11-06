package com.srineere.airbridge.health

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One of the four things readiness stands on.
 *
 * Never the total without its parts. That is the rule the sleep score
 * already keeps, and it is the only thing that makes a composite
 * defensible: a reader who disagrees with the number can see which row
 * caused it. A part with nothing behind it says so and is left out of the
 * total rather than scoring zero.
 */
data class ReadinessPart(
    val name: String,
    /** What it is measured against, when that is not obvious. */
    val note: String?,
    val value: Double?,
    val unit: String,
    val decimals: Int,
    /** The person's own usual range for this part. */
    val usual: Band?,
    /** Points it moved the total, signed. Zero when it is missing. */
    val push: Double,
    /** How much of the total it is allowed to move, before anything is read. */
    val weight: Double,
) {
    val present: Boolean get() = value != null && usual != null
}

/**
 * Where this morning sits against your own mornings, as a figure.
 *
 * Fifty is "like you usually are", not "like an average person". There is
 * no population anywhere in this number, which is both its whole value and
 * the reason it is withheld until there are enough nights to have a usual.
 *
 * [score] is null while calibrating. Sixty nights is sixty nights: before
 * that the band is noise, and a confident 68 built on nine of them is
 * exactly the clinical claim the rest of this app refuses to make. The
 * parts are shown throughout, because they are measurements either way.
 */
data class Readiness(
    val score: Int?,
    /** The readiness series, for the band and the chart. */
    val trend: Trend?,
    val parts: List<ReadinessPart>,
    val days: Int,
) {
    val calibrating: Boolean get() = score == null
    val standing: List<ReadinessPart> get() = parts.filter { it.present }

    /** Where this morning sits, in the words the rest of the tab uses. */
    val word: String
        get() {
            val trend = trend ?: return "calibrating"
            return when {
                trend.latest in trend.usual -> "like your usual"
                trend.latest > trend.usual.high -> "above your usual"
                else -> "below your usual"
            }
        }

    companion object {
        /**
         * Nights before a figure is shown.
         *
         * Two months. A band needs enough readings that one bad week cannot
         * define it, and sixty is where the usual range of these four stops
         * moving much when another night arrives.
         */
        const val NEEDED = 60

        /**
         * How far one standard deviation moves the figure.
         *
         * Fifteen points, so a day a full deviation above your baseline
         * reads 65 and one below reads 35. Picked so the ordinary range of
         * a person's own mornings fills the middle of the scale rather than
         * the ends: a readiness that spends all year between 48 and 52 is
         * a readiness nobody can use.
         */
        const val POINTS_PER_SD = 15.0
    }
}

/**
 * Readiness for every day there is enough to compute one.
 *
 * Each day is scored against the baseline of the whole window rather than
 * against a trailing one. Slightly circular, and deliberately: a trailing
 * baseline makes every day's figure depend on how much history happened to
 * be loaded, so the same morning would read differently on a tab showing
 * thirty days and one showing ninety.
 */
internal fun readinessSeries(snapshot: HealthSnapshot): List<Reading> {
    val hrv = Baseline.of(snapshot.hrv) ?: return emptyList()
    val resting = Baseline.of(snapshot.restingHeartRate)
    val sleep = Baseline.of(snapshot.nights.map { Reading(it.day, it.asleep.toMinutes().toDouble()) })
    val load = loadRatios(snapshot.steps)
    val loadBase = Baseline.of(load)

    val days = (snapshot.hrv.map { it.day } + snapshot.restingHeartRate.map { it.day })
        .distinct().sorted()
    val hrvByDay = snapshot.hrv.associate { it.day to it.value }
    val restingByDay = snapshot.restingHeartRate.associate { it.day to it.value }
    val sleepByDay = snapshot.nights.associate { it.day to it.asleep.toMinutes().toDouble() }
    val loadByDay = load.associate { it.day to it.value }

    return days.mapNotNull { day ->
        val pushes = mutableListOf<Pair<Double, Double>>()
        hrvByDay[day]?.let { pushes += WEIGHT_HRV to hrv.z(it) }
        restingByDay[day]?.let { value ->
            // Lower is better, so the sign is turned over.
            resting?.let { pushes += WEIGHT_RESTING to -it.z(value) }
        }
        sleepByDay[day]?.let { value -> sleep?.let { pushes += WEIGHT_SLEEP to it.z(value) } }
        loadByDay[day]?.let { value ->
            // A ratio far from one in either direction is the tiring thing:
            // a week far above the month is a hard week, and a week far
            // below it is a week off. Only the first costs readiness.
            loadBase?.let { pushes += WEIGHT_LOAD to -maxOf(it.z(value), 0.0) }
        }
        if (pushes.isEmpty()) return@mapNotNull null
        // Rescaled by the weight actually present, so a day missing its
        // sleep is not a day scored as though sleep were zero.
        val weight = pushes.sumOf { it.first }
        val z = pushes.sumOf { it.first * it.second } / weight
        Reading(day, (50.0 + Readiness.POINTS_PER_SD * z).coerceIn(0.0, 100.0))
    }
}

/** This morning's readiness, and the four rows it stands on. */
fun readinessOf(snapshot: HealthSnapshot): Readiness {
    val series = readinessSeries(snapshot)
    val trend = trendOf(series)
    val hrv = Baseline.of(snapshot.hrv)
    val resting = Baseline.of(snapshot.restingHeartRate)
    val sleepReadings = snapshot.nights.map { Reading(it.day, it.asleep.toMinutes().toDouble()) }
    val sleep = Baseline.of(sleepReadings)
    val load = loadRatios(snapshot.steps)
    val loadBase = Baseline.of(load)

    val parts = listOf(
        part("HRV", "weighted most", snapshot.hrv.lastOrNull()?.value, "ms", 0, hrv, WEIGHT_HRV, 1),
        part(
            "Resting HR", null, snapshot.restingHeartRate.lastOrNull()?.value, "bpm", 0,
            resting, WEIGHT_RESTING, -1,
        ),
        part(
            "Last night", null, sleepReadings.maxByOrNull { it.day }?.value, "min", 0,
            sleep, WEIGHT_SLEEP, 1,
        ),
        part(
            "Recent load", "7 against 28 days", load.lastOrNull()?.value, "", 2,
            loadBase, WEIGHT_LOAD, -1,
        ),
    )
    return Readiness(
        // Withheld, not guessed. The parts above it are shown either way.
        score = if (series.size >= Readiness.NEEDED) {
            series.last().value.roundToInt()
        } else {
            null
        },
        trend = trend,
        parts = parts,
        days = series.size,
    )
}

private fun part(
    name: String,
    note: String?,
    value: Double?,
    unit: String,
    decimals: Int,
    baseline: Baseline?,
    weight: Double,
    way: Int,
): ReadinessPart = ReadinessPart(
    name = name,
    note = note,
    value = value,
    unit = unit,
    decimals = decimals,
    usual = baseline?.usual,
    push = if (value == null || baseline == null) 0.0
    else way * baseline.z(value) * weight * Readiness.POINTS_PER_SD,
    weight = weight,
)

/**
 * Seven days of steps against twenty-eight, which is the acute-to-chronic
 * ratio athletics uses to spot a week that got ahead of itself.
 *
 * Steps rather than workout minutes because the snapshot carries a month of
 * steps and only a week of sessions: a chronic load you cannot see four
 * weeks of is not a chronic load.
 */
private fun loadRatios(steps: List<Reading>): List<Reading> {
    if (steps.size < 28) return emptyList()
    val byDay = steps.associate { it.day to it.value }
    return steps.sortedBy { it.day }.mapNotNull { reading ->
        val acute = (0 until 7).mapNotNull { byDay[reading.day - it] }
        val chronic = (0 until 28).mapNotNull { byDay[reading.day - it] }
        if (acute.size < 5 || chronic.size < 20) return@mapNotNull null
        val base = chronic.average().takeIf { it > 0 } ?: return@mapNotNull null
        Reading(reading.day, acute.average() / base)
    }
}

/** A mean and a spread, which is all a z-score needs. */
private class Baseline(val mean: Double, val deviation: Double, val usual: Band) {
    fun z(value: Double): Double = if (deviation <= 0.0) 0.0 else (value - mean) / deviation

    companion object {
        fun of(readings: List<Reading>): Baseline? {
            if (readings.size < 3) return null
            val values = readings.map { it.value }
            val mean = values.average()
            val deviation = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            // Half a deviation either side: the same smallest worthwhile
            // change every other band on this tab is drawn from.
            val half = deviation / 2
            return Baseline(mean, deviation, Band(mean - half, mean + half))
        }
    }
}

// HRV carries most of it, which is what the published method does: it is
// the one of the four that moves first when something is wrong. Load is a
// nudge rather than a verdict -- a hard week is a choice, not an illness.
private const val WEIGHT_HRV = 0.40
private const val WEIGHT_RESTING = 0.25
private const val WEIGHT_SLEEP = 0.25
private const val WEIGHT_LOAD = 0.10
