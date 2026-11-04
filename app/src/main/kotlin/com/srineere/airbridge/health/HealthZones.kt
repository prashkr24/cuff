package com.srineere.airbridge.health

import java.time.Duration

/** One heart rate reading, with the moment it was taken. */
data class HeartSample(val atMillis: Long, val bpm: Int)

/** A band of effort, as a share of maximum heart rate. */
enum class Zone(val label: String, val lowFraction: Double, val highFraction: Double) {
    Light("Light", 0.50, 0.60),
    Moderate("Moderate", 0.60, 0.70),
    Vigorous("Vigorous", 0.70, 0.85),
    Peak("Peak", 0.85, 1.20),
    ;

    fun lowBpm(maxHeartRate: Int): Int = Math.round(maxHeartRate * lowFraction).toInt()
    fun highBpm(maxHeartRate: Int): Int = Math.round(maxHeartRate * highFraction).toInt()
}

/**
 * The maximum this app assumes when nobody has told it otherwise.
 *
 * 220 minus age is a population formula with an error of about ten beats
 * either way, so every zone edge drawn from it is approximate. It is the one
 * number on the Heart screen worth replacing with a measured figure, and the
 * screen says so rather than presenting the boundaries as fact.
 */
const val ASSUMED_MAX_HEART_RATE = 185

/**
 * Which zone a reading falls in, or null when it is below the lightest --
 * sitting still is not a zone, and counting it as "Light" would turn a desk
 * day into an hour of exercise.
 */
fun zoneOf(bpm: Int, maxHeartRate: Int = ASSUMED_MAX_HEART_RATE): Zone? =
    Zone.entries.lastOrNull { bpm >= it.lowBpm(maxHeartRate) }

/**
 * How long was spent in each zone.
 *
 * Time is measured between consecutive readings and credited to the earlier
 * one, which is the only honest reading of a sampled signal. A gap longer
 * than [maxGap] is not credited at all: the band was off the wrist or asleep,
 * and stretching one reading across an hour would invent the hour.
 */
fun timeInZones(
    samples: List<HeartSample>,
    maxHeartRate: Int = ASSUMED_MAX_HEART_RATE,
    maxGap: Duration = Duration.ofMinutes(5),
): Map<Zone, Duration> {
    if (samples.size < 2) return emptyMap()
    val ordered = samples.sortedBy { it.atMillis }
    val totals = linkedMapOf<Zone, Duration>()
    for (index in 0 until ordered.lastIndex) {
        val gap = Duration.ofMillis(ordered[index + 1].atMillis - ordered[index].atMillis)
        if (gap.isNegative || gap.isZero || gap > maxGap) continue
        val zone = zoneOf(ordered[index].bpm, maxHeartRate) ?: continue
        totals[zone] = (totals[zone] ?: Duration.ZERO) + gap
    }
    return totals
}

/** Lowest, average and highest across a day's readings. */
data class HeartSummary(val lowest: Int, val average: Int, val highest: Int, val count: Int)

fun summarise(samples: List<HeartSample>): HeartSummary? {
    if (samples.isEmpty()) return null
    val values = samples.map { it.bpm }
    return HeartSummary(
        lowest = values.min(),
        average = Math.round(values.average()).toInt(),
        highest = values.max(),
        count = values.size,
    )
}
