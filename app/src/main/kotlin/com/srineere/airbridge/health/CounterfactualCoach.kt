package com.srineere.airbridge.health

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Feasibility-constrained 12-week counterfactual optimization and Appendix C.3
 * coaching prompt synthesis from Section 4.3 of Lee et al. (2026)
 * (arXiv:2611.08568v1).
 *
 * Enforces the paper's Action–Outcome Firewall: only volitional behaviors
 * (daily steps, main sleep duration, cardio exercise minutes) are exposed as
 * candidate targets; resting heart rate, Active Zone Minutes, and Coachable
 * Health Age are strictly projected outcomes.
 */
object CounterfactualCoach {

    /** Horizon in weeks (Section 4.3.2). */
    const val HORIZON_WEEKS = 12

    /** Personalized effort SD safety floors (Eq. 55, Section 4.3.2). */
    const val STEPS_STD_FLOOR = 500.0
    const val SLEEP_MIN_STD_FLOOR = 15.0
    const val CARDIO_MIN_STD_FLOOR = 10.0

    /** Minimal clinically meaningful CHA reduction threshold in years (Section 4.3.2). */
    const val MIN_CHA_REDUCTION = 0.1

    /** Minimum valid weekly vectors needed before fitting the N=1 surrogate. */
    const val MIN_SURROGATE_WEEKS = 6

    private const val SLEEP_CAP_HOURS = 9.0
    private const val STEP_SATURATION = 9_000.0
    private const val AZM_SATURATION = 500.0

    /**
     * Eq. (55): Sum of squared self-standardized behavioral deviations from
     * baseline, bounded below by safety floors so highly consistent users are
     * not given near-infinite effort penalties.
     */
    fun personalizedEffort(
        baseline: BehaviorTarget,
        candidate: BehaviorTarget,
        habitual: HabitualStats,
    ): Double {
        val sSteps = max(habitual.steps.std, STEPS_STD_FLOOR)
        val sSleepMin = max(habitual.sleepHours.std * 60.0, SLEEP_MIN_STD_FLOOR)
        val sCardioMin = max(habitual.cardioMinPerDay.std, CARDIO_MIN_STD_FLOOR)

        val dSteps = (candidate.dailySteps - baseline.dailySteps) / sSteps
        val dSleep = ((candidate.sleepHours - baseline.sleepHours) * 60.0) / sSleepMin
        val dCardio = (candidate.cardioMinPerDay - baseline.cardioMinPerDay) / sCardioMin

        return dSteps * dSteps + dSleep * dSleep + dCardio * dCardio
    }

    /** Eq. (56): Projected Health Age reduction per unit of personalized effort. */
    fun optimizationScore(statusQuoCha: Double, candidateCha: Double, effort: Double): Double =
        (statusQuoCha - candidateCha) / (1.0 + max(effort, 0.0))

    /**
     * Computes 24-week habitual distributions and recorded peaks from weekly
     * wearable vectors (Section 4.3.1), falling back gracefully when fewer weeks
     * are available.
     */
    fun habitualStats(
        summary: ChaSummary,
        dailyZones: Map<Long, DailyZoneSummary>,
        snapshot: HealthSnapshot,
    ): HabitualStats {
        val weeks = summary.weeklyVectors
        val stepSeries = weeks.mapNotNull { it.meanDailySteps }
            .ifEmpty { snapshot.steps.map { it.value } }
        val sleepSeries = weeks.mapNotNull { it.meanSleepHours }
            .ifEmpty { snapshot.nights.map(CoachableHealthAge::mainSleepHours) }
        val cardioSeries = weeks.mapNotNull { it.meanDailyCardioMin }
            .ifEmpty {
                dailyZones.values.filter { it.sampleCount > 0 }.map { it.vigorousCardioMin }
            }
        val azmSeries = weeks.mapNotNull { it.weeklyAzm }
            .ifEmpty {
                dailyZones.values.filter { it.sampleCount > 0 }.map { it.azm * 7.0 }
            }

        return HabitualStats(
            weeksObserved = weeks.size,
            steps = distributionOf(stepSeries, fallback = summary.inputs.dailySteps ?: 0.0),
            sleepHours = distributionOf(sleepSeries, fallback = summary.inputs.sleepHours ?: 7.0),
            cardioMinPerDay = distributionOf(cardioSeries, fallback = 0.0),
            weeklyAzm = distributionOf(azmSeries, fallback = summary.inputs.weeklyAzm ?: 0.0),
        )
    }

