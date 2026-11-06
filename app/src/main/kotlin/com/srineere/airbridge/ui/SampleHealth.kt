package com.srineere.airbridge.ui

import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.HeartSample
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.trendOf

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A made-up fortnight, for the edit screen.
 *
 * Arranging tiles is a question about shape: how much a size can hold, and
 * what it looks like holding it. Real readings answer that badly. Yours are
 * whatever they happen to be today, so an empty tile might mean the size is
 * wrong or might mean you have not worn the band, and nothing on the screen
 * says which. A day with no food logged makes the nutrition tile look broken
 * when it is only unfed.
 *
 * So the previews get a person who slept, walked, ate and exercised: every
 * tile full, every chart populated, nothing missing. What you are choosing
 * between is then the only thing varying.
 *
 * Fixed, never random. The same arrangement has to look the same twice, and
 * a chart that reshuffles itself on every recomposition is a chart you
 * cannot compare against the one beside it.
 */
fun sampleHealthData(): HealthData {
    val today = LocalDate.now().toEpochDay()
    val snapshot = HealthSnapshot(
        stepsToday = 8_240L,
        // Long enough to get past readiness's own gate. A made-up person
        // whose card says "calibrating, 14 of 60" teaches nothing about
        // what the card is for, and the catalogue exists to show that.
        steps = days(today, over(STEPS, SAMPLE_DAYS)),
        restingHeartRate = days(today, over(RESTING, SAMPLE_DAYS)),
        hrv = days(today, over(HRV, SAMPLE_DAYS)),
        respiratoryRate = days(today, BREATHING),
        skinTemperature = days(today, SKIN),
        nights = nights(today),
        sessions = sessions(),
        heartToday = heartToday(),
        heartRecent = heartToday(),
        burnedDaily = days(today, BURNED),
        eatenDaily = days(today, EATEN),
        weight = days(today, WEIGHT),
        // Written every week or two, which is what makes a fitness age a
        // monthly figure rather than a morning one.
        vo2Max = listOf(
            Reading(today - 24, 43.1),
            Reading(today - 11, 43.8),
            Reading(today - 3, 44.2),
        ),
        burnedHourly = BURNED_HOURLY.toList(),
        stepsHourly = STEPS_HOURLY.toList(),
        burnedToday = 2_410.0,
        eatenToday = 1_980.0,
        proteinDaily = days(today, PROTEIN),
        carbsDaily = days(today, CARBS),
        fatDaily = days(today, FAT),
        mealsDaily = (0 until 14).associate { (today - it) to 4 },
        proteinToday = 112.0,
        carbsToday = 196.0,
        fatToday = 71.0,
        writtenAt = Instant.now().minusSeconds(1_800),
    )
    return HealthData(
        stage = HealthStage.Granted,
        snapshot = snapshot,
        restingTrend = trendOf(snapshot.restingHeartRate),
        hrvTrend = trendOf(snapshot.hrv),
        breathingTrend = trendOf(snapshot.respiratoryRate),
        skinTrend = trendOf(snapshot.skinTemperature),
        weightTrend = trendOf(snapshot.weight),
        stepsTrend = trendOf(snapshot.steps),
        sleepTrend = trendOf(snapshot.nights.map { Reading(it.day, it.asleep.toMinutes().toDouble()) }),
        // Built the same way the real ones are, from the same two helpers:
        // a preview that computes its trends differently is a preview that
        // can disagree with the tab it is previewing.
        energyTrend = trendOf(balanceReadings(snapshot)),
        nutritionTrend = trendOf(snapshot.eatenDaily),
        activityTrend = trendOf(snapshot.sessions.toDailyMinutes()),
        nights = snapshot.nights,
    )
}

/**
 * The goals the previews measure against, so no tile reads 0% of nothing.
 *
 * Deliberately a little beyond the sample day rather than level with it.
 * They used to sit just under it -- 8,000 steps against 8,240 walked, 7h30
 * against 7h32 slept -- and every goal in every preview came out closed: a
 * progress bar that is simply full, and three rings that read as a bullseye
 * with no way to tell they were arcs at all. A preview has to show the
 * mechanism, and a finished one shows nothing but the end of it.
 *
 * Both are ordinary numbers for someone to have chosen: twelve thousand
 * steps, and nine hours, the top of the 7-9 band rather than the bottom.
 */
val sampleGoals = Goals(
    steps = 12_000,
    sleepMinutes = 540,
    energyKcal = -300,
    proteinGrams = 120,
    activeMinutes = 150,
)

/** Oldest first, ending today, which is the order every chart here expects. */
/**
 * The same shape, carried further back.
 *
 * The sample's own arrays are a fortnight, which is all most cards draw.
 * Readiness counts sixty readings and the load ratio wants four weeks, so
 * the three series it stands on are repeated rather than invented twice:
 * one person, one habit, more of it.
 */
private fun over(values: DoubleArray, count: Int): DoubleArray =
    DoubleArray(count) { values[it % values.size] }

/** Two months and a little, so sixty is cleared with room to spare. */
private const val SAMPLE_DAYS = 70

private fun days(today: Long, values: DoubleArray): List<Reading> =
    values.mapIndexed { index, value -> Reading(today - (values.size - 1 - index), value) }

