package com.srineere.airbridge.health

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coachable Health Age (CHA), implemented directly from Section 4.1 and
 * Supplementary Table 1 of Lee et al. (2026),
 * "Personalized coaching via coachable health age and counterfactual wearable
 * trajectories" (arXiv:2611.08568v1).
 *
 * Every domain maps a repeatedly measurable wearable figure to a log hazard
 * ratio and converts the weighted sum into an age delta via the Gompertz
 * rate constant [G] = 0.1 (a ~10% mortality/risk rise per year of age):
 *
 *     deltaAge = (1 / G) * sum_i (w_i * ln(HR_i))
 *     healthAge = max(MIN_HEALTH_AGE, chronologicalAge + deltaAge)
 *
 * Because the sum is additive in log-hazard space, every domain's exact
 * contribution in years is exposed alongside the total, and missing inputs
 * drop out cleanly (w_i = 0) instead of being guessed or zero-filled.
 */
object CoachableHealthAge {

    /** Age-rate constant G (Eq. 1): ~10% exponential risk increase per year. */
    const val G = 0.1

    /** Global lower bound on final Health Age in years (Section 4.1.5). */
    const val MIN_HEALTH_AGE = 18.0

    /** WEAR-ME multicollinearity-adjusted core weights (Eq. 8). */
    const val W_SLEEP = 0.8790
    const val W_RHR = 0.7066
    const val W_AZM = 0.6950
    const val W_STEPS = 0.4773

    /** Section 4.1.8.1 data-quality gates for a 90-day CHA window. */
    const val WINDOW_DAYS = 90
    const val HABITUAL_WINDOW_DAYS = 168 // 24 weeks (Section 4.3.1)
    const val MIN_VALID_DAYS_PER_WEEK = 2
    const val MIN_VALID_WEEKS = 5

    private const val KG_TO_LB = 2.20462262185

    /** Eq. (3): Resting heart rate log-hazard (reference <= 60 bpm, HR = 1.0). */
    fun rhrLogHazard(rhrBpm: Double): Double = when {
        rhrBpm <= 60.0 -> 0.0
        rhrBpm <= 70.0 -> ((rhrBpm - 60.0) / 10.0) * ln(1.08)
        else -> ln(1.08) + ((rhrBpm - 70.0) / 20.0) * (ln(1.33) - ln(1.08))
    }

    /** Eq. (4): Daily steps log-hazard (reference 8,000 steps/day, cap 9,000). */
    fun stepsLogHazard(dailySteps: Double): Double {
        val effective = min(max(dailySteps, 0.0), 9_000.0)
        return ((effective - 8_000.0) / 1_000.0) * ln(0.88)
    }

    /** Eq. (5): U-shaped sleep duration log-hazard (optimal 6.0–9.0 h/night). */
    fun sleepLogHazard(hoursPerNight: Double): Double {
        val h = max(hoursPerNight, 0.0)
        return when {
            h < 6.0 -> (6.0 - h) * ln(1.20)
            h <= 9.0 -> 0.0
            else -> (h - 9.0) * ln(1.34)
        }
    }

    /** Eq. (6): Weekly Active Zone Minutes log-hazard (ref < 115, plateau >= 500). */
    fun azmLogHazard(weeklyAzm: Double): Double {
        val a = max(weeklyAzm, 0.0)
        return when {
            a < 115.0 -> 0.0
            a < 245.0 -> ((a - 115.0) / 130.0) * ln(0.76)
            a < 418.0 -> ln(0.76) + ((a - 245.0) / 173.0) * (ln(0.71) - ln(0.76))
            a < 500.0 -> ln(0.71) + ((a - 418.0) / 82.0) * (ln(0.68) - ln(0.71))
            else -> ln(0.68)
        }
    }

    /** Eq. (10): FRIEND registry expected VO2 max (ml/kg/min). */
    fun expectedVo2Max(ageYears: Double, sex: Sex, weightKg: Double): Double {
        val female = if (sex == Sex.Female) 1.0 else 0.0
        val weightLb = weightKg * KG_TO_LB
        return 79.9 - 0.39 * ageYears - 13.7 * female - 0.127 * weightLb
    }

    /** Eq. (11): Optional cardiorespiratory fitness log-hazard (-1.28 yr per MET above norm). */
    fun vo2MaxLogHazard(
        vo2Max: Double,
        ageYears: Double,
        sex: Sex,
        weightKg: Double,
    ): Double {
        val expected = expectedVo2Max(ageYears, sex, weightKg)
        return ((vo2Max - expected) / 3.5) * ln(0.88)
    }