    /**
     * Runs the complete counterfactual simulation and feasibility-constrained
     * pathway selection for a user's current [ChaSummary].
     */
    fun plan(
        summary: ChaSummary,
        dailyZones: Map<Long, DailyZoneSummary>,
        snapshot: HealthSnapshot,
        config: ChaConfig = ChaConfig(),
        mode: SimulationMode = SimulationMode.Analytical,
    ): CounterfactualPlan? {
        val baseCha = summary.estimate.healthAge ?: return null
        val age = summary.inputs.chronologicalAge ?: return null
        val habitual = habitualStats(summary, dailyZones, snapshot)

        val baselineSteps = summary.inputs.dailySteps ?: habitual.steps.mean
        val baselineSleep = summary.inputs.sleepHours ?: habitual.sleepHours.mean
        val baselineRhr = summary.inputs.restingHeartRateBpm
        val baselineAzm = summary.inputs.weeklyAzm
        val baselineCardio = summary.weeklyVectors.takeLast(12)
            .mapNotNull { it.meanDailyCardioMin }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?: habitual.cardioMinPerDay.mean

        val baselineTarget = BehaviorTarget(
            dailySteps = baselineSteps,
            sleepHours = baselineSleep,
            cardioMinPerDay = baselineCardio,
        )

        val surrogate = if (mode == SimulationMode.PersonalSurrogate) {
            fitPersonalSurrogate(summary.weeklyVectors)
        } else {
            null
        }
        val effectiveMode = if (surrogate != null) SimulationMode.PersonalSurrogate else SimulationMode.Analytical

        val diagnostics = when {
            mode == SimulationMode.Analytical -> SurrogateDiagnostics(
                requestedMode = mode,
                effectiveMode = SimulationMode.Analytical,
                trainingWeeks = summary.totalWeeksAvailable,
                rhrR2 = null,
                note = "Exact CHA equations over your volitional levers (RHR held at baseline).",
            )
            surrogate != null -> SurrogateDiagnostics(
                requestedMode = mode,
                effectiveMode = SimulationMode.PersonalSurrogate,
                trainingWeeks = surrogate.trainingWeeks,
                rhrR2 = surrogate.rhrR2,
                note = "Experimental N=1 ridge dynamics fit on ${surrogate.trainingWeeks} of your weeks " +
                    "(clamped to ±6 bpm over 12 wk; substitute for unreleased SensorWM weights).",
            )
            else -> SurrogateDiagnostics(
                requestedMode = mode,
                effectiveMode = SimulationMode.Analytical,
                trainingWeeks = summary.totalWeeksAvailable,
                rhrR2 = null,
                note = "Personal N=1 model needs at least $MIN_SURROGATE_WEEKS valid weeks " +
                    "(you have ${summary.totalWeeksAvailable}); using Analytical CHA for now.",
            )
        }

        // Future A: Status quo rollout (Candidate 0).
        val statusQuo = simulateTarget(
            baselineInputs = summary.inputs,
            baselineTarget = baselineTarget,
            candidate = baselineTarget,
            config = config,
            surrogate = surrogate,
        )

        // Build feasible candidate grids bounded by personal 24-week peaks and
        // saturation ceilings (Section 3 Discussion & Section 4.3.2).
        val sleepCandidates = sleepGrid(baselineSleep, habitual.sleepHours.peak)
        val stepCandidates = stepsGrid(baselineSteps, habitual.steps.peak)
        val cardioCandidates = if (baselineAzm != null) {
            cardioGrid(baselineCardio, baselineAzm, habitual.cardioMinPerDay.peak)
        } else {
            listOf(baselineCardio)
        }

        fun evaluateCandidate(
            kind: PathwayKind,
            target: BehaviorTarget,
        ): CounterfactualOption? {
            val rollout = simulateTarget(
                baselineInputs = summary.inputs,
                baselineTarget = baselineTarget,
                candidate = target,
                config = config,
                surrogate = surrogate,
            )
            val chaReduction = statusQuo.endpointCha - rollout.endpointCha
            if (chaReduction <= MIN_CHA_REDUCTION) return null
            val effort = personalizedEffort(baselineTarget, target, habitual)
            val score = optimizationScore(statusQuo.endpointCha, rollout.endpointCha, effort)
            val withinPeak = target.dailySteps <= max(habitual.steps.peak, baselineSteps) + 1.0 &&
                target.sleepHours <= max(habitual.sleepHours.peak, baselineSleep) + 0.05 &&
                target.cardioMinPerDay <= max(habitual.cardioMinPerDay.peak, baselineCardio) + 0.5
            return CounterfactualOption(
                kind = kind,
                target = target,
                baseline = baselineTarget,
                projectedCha = rollout.endpointCha,
                chaDeltaVsStatusQuo = rollout.endpointCha - statusQuo.endpointCha,
                projectedRhr = rollout.endpointRhr,
                projectedWeeklyAzm = rollout.endpointWeeklyAzm,
                effort = effort,
                score = score,
                withinHistoricalPeak = withinPeak,
                trajectory = rollout.trajectory,
            )
        }

        // 1. Sleep-focused pathway
        val bestSleep = sleepCandidates
            .filter { abs(it - baselineSleep) >= 0.24 }
            .mapNotNull { s ->
                evaluateCandidate(
                    PathwayKind.Sleep,
                    baselineTarget.copy(sleepHours = s),
                )
            }
            .maxByOrNull { it.score }

        // 2. Movement-focused pathway (steps and/or cardio)
        val bestMovement = buildList {
            for (st in stepCandidates) {
                for (ca in cardioCandidates) {
                    if (abs(st - baselineSteps) < 250.0 && abs(ca - baselineCardio) < 2.0) continue
                    evaluateCandidate(
                        PathwayKind.Movement,
                        baselineTarget.copy(dailySteps = st, cardioMinPerDay = ca),
                    )?.let(::add)
                }
            }
        }.maxByOrNull { it.score }

        // 3. Combined pathway (requires moving both sleep AND movement)
        val bestCombined = buildList {
            val activeSleep = sleepCandidates.filter { abs(it - baselineSleep) >= 0.24 }
            for (sl in activeSleep) {
                for (st in stepCandidates) {
                    for (ca in cardioCandidates) {
                        if (abs(st - baselineSteps) < 250.0 && abs(ca - baselineCardio) < 2.0) continue
                        evaluateCandidate(
                            PathwayKind.Combined,
                            BehaviorTarget(dailySteps = st, sleepHours = sl, cardioMinPerDay = ca),
                        )?.let(::add)
                    }
                }
            }
        }.maxByOrNull { it.score }

        val rankedOptions = listOfNotNull(bestSleep, bestMovement, bestCombined)
            .sortedByDescending { it.score }

        val persona = classifyPersona(
            dailySteps = baselineSteps,
            sleepHours = baselineSleep,
            deltaCha = baseCha - age,
        )

        return CounterfactualPlan(
            baseline = baselineTarget,
            baselineCha = baseCha,
            baselineRhr = baselineRhr,
            baselineWeeklyAzm = baselineAzm,
            habitual = habitual,
            statusQuo = statusQuo,
            options = rankedOptions,
            persona = persona,
            diagnostics = diagnostics,
            surrogate = surrogate,
            effectiveMode = effectiveMode,
        )
    }

