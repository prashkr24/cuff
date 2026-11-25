package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class CounterfactualCoachTest {

    @Test
    fun `personalized effort respects safety floors in Eq 55`() {
        val baseline = BehaviorTarget(dailySteps = 6_000.0, sleepHours = 6.0, cardioMinPerDay = 10.0)
        val candidate = BehaviorTarget(dailySteps = 6_500.0, sleepHours = 6.25, cardioMinPerDay = 20.0)
        // Zero historical variance -> floors of 500 steps, 15 sleep min, 10 cardio min kick in
        val habitual = HabitualStats(
            weeksObserved = 8,
            steps = ActionDistribution(6_000.0, 0.0, 6_000.0, 6_000.0, 6_000.0),
            sleepHours = ActionDistribution(6.0, 0.0, 6.0, 6.0, 6.0),
            cardioMinPerDay = ActionDistribution(10.0, 0.0, 10.0, 10.0, 10.0),
            weeklyAzm = ActionDistribution(140.0, 0.0, 140.0, 140.0, 140.0),
        )
        // Each lever moved by exactly 1 safety-floor SD -> 1^2 + 1^2 + 1^2 = 3.0
        val effort = CounterfactualCoach.personalizedEffort(baseline, candidate, habitual)
        assertEquals(3.0, effort, 1e-9)
        assertEquals(0.25, CounterfactualCoach.optimizationScore(40.0, 39.0, effort), 1e-9)
    }

    @Test
    fun `recovery bottleneck isolates sleep and avoids pushing steps past saturation`() {
        val (snapshot, zones) = syntheticHistory(
            weeks = 10,
            dailySteps = 11_500.0,
            sleepHours = 5.2,
            rhrBpm = 58.0,
            moderateMin = 30.0,
            cardioMin = 25.0, // 560 AZM/wk (already saturated!)
        )
        val profile = FitnessProfile(birthYear = 1990, sex = Sex.Male)
        val summary = CoachableHealthAge.summarize(
            snapshot = snapshot,
            dailyZones = zones,
            profile = profile,
            today = LocalDate.of(2026, 11, 25),
            zone = ZoneOffset.UTC,
        )
        val plan = CounterfactualCoach.plan(summary, zones, snapshot)
        assertNotNull(plan)
        assertEquals(CoachablePersona.RecoveryBottleneck, plan!!.persona)
        assertTrue(plan.options.any { it.kind == PathwayKind.Sleep })
        // Movement is already saturated (>=9k steps, >=500 AZM), so no redundant step increase!
        plan.options.forEach { opt ->
            assertEquals(plan.baseline.dailySteps, opt.target.dailySteps, 1.0)
        }
    }

    @Test
    fun `activity bottleneck surfaces movement pathway and lowers projected Health Age`() {
        val (snapshot, zones) = syntheticHistory(
            weeks = 10,
            dailySteps = 3_500.0,
            sleepHours = 7.5,
            rhrBpm = 74.0,
            moderateMin = 5.0,
            cardioMin = 2.0,
        )
        val profile = FitnessProfile(birthYear = 1986, sex = Sex.Female)
        val summary = CoachableHealthAge.summarize(
            snapshot = snapshot,
            dailyZones = zones,
            profile = profile,
            today = LocalDate.of(2026, 11, 25),
            zone = ZoneOffset.UTC,
        )
        val plan = CounterfactualCoach.plan(summary, zones, snapshot)
        assertNotNull(plan)
        assertEquals(CoachablePersona.ActivityBottleneck, plan!!.persona)
        assertTrue(plan.options.isNotEmpty())
        assertTrue(plan.options.any { it.kind == PathwayKind.Movement })
        assertTrue(plan.options.first().chaDeltaVsStatusQuo < -CounterfactualCoach.MIN_CHA_REDUCTION)
    }

    @Test
    fun `optimal baseline produces no spurious interventions`() {
        val (snapshot, zones) = syntheticHistory(
            weeks = 10,
            dailySteps = 11_000.0,
            sleepHours = 7.5,
            rhrBpm = 56.0,
            moderateMin = 30.0,
            cardioMin = 25.0,
        )
        val profile = FitnessProfile(birthYear = 1990, sex = Sex.Male)
        val summary = CoachableHealthAge.summarize(
            snapshot = snapshot,
            dailyZones = zones,
            profile = profile,
            today = LocalDate.of(2026, 11, 25),
            zone = ZoneOffset.UTC,
        )
        val plan = CounterfactualCoach.plan(summary, zones, snapshot)
        assertNotNull(plan)
        assertEquals(CoachablePersona.OptimalBaseline, plan!!.persona)
        assertTrue(plan.options.isEmpty())
    }

    @Test
    fun `personal N=1 surrogate requires 6 valid weeks and keeps 12-week RHR bounded`() {
        val (shortSnap, shortZones) = syntheticHistory(
            weeks = 4,
            dailySteps = 6_000.0,
            sleepHours = 6.5,
            rhrBpm = 68.0,
            moderateMin = 10.0,
            cardioMin = 5.0,
        )
        val shortSummary = CoachableHealthAge.summarize(
            snapshot = shortSnap,
            dailyZones = shortZones,
            profile = FitnessProfile(birthYear = 1990, sex = Sex.Male),
            today = LocalDate.of(2026, 11, 25),
            zone = ZoneOffset.UTC,
        )
        assertNull(CounterfactualCoach.fitPersonalSurrogate(shortSummary.weeklyVectors))

        val (longSnap, longZones) = syntheticHistory(
            weeks = 12,
            dailySteps = 6_000.0,
            sleepHours = 6.5,
            rhrBpm = 68.0,
            moderateMin = 10.0,
            cardioMin = 5.0,
            varyWeekly = true,
        )
        val longSummary = CoachableHealthAge.summarize(
            snapshot = longSnap,
            dailyZones = longZones,
            profile = FitnessProfile(birthYear = 1990, sex = Sex.Male),
            today = LocalDate.of(2026, 11, 25),
            zone = ZoneOffset.UTC,
        )
        val plan = CounterfactualCoach.plan(
            summary = longSummary,
            dailyZones = longZones,
            snapshot = longSnap,
            mode = SimulationMode.PersonalSurrogate,
        )
        assertNotNull(plan)
        assertEquals(SimulationMode.PersonalSurrogate, plan!!.effectiveMode)
        val baseRhr = plan.baselineRhr!!
        val sqRhr = plan.statusQuo.endpointRhr!!
        assertTrue("RHR drift should stay biologically bounded", sqRhr in (baseRhr - 6.0)..(baseRhr + 4.0))
    }

    @Test
    fun `prompt builder includes Appendix C_3 required sections and guardrails`() {
        val (snapshot, zones) = syntheticHistory(
            weeks = 8,
            dailySteps = 5_000.0,
            sleepHours = 5.5,
            rhrBpm = 72.0,
            moderateMin = 10.0,
            cardioMin = 4.0,
        )
        val profile = FitnessProfile(birthYear = 1988, sex = Sex.Male)
        val summary = CoachableHealthAge.summarize(
            snapshot = snapshot,
            dailyZones = zones,
            profile = profile,
            today = LocalDate.of(2026, 11, 25),
            zone = ZoneOffset.UTC,
        )
        val plan = CounterfactualCoach.plan(summary, zones, snapshot)!!
        val prompt = CounterfactualCoach.buildCounterfactualCoachPrompt(
            summary = summary,
            plan = plan,
            profile = profile,
            heightCm = 178,
        )
        assertTrue(prompt.contains("YOUR HEALTH SNAPSHOT & HEALTH AGE"))
        assertTrue(prompt.contains("YOUR PERSONALIZED PATHWAYS & 12-WEEK PROGRESSIVE PLAN"))
        assertTrue(prompt.contains("EVIDENCE-BASED HABIT FORMATION"))
        assertTrue(prompt.contains("SUMMARY & NEXT STEPS"))
        assertTrue(prompt.contains("Future A"))
        assertTrue(prompt.contains("Future B"))
        assertTrue(prompt.contains("Option 1"))
    }

    private fun syntheticHistory(
        weeks: Int,
        dailySteps: Double,
        sleepHours: Double,
        rhrBpm: Double,
        moderateMin: Double,
        cardioMin: Double,
        varyWeekly: Boolean = false,
    ): Pair<HealthSnapshot, Map<Long, DailyZoneSummary>> {
        val today = LocalDate.of(2026, 11, 25)
        val totalDays = weeks * 7
        val steps = mutableListOf<Reading>()
        val rhr = mutableListOf<Reading>()
        val nights = mutableListOf<Night>()
        val zones = linkedMapOf<Long, DailyZoneSummary>()

        for (i in 0 until totalDays) {
            val date = today.minusDays(i.toLong())
            val epoch = date.toEpochDay()
            val wIdx = i / 7
            val wobble = if (varyWeekly) ((wIdx % 3) - 1).toDouble() else 0.0
            steps += Reading(epoch, dailySteps + wobble * 900.0)
            rhr += Reading(epoch, rhrBpm - wobble * 1.5)
            val sleepSec = ((sleepHours + wobble * 0.3) * 3600.0).toLong()
            val end = date.atTime(7, 0).toInstant(ZoneOffset.UTC)
            nights += Night(
                day = epoch,
                start = end.minusSeconds(sleepSec),
                end = end,
                asleep = Duration.ofSeconds(sleepSec),
                awake = Duration.ZERO,
                light = Duration.ofSeconds(sleepSec),
                deep = Duration.ZERO,
                rem = Duration.ZERO,
            )
            zones[epoch] = DailyZoneSummary(
                day = epoch,
                moderateMin = moderateMin,
                cardioMin = (cardioMin + wobble * 2.0).coerceAtLeast(0.0),
                peakMin = 0.0,
                sampleCount = 600,
            )
        }
        return HealthSnapshot(
            steps = steps.sortedBy { it.day },
            restingHeartRate = rhr.sortedBy { it.day },
            nights = nights.sortedBy { it.day },
        ) to zones
    }
}
