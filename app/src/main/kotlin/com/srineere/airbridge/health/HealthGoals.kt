package com.srineere.airbridge.health

import android.content.Context

/**
 * The targets the Health tab draws its goal lines from.
 *
 * **Health Connect has no goals.** It carries measurements and nothing else:
 * there is no daily step target, no sleep target and no calorie target in the
 * API, and the figures you set inside Google Health stay inside Google Health.
 * The only "goal" types it defines belong to `PlannedExerciseSessionRecord` —
 * a structured workout plan, which is a different thing entirely. So these
 * are kept here, and the Goals screen says where each default came from.
 *
 * Every default below is a published population figure, not a guess, and
 * every one of them is wrong for somebody. That is why they are editable and
 * why the screen names its source rather than presenting a number as fact.
 */
data class Goals(
    /** Steps a day. The familiar 10,000 is a 1960s pedometer slogan, not a finding. */
    val steps: Int = DEFAULT_STEPS,
    /** Minutes asleep a night. The low end of the 7–9 hour adult range. */
    val sleepMinutes: Int = DEFAULT_SLEEP_MINUTES,
    /**
     * Kilocalories a day against level, signed: negative is a deficit.
     * Zero means maintain, which is what most people actually want.
     */
    val energyKcal: Int = 0,
    /** Grams of protein a day. Null until there is a weight to derive it from. */
    val proteinGrams: Int? = null,
    /** Minutes of exercise a week. The WHO's lower bound for adults. */
    val activeMinutes: Int = DEFAULT_ACTIVE_MINUTES,
) {
    /**
     * Roughly how much weight a daily energy gap moves in a fortnight.
     *
     * 7,700 kcal to a kilogram of body fat is the standard rule of thumb and
     * it overstates real loss, because the body spends less as it shrinks.
     * It is here as a sanity check on the food log, not as a prediction.
     */
    val fortnightKg: Double get() = energyKcal * 14 / KCAL_PER_KG

    companion object {
        const val DEFAULT_STEPS = 10_000
        const val DEFAULT_SLEEP_MINUTES = 450
        const val DEFAULT_ACTIVE_MINUTES = 150
        const val KCAL_PER_KG = 7700.0

        /** 1.6 g per kilogram: the top of the range where added protein still helps. */
        fun proteinFor(weightKg: Double): Int = Math.round(weightKg * 1.6).toInt()
    }
}

/** Reads and writes [Goals], alongside the buzz settings in `air_health.xml`. */
object HealthGoals {
    private const val PREFS = "air_health"

    // The step goal shares its key with the buzz settings on purpose: the
    // band buzzes at the goal, and two keys would be two goals waiting to
    // disagree about which number the wrist is waiting for.
    private const val KEY_STEPS = "step_goal"
    private const val KEY_SLEEP = "sleep_goal_minutes"
    private const val KEY_ENERGY = "energy_goal_kcal"
    private const val KEY_PROTEIN = "protein_goal_g"
    private const val KEY_ACTIVE = "active_goal_minutes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): Goals {
        val p = prefs(context)
        val fallback = Goals()
        return Goals(
            steps = p.getInt(KEY_STEPS, fallback.steps),
            sleepMinutes = p.getInt(KEY_SLEEP, fallback.sleepMinutes),
            energyKcal = p.getInt(KEY_ENERGY, fallback.energyKcal),
            proteinGrams = p.getInt(KEY_PROTEIN, -1).takeIf { it > 0 },
            activeMinutes = p.getInt(KEY_ACTIVE, fallback.activeMinutes),
        )
    }

    fun write(context: Context, goals: Goals) {
        prefs(context).edit()
            .putInt(KEY_STEPS, goals.steps)
            .putInt(KEY_SLEEP, goals.sleepMinutes)
            .putInt(KEY_ENERGY, goals.energyKcal)
            .putInt(KEY_PROTEIN, goals.proteinGrams ?: -1)
            .putInt(KEY_ACTIVE, goals.activeMinutes)
            .apply()
    }
}