    /**
     * Simulates an arbitrary custom [candidate] target for the interactive
     * What-If simulator on the G-Age tab.
     */
    fun simulateCustom(
        summary: ChaSummary,
        plan: CounterfactualPlan,
        candidate: BehaviorTarget,
        config: ChaConfig = ChaConfig(),
    ): CustomSimulation {
        val rollout = simulateTarget(
            baselineInputs = summary.inputs,
            baselineTarget = plan.baseline,
            candidate = candidate,
            config = config,
            surrogate = plan.surrogate,
        )
        val effort = personalizedEffort(plan.baseline, candidate, plan.habitual)
        val score = optimizationScore(plan.statusQuo.endpointCha, rollout.endpointCha, effort)
        return CustomSimulation(
            target = candidate,
            rollout = rollout,
            chaDeltaVsStatusQuo = rollout.endpointCha - plan.statusQuo.endpointCha,
            chaDeltaVsBaseline = rollout.endpointCha - plan.baselineCha,
            effort = effort,
            score = score,
        )
    }

    /**
     * Builds the paper's verbatim Appendix C.3 Counterfactual Coaching Prompt
     * (Prompt 2) populated with the user's real Fitbit Air stats, 24-week
     * peaks, Future A status quo, and feasibility-ranked Options 1–3.
     *
     * Designed for one-tap Copy / Share to any external LLM while keeping Cuff
     * 100% offline with zero INTERNET permission.
     */
    fun buildCounterfactualCoachPrompt(
        summary: ChaSummary,
        plan: CounterfactualPlan,
        profile: FitnessProfile,
        heightCm: Int? = null,
    ): String {
        val age = profile.age?.toString() ?: "Unknown"
        val sex = profile.sex?.label ?: "Not specified"
        val weightKg = summary.inputs.weightKg
        val bmiText = if (weightKg != null && heightCm != null && heightCm > 0) {
            val hm = heightCm / 100.0
            "%.1f".format(weightKg / (hm * hm))
        } else {
            "Not specified"
        }

        val baseRhr = plan.baselineRhr?.let { "%.1f".format(it) } ?: "Not available"
        val baseAzmPerDay = plan.baselineWeeklyAzm?.let { "%.1f".format(it / 7.0) } ?: "Not available"
        val sqRhr = plan.statusQuo.endpointRhr?.let { "%.1f".format(it) } ?: baseRhr
        val sqAzmPerDay = plan.statusQuo.endpointWeeklyAzm?.let { "%.1f".format(it / 7.0) } ?: baseAzmPerDay

        val optionsBlock = if (plan.options.isEmpty()) {
            "- Option 1 (Maintain Optimal Baseline): Sustain current daily steps " +
                "(${plan.baseline.dailySteps.roundToInt()} steps/day), sleep " +
                "(${"%.1f".format(plan.baseline.sleepHours)} h/night), and cardio " +
                "(${plan.baseline.cardioMinPerDay.roundToInt()} mins/day) -> " +
                "Projected Health Age: ${"%.1f".format(plan.statusQuo.endpointCha)} years (0.0 yrs), " +
                "Projected RHR: $sqRhr bpm"
        } else {
            plan.options.mapIndexed { idx, opt ->
                val rhrStr = opt.projectedRhr?.let { "%.1f".format(it) } ?: baseRhr
                val deltaStr = "%+.1f".format(opt.chaDeltaVsStatusQuo)
                "- Option ${idx + 1} (${opt.kind.label}): ${opt.describeDeltas()} -> " +
                    "Projected Health Age: ${"%.1f".format(opt.projectedCha)} years " +
                    "($deltaStr yrs), Projected RHR: $rhrStr bpm"
            }.joinToString("\n")
        }

        val rhrNote = if (plan.effectiveMode == SimulationMode.Analytical) {
            "\n* Note: Projections use the analytical Coachable Health Age model " +
                "(holding Resting Heart Rate constant at baseline rather than predicting RHR drift)."
        } else {
            "\n* Note: Projected RHR uses an experimental single-user trajectory estimate " +
                "fitted on ${plan.diagnostics.trainingWeeks} weeks of personal wearable history."
        }

        val h = plan.habitual
        return buildString {
            appendLine("Prompt for Counterfactual 12-Week Lifestyle Coaching")
            appendLine()
            appendLine("### ROLE & OBJECTIVE")
            appendLine(
                "You are an expert, certified Health and Wellness Coach grounded in Self-Determination " +
                    "Theory (SDT) and Motivational Interviewing. Your objective is to write a personalized, " +
                    "supportive, and actionable 12-week lifestyle coaching plan (500--700 words) for a " +
                    "participant using their 24-week wearable history, their Coachable Health Age, and " +
                    "their personalized 12-week counterfactual health simulations.",
            )
            appendLine()
            appendLine("### PARTICIPANT CONTEXT & COUNTERFACTUAL SIMULATIONS")
            appendLine("* Demographics: Age: $age years | Sex: $sex | BMI: $bmiText kg/m2")
            appendLine("* Recent Measured Baseline (Daily Averages):")
            appendLine("- Chronological Age: $age years")
            appendLine("- Current Coachable Health Age: ${"%.1f".format(plan.baselineCha)} years")
            appendLine("- Resting Heart Rate (RHR): $baseRhr bpm")
            appendLine("- Daily Steps: ${plan.baseline.dailySteps.roundToInt()} steps/day")
            appendLine("- Nightly Sleep Duration: ${"%.1f".format(plan.baseline.sleepHours)} hours/night")
            appendLine("- Daily Cardio Workouts: ${plan.baseline.cardioMinPerDay.roundToInt()} mins/day")
            appendLine("- Active Zone Minutes (AZM): $baseAzmPerDay mins/day")
            appendLine("* ${h.weeksObserved}-Week Habitual Distribution & Historical Peaks:")
            appendLine(
                "- Steps: Average ${h.steps.mean.roundToInt()} " +
                    "(Range: ${h.steps.min.roundToInt()}-${h.steps.max.roundToInt()}, " +
                    "Std: +/-${h.steps.std.roundToInt()}) | " +
                    "Best Recorded Peak: ${h.steps.peak.roundToInt()} steps/day",
            )
            appendLine(
                "- Sleep: Average ${"%.1f".format(h.sleepHours.mean)}h " +
                    "(Range: ${"%.1f".format(h.sleepHours.min)}-${"%.1f".format(h.sleepHours.max)}h, " +
                    "Std: +/-${"%.1f".format(h.sleepHours.std)}h) | " +
                    "Best Recorded Peak: ${"%.1f".format(h.sleepHours.peak)} hours/night",
            )
            appendLine(
                "- Cardio: Average ${h.cardioMinPerDay.mean.roundToInt()} mins " +
                    "(Range: ${h.cardioMinPerDay.min.roundToInt()}-${h.cardioMinPerDay.max.roundToInt()} mins) | " +
                    "Best Recorded Peak: ${h.cardioMinPerDay.peak.roundToInt()} mins/day",
            )
            appendLine("* 12-Week Status-Quo Projection (Future A -- If Current Habits Continue Unchanged):")
            appendLine("- Projected Coachable Health Age: ${"%.1f".format(plan.statusQuo.endpointCha)} years")
            appendLine("- Projected Resting Heart Rate: $sqRhr bpm")
            appendLine("- Projected Active Zone Minutes: $sqAzmPerDay mins/day")
            appendLine("* 12-Week Personalized Counterfactual Pathways (Future B -- Simulated Habit Options Bounded by Historical Peaks):")
            appendLine(optionsBlock)
            appendLine(rhrNote)
            appendLine()
            appendLine("### COACHING & CLINICAL GUARDRAILS")
            appendLine(
                "1. Tone: Warm, empathetic, collaborative, and autonomy-supportive. Frame Coachable " +
                    "Health Age and Future A vs. Future B as an empowering compass--never use fear, " +
                    "guilt, or alarmism.",
            )
            appendLine(
                "2. Jargon Ban: Never use the word 'counterfactual', 'simulation', 'model', 'std', or " +
                    "'min/max'. Present Future A and Future B naturally as 'Where Your Current Routine " +
                    "Leads' versus 'Your Personalized Pathways Forward'.",
            )
            appendLine(
                "3. Autonomy & Choice Architecture: Present the pathways clearly so the participant " +
                    "feels complete ownership over choosing the path (Sleep, Movement, or Combined) that " +
                    "best fits their life right now.",
            )
            appendLine(
                "4. Safe & Realistic Calibration: All pathways are bounded by the participant's own " +
                    "historical peaks; reinforce that their body has already demonstrated it can achieve " +
                    "these levels. Recommend joint-friendly modalities when appropriate. Only prescribe " +
                    "volitional behaviors (steps, sleep, cardio) as goals--never instruct them to directly " +
                    "lower their resting heart rate.",
            )
            appendLine("5. Length: Keep the complete response between 500 and 700 words.")
            appendLine()
            appendLine("### REQUIRED 4-SECTION STRUCTURE")
            appendLine("1. YOUR HEALTH SNAPSHOT & HEALTH AGE")
            appendLine(
                "- Warmly welcome the participant and synthesize their wearable metrics (Steps, Sleep, " +
                    "RHR, AZM) and Coachable Health Age into a unified compass.",
            )
            appendLine(
                "- Gently contrast Future A (what happens over the next 12 weeks if current habits stay " +
                    "the same) with Future B (how modifying specific daily levers can lower their Health " +
                    "Age), referencing their historical peaks as proof of capability.",
            )
            appendLine("2. YOUR PERSONALIZED PATHWAYS & 12-WEEK PROGRESSIVE PLAN")
            appendLine(
                "- Present the distinct, quantified Options with their exact 12-week Health Age payoffs, " +
                    "inviting the participant to choose their preferred focus.",
            )
            appendLine("- Lay out a progressive 12-week roadmap divided into three 4-week phases:")
            appendLine("  * Phase 1 (Weeks 1--4: Foundation): Starting gently on their chosen pathway.")
            appendLine("  * Phase 2 (Weeks 5--8: Build): Stepping up toward their target habit change and heart-pumping movement (AZM).")
            appendLine("  * Phase 3 (Weeks 9--12: Sustain): Locking in the routine to realize their 12-week Health Age improvements.")
            appendLine("3. EVIDENCE-BASED HABIT FORMATION")
            appendLine(
                "- Provide concrete behavioral strategies (Habit Stacking, 'If-Then' Implementation " +
                    "Intentions, Self-Monitoring of RHR/energy, and compassionate barrier planning) to " +
                    "support long-term adherence.",
            )
            appendLine("4. SUMMARY & NEXT STEPS")
            appendLine(
                "- Conclude with an encouraging summary and a memorable one-sentence takeaway " +
                    "celebrating their control over their future health trajectory.",
            )
        }
    }

