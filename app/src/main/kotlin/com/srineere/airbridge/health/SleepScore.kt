package com.srineere.airbridge.health

import java.time.Duration
import java.time.ZoneId
import kotlin.math.abs

/**
 * A sleep score, and the four things it is made of.
 *
 * Every published sleep score is a weighted sum of the same handful of
 * measurements, and every vendor weights them differently — which is the
 * whole reason a bare number is worthless. So this one never appears without
 * its parts, and each part is measured against **your own** nights rather
 * than a textbook figure. The app has no opinion on how long you should
 * sleep; it has an opinion on whether last night was like your others.
 *
 * A component with nothing behind it is null, not zero. A night written
 * without stages scores on three parts and says so, because scoring it as
 * no deep sleep would punish a night for how it was recorded.
 */
data class SleepScore(
    val duration: Int?,
    val stages: Int?,
    val consistency: Int?,
    val interruptions: Int?,
) {
    val parts: List<Pair<String, Int>>
        get() = listOfNotNull(
            duration?.let { "Duration" to it },
            stages?.let { "Stages" to it },
            consistency?.let { "Consistency" to it },
            interruptions?.let { "Interruptions" to it },
        )

    /** The mean of whatever could be measured, or null when nothing could. */
    val total: Int?
        get() = parts.takeIf { it.isNotEmpty() }?.let { found ->
            found.sumOf { it.second } / found.size
        }

    /** What the score is standing on, for the line under it. */
    val basis: String
        get() = when (parts.size) {
            0 -> "nothing measurable"
            4 -> "all four parts"
            1 -> "one part of four"
            else -> "${parts.size} parts of four"
        }
}

/**
 * Scores one night against the nights around it.
 *
 * [history] should be the window the screen is showing, last night included;
 * with fewer than [MINIMUM_NIGHTS] nights behind it there is no personal
 * normal to measure against and the parts that need one stay null.
 */
fun sleepScoreOf(
    night: Night,
    history: List<Night>,
    zone: ZoneId = ZoneId.systemDefault(),
): SleepScore {
    val others = history.filter { it.day != night.day }
    val enough = others.size >= MINIMUM_NIGHTS

    val duration = if (!enough) null else {
        val usual = bandOf(others.map { it.asleep.toMinutes().toDouble() })
        scoreAgainst(night.asleep.toMinutes().toDouble(), usual)
    }

    val stages = if (!enough || !night.hasStages) null else {
        val staged = others.filter { it.hasStages }
        if (staged.size < MINIMUM_NIGHTS) null else {
            // Deep and REM together: the two stages every vendor's score
            // leans on, and the two a short night loses first.
            val usual = bandOf(staged.map { restorativeShare(it) })
            scoreAgainst(restorativeShare(night), usual)
        }
    }

    val consistency = if (!enough) null else {
        val middles = others.map { minutesIntoDay(it, zone) }
        val usual = circularMean(middles)
        val off = circularDistance(minutesIntoDay(night, zone), usual)
        // Within half an hour of your own middle is as consistent as anyone
        // is; three hours out is a different night altogether.
        scale(off, best = 30.0, worst = 180.0, inverted = true)
    }

    val interruptions = run {
        val inBed = Duration.between(night.start, night.end).seconds.toDouble()
        if (inBed <= 0.0) null else {
            val share = night.awake.seconds / inBed
            val fromAwake = scale(share * 100, best = 5.0, worst = 25.0, inverted = true)
            // Each wake past the second costs five: waking twice is normal
            // sleep architecture, waking eight times is not the same night.
            (fromAwake - (night.wakes - 2).coerceAtLeast(0) * 5).coerceIn(0, 100)
        }
    }

    return SleepScore(duration, stages, consistency, interruptions)
}

private const val MINIMUM_NIGHTS = 5

private fun restorativeShare(night: Night): Double {
    val asleep = night.asleep.seconds.toDouble()
    return if (asleep <= 0.0) 0.0 else (night.deep + night.rem).seconds / asleep * 100
}

/**
 * Where a value sits against a band: at or above the top is full marks, the
 * bottom of the band is a pass, below that it falls away to nothing.
 *
 * Two segments rather than one, because the distance from "my usual" to
 * "half my usual" matters far more than the distance from "my usual" to
 * "better than my usual" — and a single line through both would say a long
 * night was as unusual as a lost one.
 */
private fun scoreAgainst(value: Double, usual: Band): Int = when {
    value >= usual.high -> 100
    value >= usual.low -> (70 + (value - usual.low) / (usual.high - usual.low).safe() * 30).toInt()
    usual.low <= 0.0 -> 100
    else -> (value / usual.low * 70).toInt().coerceIn(0, 70)
}

private fun scale(value: Double, best: Double, worst: Double, inverted: Boolean): Int {
    val at = ((value - best) / (worst - best)).coerceIn(0.0, 1.0)
    return if (inverted) ((1 - at) * 100).toInt() else (at * 100).toInt()
}

private fun Double.safe(): Double = if (this <= 0.0) 1.0 else this

/**
 * The middle half of a set, never narrower than a twentieth of it.
 *
 * Somebody who sleeps the same seven hours every night has an interquartile
 * range of nothing, and against a band of zero width a five-minute
 * difference reads as a missed night. The floor is what keeps a very
 * consistent sleeper from being marked down for being consistent.
 */
private fun bandOf(values: List<Double>): Band {
    val sorted = values.sorted()
    val high = quantile(sorted, 0.75)
    val low = quantile(sorted, 0.25)
    return Band(minOf(low, high * (1 - BAND_FLOOR)), high)
}

private const val BAND_FLOOR = 0.05

/** Minutes past midnight of the night's middle, 0..1439. */
private fun minutesIntoDay(night: Night, zone: ZoneId): Double {
    val at = night.midpoint.atZone(zone).toLocalTime()
    return at.hour * 60.0 + at.minute
}

/**
 * The mean of times of day, done on the clock face.
 *
 * A midpoint of 01:00 and one of 23:00 are two hours apart, not twenty-two,
 * and a plain average of those two puts the usual middle at noon.
 */
private fun circularMean(minutes: List<Double>): Double {
    if (minutes.isEmpty()) return 0.0
    val radians = minutes.map { it / 1440.0 * 2 * Math.PI }
    val x = radians.sumOf { Math.cos(it) } / radians.size
    val y = radians.sumOf { Math.sin(it) } / radians.size
    val angle = Math.atan2(y, x)
    val normalised = if (angle < 0) angle + 2 * Math.PI else angle
    return normalised / (2 * Math.PI) * 1440.0
}

private fun circularDistance(a: Double, b: Double): Double {
    val raw = abs(a - b)
    return minOf(raw, 1440.0 - raw)
}
