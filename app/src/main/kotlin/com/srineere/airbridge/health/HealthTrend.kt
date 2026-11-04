package com.srineere.airbridge.health

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * One reading, on one day. [day] is an epoch day, so a gap in the sequence is
 * a night with no reading rather than a zero -- the difference between the
 * band being off your wrist and your heart stopping.
 */
data class Reading(val day: Long, val value: Double)

/**
 * The middle half of your readings. Quartiles rather than mean plus a
 * standard deviation: one freak night after a flight would widen an SD band
 * enough to swallow the drift it was meant to reveal, and "half your nights
 * land here" is a sentence anyone can check against the chart.
 */
data class Band(val low: Double, val high: Double) {
    operator fun contains(value: Double): Boolean = value in low..high

    /** Where [value] sits on a 0..1 scale across [from]..[to], for a range bar. */
    fun position(value: Double, from: Double, to: Double): Double =
        if (to <= from) 0.0 else ((value - from) / (to - from)).coerceIn(0.0, 1.0)
}

/**
 * What a window of readings says. Every figure the Health tab prints about a
 * trend comes from here, so the screens hold no arithmetic of their own and
 * this file is the only thing that needs testing.
 */
data class Trend(
    val readings: List<Reading>,
    val average: Double,
    val latest: Double,
    val lowest: Double,
    val highest: Double,
    val usual: Band,
    /** Mean of the most recent few readings -- the "last 7 nights" figure. */
    val recent: Double,
    /** How that recent mean sits against the whole window. */
    val versusAverage: Double,
    /** Oldest third against newest third: the drift across the window. */
    val change: Double,
    /**
     * Half a standard deviation: the smallest change worth reacting to, the
     * threshold HRV-guided training uses. A single reading outside it is
     * noise; a run of them is not.
     */
    val smallestWorthwhileChange: Double,
) {
    /** True when the newest reading sits inside the usual band. */
    val latestIsUsual: Boolean get() = latest in usual

    /** True when the recent mean has moved further than noise explains. */
    val recentHasMoved: Boolean
        get() = abs(versusAverage) > smallestWorthwhileChange
}

/**
 * Builds a [Trend], or null when there is not enough to say anything. Two
 * readings can be averaged but cannot have a shape, so the floor is three --
 * below that the tab shows the number and no trend rather than a confident
 * line through a couple of points.
 */
fun trendOf(readings: List<Reading>, recentCount: Int = 7): Trend? {
    if (readings.size < 3) return null
    val ordered = readings.sortedBy { it.day }
    val values = ordered.map { it.value }
    val sorted = values.sorted()

    val average = values.average()
    val recentWindow = values.takeLast(recentCount.coerceAtLeast(1))
    val recent = recentWindow.average()

    // Thirds, not first point against last: one unusual night at either end
    // would otherwise set the whole direction of travel.
    val third = (values.size / 3).coerceAtLeast(1)
    val change = values.takeLast(third).average() - values.take(third).average()

    return Trend(
        readings = ordered,
        average = average,
        latest = values.last(),
        lowest = sorted.first(),
        highest = sorted.last(),
        usual = Band(quantile(sorted, 0.25), quantile(sorted, 0.75)),
        recent = recent,
        versusAverage = recent - average,
        change = change,
        smallestWorthwhileChange = standardDeviation(values, average) * 0.5,
    )
}

/** Linear interpolation between order statistics, the usual convention. */
internal fun quantile(sorted: List<Double>, fraction: Double): Double {
    require(sorted.isNotEmpty()) { "No readings to take a quantile of." }
    if (sorted.size == 1) return sorted.first()
    val index = fraction.coerceIn(0.0, 1.0) * (sorted.size - 1)
    val below = floor(index).toInt()
    val above = ceil(index).toInt()
    if (below == above) return sorted[below]
    return sorted[below] + (index - below) * (sorted[above] - sorted[below])
}

/** Sample standard deviation; zero when every reading is the same. */
internal fun standardDeviation(values: List<Double>, mean: Double): Double {
    if (values.size < 2) return 0.0
    val sumSquares = values.sumOf { (it - mean) * (it - mean) }
    return sqrt(sumSquares / (values.size - 1))
}

/**
 * How complete a window is: readings present against days covered. The screens
 * print this rather than hiding gaps, because a 30-day average built from 11
 * nights is a different claim from one built from 30.
 */
fun coverage(readings: List<Reading>, days: Int): Int =
    if (days <= 0) 0 else readings.map { it.day }.distinct().size.coerceAtMost(days)