    // ------------------------------------------------------------- internals

    private fun simulateTarget(
        baselineInputs: ChaInputs,
        baselineTarget: BehaviorTarget,
        candidate: BehaviorTarget,
        config: ChaConfig,
        surrogate: PersonalRhrSurrogate?,
    ): TrajectoryRollout {
        val baseAzm = baselineInputs.weeklyAzm
        val baseRhr = baselineInputs.restingHeartRateBpm
        val trajectory = mutableListOf<ProjectedWeek>()

        var currentRhr = baseRhr
        for (week in 1..HORIZON_WEEKS) {
            // Progressive 3-phase ramp matching the 12-week coaching structure
            // (Weeks 1–4 Foundation, Weeks 5–8 Build, Weeks 9–12 Sustain).
            val ramp = when {
                week <= 4 -> 0.35 + 0.05 * (week - 1)
                week <= 8 -> 0.65 + 0.08 * (week - 5)
                else -> 1.0
            }.coerceIn(0.0, 1.0)

            val weekSteps = baselineTarget.dailySteps + ramp * (candidate.dailySteps - baselineTarget.dailySteps)
            val weekSleep = baselineTarget.sleepHours + ramp * (candidate.sleepHours - baselineTarget.sleepHours)
            val weekCardio = baselineTarget.cardioMinPerDay + ramp * (candidate.cardioMinPerDay - baselineTarget.cardioMinPerDay)

            // Each vigorous cardio minute earns 2 AZM; 7 days/week -> +14 AZM/wk per cardio min/day.
            val weekAzm = baseAzm?.let {
                max(0.0, it + 14.0 * (weekCardio - baselineTarget.cardioMinPerDay))
            }

            if (surrogate != null && currentRhr != null && baseRhr != null) {
                val nextRhr = surrogate.stepRhr(
                    currentRhr = currentRhr,
                    steps = weekSteps,
                    sleepHours = weekSleep,
                    cardioMinPerDay = weekCardio,
                )
                // Biological plausibility guard (Section 3 Discussion): clamp
                // cumulative 12-week RHR shift so single-user noise cannot explode.
                currentRhr = nextRhr.coerceIn(
                    max(38.0, baseRhr - 6.0),
                    min(110.0, baseRhr + 4.0),
                )
            }

            val est = CoachableHealthAge.evaluate(
                baselineInputs.copy(
                    restingHeartRateBpm = currentRhr,
                    dailySteps = weekSteps,
                    sleepHours = weekSleep,
                    weeklyAzm = weekAzm,
                ),
                config,
            )
            trajectory += ProjectedWeek(
                week = week,
                healthAge = est.healthAge ?: (baselineInputs.chronologicalAge ?: 30.0),
                rhrBpm = currentRhr,
                weeklyAzm = weekAzm,
            )
        }

        val last = trajectory.last()
        return TrajectoryRollout(
            endpointCha = last.healthAge,
            endpointRhr = last.rhrBpm,
            endpointWeeklyAzm = last.weeklyAzm,
            trajectory = trajectory,
        )
    }