    /** Eq. (12): Optional sleep midpoint regularity log-hazard (ref <= 30 min, cap 120 min). */
    fun sleepRegularityLogHazard(sdMidpointMinutes: Double): Double {
        val sd = min(max(sdMidpointMinutes, 0.0), 120.0)
        return when {
            sd <= 30.0 -> 0.0
            sd <= 60.0 -> ((sd - 30.0) / 30.0) * ln(1.16)
            sd <= 90.0 -> ln(1.16) + ((sd - 60.0) / 30.0) * (ln(1.52) - ln(1.16))
            else -> ln(1.52) + ((sd - 90.0) / 30.0) * (ln(2.11) - ln(1.52))
        }
    }

    /** Eq. (13): Optional wearable/lab insulin resistance status log-hazard. */
    fun insulinResistanceLogHazard(insulinResistant: Boolean): Double =
        if (insulinResistant) ln(2.00) else 0.0

    /**
     * Evaluates CHA from aggregate domain inputs (Eqs. 1–13).
     *
     * Any null input is excluded cleanly (weight 0) and surfaced in [ChaEstimate.domains]
     * so the screen can tell the user exactly which signals stand behind the number.
     */
    fun evaluate(
        inputs: ChaInputs,
        config: ChaConfig = ChaConfig(),
    ): ChaEstimate {
        val wRhr = if (config.collinearityAdjusted) W_RHR else 1.0
        val wSteps = if (config.collinearityAdjusted) W_STEPS else 1.0
        val wSleep = if (config.collinearityAdjusted) W_SLEEP else 1.0
        val wAzm = if (config.collinearityAdjusted) W_AZM else 1.0

        val domains = buildList {
            // Core 1: Resting Heart Rate
            val rhrLog = inputs.restingHeartRateBpm?.let(::rhrLogHazard)
            add(
                ChaDomainResult(
                    domain = ChaDomain.RestingHeartRate,
                    measuredValue = inputs.restingHeartRateBpm,
                    unit = "bpm",
                    reference = "optimal ≤ 60 bpm",
                    weight = if (rhrLog != null) wRhr else 0.0,
                    logHazard = rhrLog ?: 0.0,
                    yearsDelta = rhrLog?.let { (wRhr * it) / G } ?: 0.0,
                    included = rhrLog != null,
                    saturated = inputs.restingHeartRateBpm != null && inputs.restingHeartRateBpm <= 60.0,
                ),
            )

            // Core 2: Daily Steps
            val stepsLog = inputs.dailySteps?.let(::stepsLogHazard)
            add(
                ChaDomainResult(
                    domain = ChaDomain.DailySteps,
                    measuredValue = inputs.dailySteps,
                    unit = "steps/day",
                    reference = "ref 8,000 · cap 9,000",
                    weight = if (stepsLog != null) wSteps else 0.0,
                    logHazard = stepsLog ?: 0.0,
                    yearsDelta = stepsLog?.let { (wSteps * it) / G } ?: 0.0,
                    included = stepsLog != null,
                    saturated = inputs.dailySteps != null && inputs.dailySteps >= 9_000.0,
                ),
            )

            // Core 3: Sleep Duration
            val sleepLog = inputs.sleepHours?.let(::sleepLogHazard)
            add(
                ChaDomainResult(
                    domain = ChaDomain.SleepDuration,
                    measuredValue = inputs.sleepHours,
                    unit = "h/night",
                    reference = "optimal 6.0–9.0 h",
                    weight = if (sleepLog != null) wSleep else 0.0,
                    logHazard = sleepLog ?: 0.0,
                    yearsDelta = sleepLog?.let { (wSleep * it) / G } ?: 0.0,
                    included = sleepLog != null,
                    saturated = inputs.sleepHours != null && inputs.sleepHours in 6.0..9.0,
                ),
            )

            // Core 4: Weekly Active Zone Minutes
            val azmLog = inputs.weeklyAzm?.let(::azmLogHazard)
            add(
                ChaDomainResult(
                    domain = ChaDomain.WeeklyAzm,
                    measuredValue = inputs.weeklyAzm,
                    unit = "min/wk",
                    reference = "ref <115 · cap 500",
                    weight = if (azmLog != null) wAzm else 0.0,
                    logHazard = azmLog ?: 0.0,
                    yearsDelta = azmLog?.let { (wAzm * it) / G } ?: 0.0,
                    included = azmLog != null,
                    saturated = inputs.weeklyAzm != null && inputs.weeklyAzm >= 500.0,
                ),
            )

            // Optional 1: VO2 max (FRIEND equation)
            val vo2Included = config.includeVo2Max &&
                inputs.vo2Max != null &&
                inputs.chronologicalAge != null &&
                inputs.sex != null &&
                inputs.weightKg != null &&
                inputs.weightKg > 0.0
            val vo2Log = if (vo2Included) {
                vo2MaxLogHazard(
                    vo2Max = inputs.vo2Max!!,
                    ageYears = inputs.chronologicalAge!!,
                    sex = inputs.sex!!,
                    weightKg = inputs.weightKg!!,
                )
            } else {
                null
            }
            val expectedVo2 = if (inputs.chronologicalAge != null &&
                inputs.sex != null &&
                inputs.weightKg != null &&
                inputs.weightKg > 0.0
            ) {
                expectedVo2Max(inputs.chronologicalAge, inputs.sex, inputs.weightKg)
            } else {
                null
            }
            add(
                ChaDomainResult(
                    domain = ChaDomain.Vo2Max,
                    measuredValue = inputs.vo2Max,
                    unit = "ml/kg/min",
                    reference = expectedVo2?.let { "FRIEND norm %.1f".format(it) }
                        ?: "needs age, sex & weight",
                    weight = if (vo2Log != null) 1.0 else 0.0,
                    logHazard = vo2Log ?: 0.0,
                    yearsDelta = vo2Log?.let { it / G } ?: 0.0,
                    included = vo2Log != null,
                    saturated = false,
                ),
            )

            // Optional 2: Sleep Regularity (SD of sleep midpoint)
            val regIncluded = config.includeSleepRegularity && inputs.sleepMidpointSdMinutes != null
            val regLog = if (regIncluded) {
                sleepRegularityLogHazard(inputs.sleepMidpointSdMinutes!!)
            } else {
                null
            }
            add(
                ChaDomainResult(
                    domain = ChaDomain.SleepRegularity,
                    measuredValue = inputs.sleepMidpointSdMinutes,
                    unit = "min SD",
                    reference = "optimal ≤ 30 min",
                    weight = if (regLog != null) 1.0 else 0.0,
                    logHazard = regLog ?: 0.0,
                    yearsDelta = regLog?.let { it / G } ?: 0.0,
                    included = regLog != null,
                    saturated = inputs.sleepMidpointSdMinutes != null && inputs.sleepMidpointSdMinutes <= 30.0,
                ),
            )

            // Optional 3: Insulin Resistance (optional lab HOMA-IR status)
            val irIncluded = inputs.insulinResistant != null
            val irLog = if (irIncluded) {
                insulinResistanceLogHazard(inputs.insulinResistant!!)
            } else {
                null
            }
            add(
                ChaDomainResult(
                    domain = ChaDomain.InsulinResistance,
                    measuredValue = inputs.insulinResistant?.let { if (it) 1.0 else 0.0 },
                    unit = "",
                    reference = "optional HOMA-IR ≥ 2.9",
                    weight = if (irLog != null) 1.0 else 0.0,
                    logHazard = irLog ?: 0.0,
                    yearsDelta = irLog?.let { it / G } ?: 0.0,
                    included = irLog != null,
                    saturated = inputs.insulinResistant == false,
                ),
            )
        }

        val corePresent = domains.count { !it.domain.optional && it.included }
        val rawDelta = domains.sumOf { it.yearsDelta }
        val healthAge = if (inputs.chronologicalAge != null && corePresent > 0) {
            max(MIN_HEALTH_AGE, inputs.chronologicalAge + rawDelta)
        } else {
            null
        }
        val effectiveDelta = if (inputs.chronologicalAge != null && healthAge != null) {
            healthAge - inputs.chronologicalAge
        } else {
            null
        }

        // Core-only theoretical rejuvenation floor (Section 4.1.5).
        val coreFloorDelta = (wSteps * stepsLogHazard(9_000.0) + wAzm * azmLogHazard(500.0)) / G

        return ChaEstimate(
            chronologicalAge = inputs.chronologicalAge,
            healthAge = healthAge,
            deltaYears = effectiveDelta,
            rawDeltaYears = if (corePresent > 0) rawDelta else null,
            corePresent = corePresent,
            coreFloorDelta = coreFloorDelta,
            domains = domains,
            collinearityAdjusted = config.collinearityAdjusted,
        )
    }

