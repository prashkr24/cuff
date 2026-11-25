package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CoachableHealthAgeTest {

    @Test
    fun `unadjusted four-core optimal matches paper rejuvenation floor of -5_135 years`() {
        // Section 4.1.5: RHR <= 60 (0.00), steps >= 9000 (-1.28), sleep 6-9h (0.00), AZM >= 500 (-3.86)
        val est = CoachableHealthAge.evaluate(
            ChaInputs(
                chronologicalAge = 40.0,
                restingHeartRateBpm = 58.0,
                dailySteps = 12_000.0,
                sleepHours = 7.5,
                weeklyAzm = 600.0,
            ),
            ChaConfig(collinearityAdjusted = false),
        )
        assertEquals(4, est.corePresent)
        assertEquals(-5.135, est.deltaYears!!, 0.002)
        assertEquals(34.865, est.healthAge!!, 0.002)

        val stepsDelta = est.domains.first { it.domain == ChaDomain.DailySteps }.yearsDelta
        val azmDelta = est.domains.first { it.domain == ChaDomain.WeeklyAzm }.yearsDelta
        assertEquals(-1.278, stepsDelta, 0.005)
        assertEquals(-3.857, azmDelta, 0.005)
    }

    @Test
    fun `collinearity-adjusted four-core optimal matches paper floor of -3_29 years`() {
        // Section 4.1.5: steps -0.61 yr, AZM -2.68 yr, total ~ -3.29 yr
        val est = CoachableHealthAge.evaluate(
            ChaInputs(
                chronologicalAge = 40.0,
                restingHeartRateBpm = 60.0,
                dailySteps = 9_500.0,
                sleepHours = 7.0,
                weeklyAzm = 550.0,
            ),
            ChaConfig(collinearityAdjusted = true),
        )
        assertEquals(-3.2905, est.deltaYears!!, 0.005)
        assertEquals(-0.610, est.domains.first { it.domain == ChaDomain.DailySteps }.yearsDelta, 0.005)
        assertEquals(-2.680, est.domains.first { it.domain == ChaDomain.WeeklyAzm }.yearsDelta, 0.005)
    }

    @Test
    fun `all piecewise domain knots are continuous and match Supplementary Table 1`() {
        // RHR knots at 60, 70, 90
        assertEquals(0.0, CoachableHealthAge.rhrLogHazard(60.0), 1e-9)
        assertEquals(kotlin.math.ln(1.08), CoachableHealthAge.rhrLogHazard(70.0), 1e-9)
        assertEquals(kotlin.math.ln(1.33), CoachableHealthAge.rhrLogHazard(90.0), 1e-9)

        // Steps reference at 8000, cap at 9000
        assertEquals(0.0, CoachableHealthAge.stepsLogHazard(8_000.0), 1e-9)
        assertEquals(kotlin.math.ln(0.88), CoachableHealthAge.stepsLogHazard(9_000.0), 1e-9)
        assertEquals(
            CoachableHealthAge.stepsLogHazard(9_000.0),
            CoachableHealthAge.stepsLogHazard(20_000.0),
            1e-9,
        )

        // Sleep U-shape: 6..9h optimal, short (<6) and long (>9) penalties
        assertEquals(0.0, CoachableHealthAge.sleepLogHazard(6.0), 1e-9)
        assertEquals(0.0, CoachableHealthAge.sleepLogHazard(9.0), 1e-9)
        assertEquals(kotlin.math.ln(1.20), CoachableHealthAge.sleepLogHazard(5.0), 1e-9)
        assertEquals(kotlin.math.ln(1.34), CoachableHealthAge.sleepLogHazard(10.0), 1e-9)

        // AZM knots at 115, 245, 418, 500
        assertEquals(0.0, CoachableHealthAge.azmLogHazard(114.9), 1e-9)
        assertEquals(0.0, CoachableHealthAge.azmLogHazard(115.0), 1e-9)
        assertEquals(kotlin.math.ln(0.76), CoachableHealthAge.azmLogHazard(245.0), 1e-9)
        assertEquals(kotlin.math.ln(0.71), CoachableHealthAge.azmLogHazard(418.0), 1e-9)
        assertEquals(kotlin.math.ln(0.68), CoachableHealthAge.azmLogHazard(500.0), 1e-9)
        assertEquals(kotlin.math.ln(0.68), CoachableHealthAge.azmLogHazard(900.0), 1e-9)
    }

    @Test
    fun `optional domains match paper values for VO2 max, sleep regularity, and insulin resistance`() {
        // Sleep irregularity at >= 120 min adds +7.47 yr
        val regDelta = CoachableHealthAge.sleepRegularityLogHazard(130.0) / CoachableHealthAge.G
        assertEquals(7.47, regDelta, 0.01)
        assertEquals(0.0, CoachableHealthAge.sleepRegularityLogHazard(30.0), 1e-9)

        // Insulin resistance adds +6.93 yr
        val irDelta = CoachableHealthAge.insulinResistanceLogHazard(true) / CoachableHealthAge.G
        assertEquals(6.93, irDelta, 0.01)
        assertEquals(0.0, CoachableHealthAge.insulinResistanceLogHazard(false), 1e-9)

        // VO2 max +1 MET (+3.5 ml/kg/min) above FRIEND norm gives -1.28 yr
        val expected = CoachableHealthAge.expectedVo2Max(ageYears = 35.0, sex = Sex.Male, weightKg = 75.0)
        val vo2Delta = CoachableHealthAge.vo2MaxLogHazard(
            vo2Max = expected + 3.5,
            ageYears = 35.0,
            sex = Sex.Male,
            weightKg = 75.0,
        ) / CoachableHealthAge.G
        assertEquals(-1.28, vo2Delta, 0.01)
    }

    @Test
    fun `final Health Age respects global 18-year lower bound`() {
        val est = CoachableHealthAge.evaluate(
            ChaInputs(
                chronologicalAge = 20.0,
                restingHeartRateBpm = 52.0,
                dailySteps = 10_000.0,
                sleepHours = 7.5,
                weeklyAzm = 600.0,
            ),
            ChaConfig(collinearityAdjusted = false),
        )
        assertEquals(18.0, est.healthAge!!, 1e-9)
        assertEquals(-2.0, est.deltaYears!!, 1e-9)
    }

    @Test
    fun `missing inputs are excluded cleanly rather than zero-filled`() {
        val est = CoachableHealthAge.evaluate(
            ChaInputs(
                chronologicalAge = 35.0,
                restingHeartRateBpm = 60.0,
                dailySteps = null,
                sleepHours = 7.5,
                weeklyAzm = null,
            ),
        )
        assertEquals(2, est.corePresent)
        assertEquals(35.0, est.healthAge!!, 1e-9)
        assertFalse(est.domains.first { it.domain == ChaDomain.DailySteps }.included)
        assertFalse(est.domains.first { it.domain == ChaDomain.WeeklyAzm }.included)
    }

    @Test
    fun `missing chronological age yields null healthAge`() {
        val est = CoachableHealthAge.evaluate(
            ChaInputs(chronologicalAge = null, restingHeartRateBpm = 60.0, dailySteps = 9_000.0),
        )
        assertNull(est.healthAge)
        assertNull(est.deltaYears)
    }

    @Test
    fun `mainSleepHours picks longest part when day includes a nap`() {
        val day = LocalDate.of(2026, 11, 10).toEpochDay()
        val main = Night(
            day = day,
            start = Instant.parse("2026-11-09T23:00:00Z"),
            end = Instant.parse("2026-11-10T06:00:00Z"),
            asleep = Duration.ofHours(7),
            awake = Duration.ZERO,
            light = Duration.ofHours(4),
            deep = Duration.ofHours(1),
            rem = Duration.ofHours(2),
        )
        val nap = Night(
            day = day,
            start = Instant.parse("2026-11-10T14:00:00Z"),
            end = Instant.parse("2026-11-10T14:45:00Z"),
            asleep = Duration.ofMinutes(45),
            awake = Duration.ZERO,
            light = Duration.ofMinutes(45),
            deep = Duration.ZERO,
            rem = Duration.ZERO,
        )
        val combined = oneNightPerDay(listOf(main, nap)).single()
        assertEquals(7.75, combined.asleep.seconds / 3600.0, 1e-6)
        assertEquals(7.0, CoachableHealthAge.mainSleepHours(combined), 1e-6)
    }

    @Test
    fun `sleepMidpointSdMinutes handles midnight wrap cleanly`() {
        val base = LocalDate.of(2026, 11, 1)
        // Midpoints alternating between 23:45 and 00:15 (30 min apart, not 23.5 h!)
        val nights = (0 until 6).map { i ->
            val d = base.plusDays(i.toLong())
            val midHour = if (i % 2 == 0) 23 else 0
            val midMin = if (i % 2 == 0) 45 else 15
            val mid = d.atTime(midHour, midMin).toInstant(ZoneOffset.UTC)
            Night(
                day = d.toEpochDay(),
                start = mid.minus(Duration.ofHours(3)),
                end = mid.plus(Duration.ofHours(3)),
                asleep = Duration.ofHours(6),
                awake = Duration.ZERO,
                light = Duration.ofHours(6),
                deep = Duration.ZERO,
                rem = Duration.ZERO,
                midpoint = mid,
            )
        }
        val sd = CoachableHealthAge.sleepMidpointSdMinutes(nights, ZoneOffset.UTC)
        assertNotNull(sd)
        assertTrue("Expected circular SD ~16 min, got $sd", sd!! in 14.0..20.0)
    }

    @Test
    fun `summarize aggregates weekly vectors and calibrates after 5 valid weeks`() {
        val today = LocalDate.of(2026, 11, 25)
        val days = (0 until 49).map { today.minusDays(it.toLong()).toEpochDay() }.sorted()
        val steps = days.map { Reading(it, 8_500.0) }
        val rhr = days.map { Reading(it, 62.0) }
        val nights = days.map { d ->
            val ld = LocalDate.ofEpochDay(d)
            val end = ld.atTime(7, 0).toInstant(ZoneOffset.UTC)
            val start = end.minus(Duration.ofHours(7))
            Night(
                day = d,
                start = start,
                end = end,
                asleep = Duration.ofHours(7),
                awake = Duration.ZERO,
                light = Duration.ofHours(5),
                deep = Duration.ofHours(1),
                rem = Duration.ofHours(1),
            )
        }
        val zones = days.associateWith { d ->
            DailyZoneSummary(day = d, moderateMin = 20.0, cardioMin = 10.0, peakMin = 2.0, sampleCount = 500)
        }

        val summary = CoachableHealthAge.summarize(
            snapshot = HealthSnapshot(steps = steps, restingHeartRate = rhr, nights = nights),
            dailyZones = zones,
            profile = FitnessProfile(birthYear = 1991, sex = Sex.Male),
            today = today,
            zone = ZoneOffset.UTC,
        )
        assertTrue(summary.calibrated)
        assertTrue(summary.validWeeksIn90Days >= 5)
        assertNotNull(summary.estimate.healthAge)
        // Daily AZM = 20 + 2*(10+2) = 44 -> 308/wk
        assertEquals(308.0, summary.inputs.weeklyAzm!!, 0.1)
    }
}