    private fun sleepGrid(baselineHours: Double, peakHours: Double): List<Double> {
        val out = linkedSetOf(baselineHours)
        when {
            baselineHours < 6.5 -> {
                // Target upward in 15-min steps up to optimal range, bounded by
                // personal peak (or at least 7.0 h so chronic short sleepers get a path).
                val ceiling = min(SLEEP_CAP_HOURS, max(peakHours, 7.0))
                var h = baselineHours + 0.25
                while (h <= ceiling + 1e-6) {
                    out += h
                    h += 0.25
                }
            }
            baselineHours > SLEEP_CAP_HOURS -> {
                // Target downward toward the optimal <= 9.0 h range.
                var h = baselineHours - 0.25
                while (h >= 7.5 - 1e-6) {
                    out += h
                    h -= 0.25
                }
            }
        }
        return out.toList()
    }

    private fun stepsGrid(baselineSteps: Double, peakSteps: Double): List<Double> {
        val out = linkedSetOf(baselineSteps)
        // Baseline-aware saturation ceiling (fixes Discussion Persona 3 failure mode):
        // once already >= 9,000 steps/day, additional steps produce zero direct CHA gain.
        if (baselineSteps >= STEP_SATURATION) return out.toList()
        val ceiling = min(STEP_SATURATION, max(peakSteps, baselineSteps + 2_500.0))
        var s = baselineSteps + 500.0
        while (s <= ceiling + 1e-6) {
            out += s
            s += 500.0
        }
        if (ceiling > baselineSteps + 100.0) out += ceiling
        return out.toList()
    }