private fun nights(today: Long): List<Night> = ASLEEP.mapIndexed { index, minutes ->
    val day = today - (ASLEEP.size - 1 - index)
    // Bedtime wanders by a few minutes a night, which is what makes the
    // consistency half of the score say anything at all.
    val bed = LocalDate.ofEpochDay(day).minusDays(1)
        .atTime(23, 10 + (index % 5) * 7)
        .atZone(ZoneId.systemDefault()).toInstant()
    val asleep = Duration.ofMinutes(minutes.toLong())
    val awake = Duration.ofMinutes(18 + (index % 4) * 6L)
    Night(
        day = day,
        start = bed,
        end = bed.plus(asleep).plus(awake),
        asleep = asleep,
        awake = awake,
        // Roughly the proportions a night actually holds: a fifth deep, a
        // fifth REM, the rest light.
        light = Duration.ofMinutes((minutes * 0.57).toLong()),
        deep = Duration.ofMinutes((minutes * 0.20).toLong()),
        rem = Duration.ofMinutes((minutes * 0.23).toLong()),
    )
}

private fun sessions(): List<Session> {
    val now = Instant.now()
    return listOf(
        Session(now.minusSeconds(86_400 * 5 + 3_600), now.minusSeconds(86_400 * 5), "Evening walk", 79),
        Session(now.minusSeconds(86_400 * 3 + 2_700), now.minusSeconds(86_400 * 3), "Strength", 80),
        Session(now.minusSeconds(86_400 * 2 + 1_800), now.minusSeconds(86_400 * 2), "Run", 56),
        Session(now.minusSeconds(86_400 + 3_000), now.minusSeconds(86_400), "Cycle", 8),
        Session(now.minusSeconds(9_000), now.minusSeconds(6_300), "Morning walk", 79),
    )
}

/** A day of heart rate, at ten-minute steps, with a workout in the middle. */
private fun heartToday(): List<HeartSample> {
    val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return (0 until 144).map { step ->
        val hour = step / 6.0
        val bpm = when {
            hour < 6.5 -> 56 + (step % 5)
            hour in 9.0..9.8 -> 132 - (step % 11)
            hour in 17.5..18.3 -> 118 - (step % 9)
            else -> 72 + (step % 13)
        }
        HeartSample(midnight + step * 600_000L, bpm)
    }
}

private val STEPS = doubleArrayOf(
    7_420.0, 9_180.0, 5_960.0, 8_740.0, 11_200.0, 6_380.0, 7_900.0,
    8_610.0, 10_430.0, 6_120.0, 9_050.0, 7_280.0, 8_960.0, 8_240.0,
)
private val RESTING = doubleArrayOf(
    66.0, 65.0, 67.0, 64.0, 65.0, 66.0, 64.0, 63.0, 65.0, 63.0, 64.0, 62.0, 63.0, 62.0,
)
private val HRV = doubleArrayOf(
    38.0, 41.0, 36.0, 43.0, 45.0, 39.0, 42.0, 44.0, 47.0, 40.0, 46.0, 43.0, 48.0, 45.0,
)
private val BREATHING = doubleArrayOf(
    14.2, 13.8, 14.6, 14.0, 13.9, 14.4, 14.1, 13.7, 14.3, 14.0, 13.8, 14.2, 13.9, 14.1,
)
/** A delta against your own baseline, which is how a band reports skin temperature. */
private val SKIN = doubleArrayOf(
    -0.2, 0.1, -0.4, 0.3, 0.0, -0.1, 0.2, 0.4, -0.3, 0.1, 0.0, 0.3, -0.1, 0.2,
)
private val ASLEEP = doubleArrayOf(
    412.0, 455.0, 388.0, 471.0, 436.0, 402.0, 448.0,
    465.0, 394.0, 441.0, 428.0, 476.0, 419.0, 452.0,
).map { it.toInt() }
private val BURNED = doubleArrayOf(
    2_380.0, 2_520.0, 2_210.0, 2_470.0, 2_690.0, 2_240.0, 2_400.0,
    2_460.0, 2_610.0, 2_190.0, 2_540.0, 2_350.0, 2_480.0, 2_410.0,
)
private val EATEN = doubleArrayOf(
    2_040.0, 2_180.0, 1_860.0, 2_120.0, 2_310.0, 1_940.0, 2_070.0,
    2_150.0, 2_260.0, 1_880.0, 2_200.0, 1_990.0, 2_110.0, 1_980.0,
)
private val WEIGHT = doubleArrayOf(
    74.8, 74.6, 74.9, 74.5, 74.4, 74.6, 74.3, 74.2, 74.4, 74.1, 74.0, 74.2, 73.9, 74.0,
)
private val PROTEIN = doubleArrayOf(
    104.0, 118.0, 96.0, 122.0, 131.0, 99.0, 110.0,
    116.0, 127.0, 94.0, 120.0, 108.0, 124.0, 112.0,
)
private val CARBS = doubleArrayOf(
    198.0, 224.0, 176.0, 210.0, 248.0, 184.0, 202.0,
    216.0, 238.0, 172.0, 220.0, 190.0, 214.0, 196.0,
)
private val FAT = doubleArrayOf(
    72.0, 81.0, 64.0, 76.0, 88.0, 68.0, 74.0,
    79.0, 85.0, 62.0, 80.0, 70.0, 77.0, 71.0,
)
/** Midnight to midnight, with the shape a day actually has. */
private val BURNED_HOURLY = doubleArrayOf(
    62.0, 58.0, 56.0, 55.0, 57.0, 64.0, 88.0, 112.0, 134.0, 186.0, 128.0, 142.0,
    156.0, 118.0, 104.0, 112.0, 126.0, 168.0, 144.0, 118.0, 96.0, 84.0, 72.0, 64.0,
)
private val STEPS_HOURLY = doubleArrayOf(
    0.0, 0.0, 0.0, 0.0, 0.0, 40.0, 180.0, 420.0, 760.0, 1_240.0, 380.0, 620.0,
    980.0, 540.0, 320.0, 410.0, 680.0, 1_120.0, 460.0, 290.0, 120.0, 60.0, 20.0, 0.0,
)