    /**
     * Main sleep duration for a day in hours.
     *
     * Cuff merges naps into [Night.asleep] while keeping each individual sleep
     * session in [Night.parts]. CHA is calibrated on main sleep duration
     * (Section 4.1.8.1 & Table 1), so when multiple sessions exist we take the
     * longest session of the day.
     */
    fun mainSleepHours(night: Night): Double {
        val main = if (night.parts.isNotEmpty()) {
            night.parts.maxByOrNull { it.asleep.seconds } ?: night
        } else {
            night
        }
        return main.asleep.seconds / 3600.0
    }

    /**
     * Circular standard deviation of sleep midpoint in minutes on the 24-hour
     * clock face. Needs at least [MIN_REGULARITY_NIGHTS] nights to be meaningful.
     *
     * Uses RMS circular distance around the clock-face circular mean rather
     * than linear SD, so 23:30 and 00:30 are 60 minutes apart instead of 23 hours.
     */
    fun sleepMidpointSdMinutes(
        nights: List<Night>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Double? {
        if (nights.size < MIN_REGULARITY_NIGHTS) return null
        val midpoints = nights.map { night ->
            val main = if (night.parts.isNotEmpty()) {
                night.parts.maxByOrNull { it.asleep.seconds } ?: night
            } else {
                night
            }
            val t = main.midpoint.atZone(zone).toLocalTime()
            t.hour * 60.0 + t.minute + t.second / 60.0
        }
        val mean = circularMeanMinutes(midpoints)
        val sumSq = midpoints.sumOf { m ->
            val d = circularDistanceMinutes(m, mean)
            d * d
        }
        return sqrt(sumSq / (midpoints.size - 1))
    }

    /**
     * Aggregates daily Health Connect series + cached daily Active Zone Minutes
     * into ISO calendar weeks (Eq. 26: normalized by observed days per week)
     * and computes both the trailing 90-day CHA and weekly CHA history.
     */
    fun summarize(
        snapshot: HealthSnapshot,
        dailyZones: Map<Long, DailyZoneSummary>,
        profile: FitnessProfile,
        config: ChaConfig = ChaConfig(),
        insulinResistant: Boolean? = null,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ChaSummary {
        val todayEpoch = today.toEpochDay()
        val windowStart = todayEpoch - WINDOW_DAYS + 1
        val habitualStart = todayEpoch - HABITUAL_WINDOW_DAYS + 1

        val stepsByDay = snapshot.steps.associate { it.day to it.value }
        val rhrByDay = snapshot.restingHeartRate.associate { it.day to it.value }
        val hrvByDay = snapshot.hrv.associate { it.day to it.value }
        val respByDay = snapshot.respiratoryRate.associate { it.day to it.value }
        val sleepByDay = snapshot.nights.associate { it.day to mainSleepHours(it) }
        val nightsByDay = snapshot.nights.associateBy { it.day }

        val allDays = buildSet {
            addAll(stepsByDay.keys)
            addAll(rhrByDay.keys)
            addAll(sleepByDay.keys)
            addAll(dailyZones.keys)
        }.filter { it in habitualStart..todayEpoch }.sorted()

        val weekFields = WeekFields.ISO
        fun weekKey(epochDay: Long): IsoWeekKey {
            val d = LocalDate.ofEpochDay(epochDay)
            return IsoWeekKey(
                weekYear = d.get(weekFields.weekBasedYear()),
                weekNumber = d.get(weekFields.weekOfWeekBasedYear()),
                mondayEpochDay = d.with(weekFields.dayOfWeek(), 1).toEpochDay(),
            )
        }

        val weeklyBuckets = allDays.groupBy(::weekKey).entries
            .sortedBy { it.key.mondayEpochDay }
            .map { (key, days) ->
                val stepsVals = days.mapNotNull { stepsByDay[it] }
                val rhrVals = days.mapNotNull { rhrByDay[it] }
                val sleepVals = days.mapNotNull { sleepByDay[it] }
                val zoneVals = days.mapNotNull { dailyZones[it]?.takeIf { z -> z.sampleCount > 0 } }
                val hrvVals = days.mapNotNull { hrvByDay[it] }
                val respVals = days.mapNotNull { respByDay[it] }
                val completeDays = days.count { d ->
                    stepsByDay[d] != null && rhrByDay[d] != null && sleepByDay[d] != null
                }
                // Eq. (26): normalize daily metrics by observed days, then scale
                // daily AZM to a 7-day weekly rate so partial weeks aren't penalized.
                val dailyAzmMean = zoneVals.takeIf { it.isNotEmpty() }?.map { it.azm }?.average()
                val dailyCardioMean = zoneVals.takeIf { it.isNotEmpty() }?.map { it.vigorousCardioMin }?.average()
                val dailyModMean = zoneVals.takeIf { it.isNotEmpty() }?.map { it.moderateMin }?.average()

                WeeklyWearableVector(
                    weekStartEpochDay = key.mondayEpochDay,
                    observedDays = days.size,
                    completeDays = completeDays,
                    meanDailySteps = stepsVals.takeIf { it.isNotEmpty() }?.average(),
                    meanRhrBpm = rhrVals.takeIf { it.isNotEmpty() }?.average(),
                    meanSleepHours = sleepVals.takeIf { it.isNotEmpty() }?.average(),
                    weeklyAzm = dailyAzmMean?.times(7.0),
                    meanDailyCardioMin = dailyCardioMean,
                    meanDailyModerateMin = dailyModMean,
                    meanHrvMs = hrvVals.takeIf { it.isNotEmpty() }?.average(),
                    meanRespRate = respVals.takeIf { it.isNotEmpty() }?.average(),
                    zoneDays = zoneVals.size,
                )
            }

        val ageDouble = profile.age?.toDouble()
        val latestVo2 = snapshot.vo2Max.maxByOrNull { it.day }?.value
        val latestWeightKg = snapshot.weight.maxByOrNull { it.day }?.value

        // Trailing 90-day window inputs (Section 4.1.8.1).
        val recentDays = allDays.filter { it >= windowStart }
        val recentSteps = recentDays.mapNotNull { stepsByDay[it] }
        val recentRhr = recentDays.mapNotNull { rhrByDay[it] }
        val recentSleep = recentDays.mapNotNull { sleepByDay[it] }
        val recentNights = recentDays.mapNotNull { nightsByDay[it] }
        val recentZones = recentDays.mapNotNull { dailyZones[it]?.takeIf { z -> z.sampleCount > 0 } }

        val recentWeeks = weeklyBuckets.filter { it.weekStartEpochDay + 6 >= windowStart }
        val validRecentWeeks = recentWeeks.count { it.isValid }

        val windowWeeklyAzm = recentZones.takeIf { it.isNotEmpty() }
            ?.map { it.azm }
            ?.average()
            ?.times(7.0)
        val windowSleepSd = sleepMidpointSdMinutes(recentNights, zone)

        val currentInputs = ChaInputs(
            chronologicalAge = ageDouble,
            restingHeartRateBpm = recentRhr.takeIf { it.isNotEmpty() }?.average(),
            dailySteps = recentSteps.takeIf { it.isNotEmpty() }?.average(),
            sleepHours = recentSleep.takeIf { it.isNotEmpty() }?.average(),
            weeklyAzm = windowWeeklyAzm,
            vo2Max = latestVo2,
            sex = profile.sex,
            weightKg = latestWeightKg,
            sleepMidpointSdMinutes = windowSleepSd,
            insulinResistant = insulinResistant,
        )
        val current = evaluate(currentInputs, config)

        // Weekly longitudinal CHA series (using each week's own core metrics +
        // latest static/optional values when enabled, so the curve reflects real
        // week-to-week habit and RHR movement).
        val weeklyHistory = weeklyBuckets.mapNotNull { week ->
            val est = evaluate(
                ChaInputs(
                    chronologicalAge = ageDouble,
                    restingHeartRateBpm = week.meanRhrBpm,
                    dailySteps = week.meanDailySteps,
                    sleepHours = week.meanSleepHours,
                    weeklyAzm = week.weeklyAzm,
                    vo2Max = latestVo2,
                    sex = profile.sex,
                    weightKg = latestWeightKg,
                    sleepMidpointSdMinutes = windowSleepSd,
                    insulinResistant = insulinResistant,
                ),
                config,
            )
            val ha = est.healthAge ?: return@mapNotNull null
            val delta = est.deltaYears ?: return@mapNotNull null
            WeeklyChaPoint(
                weekStartEpochDay = week.weekStartEpochDay,
                healthAge = ha,
                deltaYears = delta,
                validWeek = week.isValid,
                corePresent = est.corePresent,
            )
        }

        return ChaSummary(
            estimate = current,
            inputs = currentInputs,
            weeklyVectors = weeklyBuckets,
            weeklyHistory = weeklyHistory,
            validWeeksIn90Days = validRecentWeeks,
            totalWeeksAvailable = weeklyBuckets.count { it.isValid },
            observedDaysIn90Days = recentDays.size,
            zoneDaysIn90Days = recentZones.size,
            calibrated = validRecentWeeks >= MIN_VALID_WEEKS && current.corePresent == 4,
        )
    }

    private const val MIN_REGULARITY_NIGHTS = 5

    private fun circularMeanMinutes(minutes: List<Double>): Double {
        if (minutes.isEmpty()) return 0.0
        val rad = minutes.map { it / 1440.0 * 2.0 * Math.PI }
        val x = rad.sumOf { cos(it) } / rad.size
        val y = rad.sumOf { sin(it) } / rad.size
        val angle = atan2(y, x).let { if (it < 0) it + 2.0 * Math.PI else it }
        return angle / (2.0 * Math.PI) * 1440.0
    }

    private fun circularDistanceMinutes(a: Double, b: Double): Double {
        val raw = abs(a - b)
        return min(raw, 1440.0 - raw)
    }

    private data class IsoWeekKey(
        val weekYear: Int,
        val weekNumber: Int,
        val mondayEpochDay: Long,
    )
}

enum class ChaDomain(val label: String, val optional: Boolean) {
    RestingHeartRate("Resting heart rate", false),
    DailySteps("Daily steps", false),
    SleepDuration("Main sleep", false),
    WeeklyAzm("Active Zone Minutes", false),
    Vo2Max("VO₂ max (FRIEND)", true),
    SleepRegularity("Sleep regularity", true),
    InsulinResistance("Insulin resistance", true),
}

data class ChaConfig(
    val collinearityAdjusted: Boolean = true,
    val includeVo2Max: Boolean = true,
    val includeSleepRegularity: Boolean = true,
)

data class ChaInputs(
    val chronologicalAge: Double?,
    val restingHeartRateBpm: Double? = null,
    val dailySteps: Double? = null,
    val sleepHours: Double? = null,
    val weeklyAzm: Double? = null,
    val vo2Max: Double? = null,
    val sex: Sex? = null,
    val weightKg: Double? = null,
    val sleepMidpointSdMinutes: Double? = null,
    val insulinResistant: Boolean? = null,
)

data class ChaDomainResult(
    val domain: ChaDomain,
    val measuredValue: Double?,
    val unit: String,
    val reference: String,
    val weight: Double,
    val logHazard: Double,
    val yearsDelta: Double,
    val included: Boolean,
    val saturated: Boolean,
)

data class ChaEstimate(
    val chronologicalAge: Double?,
    val healthAge: Double?,
    val deltaYears: Double?,
    val rawDeltaYears: Double?,
    val corePresent: Int,
    val coreFloorDelta: Double,
    val domains: List<ChaDomainResult>,
    val collinearityAdjusted: Boolean,
)

data class WeeklyWearableVector(
    val weekStartEpochDay: Long,
    val observedDays: Int,
    val completeDays: Int,
    val meanDailySteps: Double?,
    val meanRhrBpm: Double?,
    val meanSleepHours: Double?,
    val weeklyAzm: Double?,
    val meanDailyCardioMin: Double?,
    val meanDailyModerateMin: Double?,
    val meanHrvMs: Double?,
    val meanRespRate: Double?,
    val zoneDays: Int,
) {
    val isValid: Boolean get() = completeDays >= CoachableHealthAge.MIN_VALID_DAYS_PER_WEEK
}

data class WeeklyChaPoint(
    val weekStartEpochDay: Long,
    val healthAge: Double,
    val deltaYears: Double,
    val validWeek: Boolean,
    val corePresent: Int,
)

data class ChaSummary(
    val estimate: ChaEstimate,
    val inputs: ChaInputs,
    val weeklyVectors: List<WeeklyWearableVector>,
    val weeklyHistory: List<WeeklyChaPoint>,
    val validWeeksIn90Days: Int,
    val totalWeeksAvailable: Int,
    val observedDaysIn90Days: Int,
    val zoneDaysIn90Days: Int,
    val calibrated: Boolean,
)