    private fun cardioGrid(
        baselineCardioMin: Double,
        baselineWeeklyAzm: Double,
        peakCardioMin: Double,
    ): List<Double> {
        val out = linkedSetOf(baselineCardioMin)
        if (baselineWeeklyAzm >= AZM_SATURATION) return out.toList()
        val azmGapCardioMin = (AZM_SATURATION - baselineWeeklyAzm) / 14.0
        val ceiling = min(
            baselineCardioMin + min(azmGapCardioMin + 2.0, 25.0),
            max(peakCardioMin, baselineCardioMin + 15.0),
        ).coerceAtMost(60.0)
        var c = baselineCardioMin + 3.0
        while (c <= ceiling + 1e-6) {
            out += c
            c += 3.0
        }
        if (ceiling > baselineCardioMin + 1.0) out += ceiling
        return out.toList()
    }

    private fun distributionOf(values: List<Double>, fallback: Double): ActionDistribution {
        if (values.isEmpty()) {
            return ActionDistribution(fallback, 0.0, fallback, fallback, fallback)
        }
        val mean = values.average()
        val variance = if (values.size > 1) {
            values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        } else {
            0.0
        }
        val sorted = values.sorted()
        return ActionDistribution(
            mean = mean,
            std = sqrt(variance),
            min = sorted.first(),
            max = sorted.last(),
            peak = sorted.last(),
        )
    }

    /**
     * Classifies the user into one of the five WEAR-ME Coachable Personas
     * defined in Section 4.3.4 (adapted cleanly without requiring BMI).
     */
    private fun classifyPersona(
        dailySteps: Double,
        sleepHours: Double,
        deltaCha: Double,
    ): CoachablePersona = when {
        dailySteps >= 10_000.0 && sleepHours in 7.0..9.0 && deltaCha < -1.0 ->
            CoachablePersona.OptimalBaseline
        dailySteps >= 10_000.0 && sleepHours < 7.0 ->
            CoachablePersona.RecoveryBottleneck
        dailySteps < 5_000.0 && sleepHours < 7.0 && deltaCha >= 1.0 ->
            CoachablePersona.MultiRiskChallenger
        dailySteps < 5_000.0 && sleepHours in 7.0..9.0 && deltaCha >= 1.0 ->
            CoachablePersona.ActivityBottleneck
        else ->
            CoachablePersona.BalancedMaintainer
    }

    /**
     * Fits a heavily ridge-regularized N=1 linear dynamical model for weekly
     * RHR transitions:
     *
     *     RHR_{t+1} - RHR_t = b0 + b1*(RHR_t - mu_rhr) + b2*z_steps + b3*z_sleep + b4*z_cardio
     *
     * Requires at least [MIN_SURROGATE_WEEKS] consecutive valid weeks.
     */
    internal fun fitPersonalSurrogate(weeks: List<WeeklyWearableVector>): PersonalRhrSurrogate? {
        val valid = weeks.filter {
            it.isValid &&
                it.meanRhrBpm != null &&
                it.meanDailySteps != null &&
                it.meanSleepHours != null
        }.sortedBy { it.weekStartEpochDay }

        if (valid.size < MIN_SURROGATE_WEEKS) return null

        // Collect consecutive week transitions (gap <= 14 days so long hiatuses don't skew dynamics).
        val pairs = mutableListOf<Pair<WeeklyWearableVector, WeeklyWearableVector>>()
        for (i in 0 until valid.lastIndex) {
            val gap = valid[i + 1].weekStartEpochDay - valid[i].weekStartEpochDay
            if (gap in 1..14) pairs += valid[i] to valid[i + 1]
        }
        if (pairs.size < MIN_SURROGATE_WEEKS - 1) return null

        val meanRhr = valid.map { it.meanRhrBpm!! }.average()
        val stepsList = valid.map { it.meanDailySteps!! }
        val sleepList = valid.map { it.meanSleepHours!! }
        val cardioList = valid.map { it.meanDailyCardioMin ?: 0.0 }

        val muSteps = stepsList.average()
        val sdSteps = max(stdDev(stepsList), STEPS_STD_FLOOR)
        val muSleep = sleepList.average()
        val sdSleep = max(stdDev(sleepList), SLEEP_MIN_STD_FLOOR / 60.0)
        val muCardio = cardioList.average()
        val sdCardio = max(stdDev(cardioList), CARDIO_MIN_STD_FLOOR)

        // Normal equations (X^T X + lambda * diag(0, 1, 1, 1, 1)) beta = X^T y
        val p = 5
        val xtx = Array(p) { DoubleArray(p) }
        val xty = DoubleArray(p)
        val ys = mutableListOf<Double>()

        for ((curr, next) in pairs) {
            val x = doubleArrayOf(
                1.0,
                curr.meanRhrBpm!! - meanRhr,
                (curr.meanDailySteps!! - muSteps) / sdSteps,
                (curr.meanSleepHours!! - muSleep) / sdSleep,
                ((curr.meanDailyCardioMin ?: 0.0) - muCardio) / sdCardio,
            )
            val y = next.meanRhrBpm!! - curr.meanRhrBpm
            ys += y
            for (r in 0 until p) {
                xty[r] += x[r] * y
                for (c in 0 until p) {
                    xtx[r][c] += x[r] * x[c]
                }
            }
        }

        // Strong ridge regularization to prevent N=1 overfitting.
        val lambda = 12.0
        for (i in 1 until p) xtx[i][i] += lambda
        xtx[0][0] += 1e-4

        val beta = solveLinearSystem(xtx, xty) ?: return null

        // Enforce damped mean reversion and mild step bounds for rollout stability.
        val intercept = beta[0].coerceIn(-0.4, 0.4)
        val revert = beta[1].coerceIn(-0.55, -0.05)
        val bSteps = beta[2].coerceIn(-0.6, 0.2)
        val bSleep = beta[3].coerceIn(-0.6, 0.3)
        val bCardio = beta[4].coerceIn(-0.8, 0.2)

        val model = PersonalRhrSurrogate(
            trainingWeeks = valid.size,
            meanRhr = meanRhr,
            muSteps = muSteps,
            sdSteps = sdSteps,
            muSleep = muSleep,
            sdSleep = sdSleep,
            muCardio = muCardio,
            sdCardio = sdCardio,
            intercept = intercept,
            revert = revert,
            bSteps = bSteps,
            bSleep = bSleep,
            bCardio = bCardio,
            rhrR2 = 0.0,
        )

        // Compute in-sample R^2 on next-week RHR level.
        val actualNext = pairs.map { it.second.meanRhrBpm!! }
        val meanNext = actualNext.average()
        var ssRes = 0.0
        var ssTot = 0.0
        for ((curr, next) in pairs) {
            val pred = model.stepRhr(
                currentRhr = curr.meanRhrBpm!!,
                steps = curr.meanDailySteps!!,
                sleepHours = curr.meanSleepHours!!,
                cardioMinPerDay = curr.meanDailyCardioMin ?: 0.0,
            )
            val err = next.meanRhrBpm!! - pred
            val dev = next.meanRhrBpm - meanNext
            ssRes += err * err
            ssTot += dev * dev
        }
        val r2 = if (ssTot > 1e-9) (1.0 - ssRes / ssTot).coerceIn(-1.0, 1.0) else 0.0
        return model.copy(rhrR2 = r2)
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = values.average()
        return sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
    }

    /** Gaussian elimination with partial pivoting for small dense systems. */
    private fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) {
                if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            }
            if (abs(m[pivot][col]) < 1e-10) return null
            if (pivot != col) {
                val tmp = m[col]
                m[col] = m[pivot]
                m[pivot] = tmp
            }
            val div = m[col][col]
            for (c in col..n) m[col][c] /= div
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col]
                for (c in col..n) m[r][c] -= factor * m[col][c]
            }
        }
        return DoubleArray(n) { r -> m[r][n] }
    }
}

data class BehaviorTarget(
    val dailySteps: Double,
    val sleepHours: Double,
    val cardioMinPerDay: Double,
)

data class ActionDistribution(
    val mean: Double,
    val std: Double,
    val min: Double,
    val max: Double,
    val peak: Double,
)

data class HabitualStats(
    val weeksObserved: Int,
    val steps: ActionDistribution,
    val sleepHours: ActionDistribution,
    val cardioMinPerDay: ActionDistribution,
    val weeklyAzm: ActionDistribution,
)

data class ProjectedWeek(
    val week: Int,
    val healthAge: Double,
    val rhrBpm: Double?,
    val weeklyAzm: Double?,
)

data class TrajectoryRollout(
    val endpointCha: Double,
    val endpointRhr: Double?,
    val endpointWeeklyAzm: Double?,
    val trajectory: List<ProjectedWeek>,
)

enum class PathwayKind(val label: String) {
    Sleep("Sleep focus"),
    Movement("Movement focus"),
    Combined("Combined focus"),
}

enum class CoachablePersona(val label: String, val note: String) {
    OptimalBaseline("Optimal Baseline", "High movement and solid sleep — focus on maintaining your trajectory."),
    RecoveryBottleneck("Recovery Bottleneck", "Already active, with sleep as the highest-leverage recovery dial."),
    MultiRiskChallenger("Multi-Risk Challenger", "Both sleep and movement have headroom; start with small, sustainable shifts."),
    ActivityBottleneck("Activity Bottleneck", "Sleep is on track — adding daily steps and cardio offers the largest payoff."),
    BalancedMaintainer("Balanced Maintainer", "Steady baseline with clear room for progressive habit building."),
}

data class CounterfactualOption(
    val kind: PathwayKind,
    val target: BehaviorTarget,
    val baseline: BehaviorTarget,
    val projectedCha: Double,
    val chaDeltaVsStatusQuo: Double,
    val projectedRhr: Double?,
    val projectedWeeklyAzm: Double?,
    val effort: Double,
    val score: Double,
    val withinHistoricalPeak: Boolean,
    val trajectory: List<ProjectedWeek>,
) {
    fun describeDeltas(): String {
        val parts = mutableListOf<String>()
        val dSteps = (target.dailySteps - baseline.dailySteps).roundToInt()
        if (abs(dSteps) >= 250) {
            parts += "${if (dSteps > 0) "+" else ""}$dSteps steps/day (to ${target.dailySteps.roundToInt()})"
        }
        val dSleepMin = ((target.sleepHours - baseline.sleepHours) * 60.0).roundToInt()
        if (abs(dSleepMin) >= 10) {
            parts += "${if (dSleepMin > 0) "+" else ""}$dSleepMin min sleep/night (to ${"%.1f".format(target.sleepHours)} h)"
        }
        val dCardio = (target.cardioMinPerDay - baseline.cardioMinPerDay).roundToInt()
        if (abs(dCardio) >= 2) {
            parts += "${if (dCardio > 0) "+" else ""}$dCardio cardio min/day (to ${target.cardioMinPerDay.roundToInt()} min)"
        }
        return if (parts.isEmpty()) "Maintain current habits" else parts.joinToString(", ")
    }
}

data class SurrogateDiagnostics(
    val requestedMode: SimulationMode,
    val effectiveMode: SimulationMode,
    val trainingWeeks: Int,
    val rhrR2: Double?,
    val note: String,
)

data class PersonalRhrSurrogate(
    val trainingWeeks: Int,
    val meanRhr: Double,
    val muSteps: Double,
    val sdSteps: Double,
    val muSleep: Double,
    val sdSleep: Double,
    val muCardio: Double,
    val sdCardio: Double,
    val intercept: Double,
    val revert: Double,
    val bSteps: Double,
    val bSleep: Double,
    val bCardio: Double,
    val rhrR2: Double,
) {
    fun stepRhr(
        currentRhr: Double,
        steps: Double,
        sleepHours: Double,
        cardioMinPerDay: Double,
    ): Double {
        val delta = intercept +
            revert * (currentRhr - meanRhr) +
            bSteps * ((steps - muSteps) / sdSteps).coerceIn(-3.0, 3.0) +
            bSleep * ((sleepHours - muSleep) / sdSleep).coerceIn(-3.0, 3.0) +
            bCardio * ((cardioMinPerDay - muCardio) / sdCardio).coerceIn(-3.0, 3.0)
        return currentRhr + delta.coerceIn(-1.5, 1.5)
    }
}

data class CustomSimulation(
    val target: BehaviorTarget,
    val rollout: TrajectoryRollout,
    val chaDeltaVsStatusQuo: Double,
    val chaDeltaVsBaseline: Double,
    val effort: Double,
    val score: Double,
)

data class CounterfactualPlan(
    val baseline: BehaviorTarget,
    val baselineCha: Double,
    val baselineRhr: Double?,
    val baselineWeeklyAzm: Double?,
    val habitual: HabitualStats,
    val statusQuo: TrajectoryRollout,
    val options: List<CounterfactualOption>,
    val persona: CoachablePersona,
    val diagnostics: SurrogateDiagnostics,
    val surrogate: PersonalRhrSurrogate?,
    val effectiveMode: SimulationMode,
)
