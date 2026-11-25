package com.srineere.airbridge.health

import android.content.Context

/**
 * Which counterfactual projection engine G-Age runs for Future A / Future B.
 *
 * - [Analytical]: Exact published CHA equations over volitional behaviors
 *   (steps, sleep, cardio/AZM), holding RHR at observed baseline. Zero risk of
 *   single-user overfitting.
 * - [PersonalSurrogate]: Experimental N=1 ridge-regularized linear transition
 *   model trained on your own Fitbit Air weekly transitions (>= 6 valid weeks)
 *   to approximate 12-week RHR and AZM drift, substituting for Google's
 *   unreleased 97k-user SensorWM weights.
 */
enum class SimulationMode(val label: String) {
    Analytical("Analytical CHA"),
    PersonalSurrogate("Personal N=1 (Lab)"),
}

/**
 * Stored model and profile options for the G-Age tab.
 *
 * Lives in `air_health` beside [FitnessProfileStore] so birth year and sex
 * stay shared across Fitness Age and G-Age.
 */
data class GAgePrefs(
    val collinearityAdjusted: Boolean = true,
    val includeVo2Max: Boolean = true,
    val includeSleepRegularity: Boolean = true,
    /** Optional lab HOMA-IR >= 2.9 status. Null omits the domain (w = 0). */
    val insulinResistant: Boolean? = null,
    val simulationMode: SimulationMode = SimulationMode.Analytical,
    /** Optional height in cm, used solely to compute BMI in the coach prompt. */
    val heightCm: Int? = null,
) {
    fun toChaConfig(): ChaConfig = ChaConfig(
        collinearityAdjusted = collinearityAdjusted,
        includeVo2Max = includeVo2Max,
        includeSleepRegularity = includeSleepRegularity,
    )
}

object GAgeSettings {
    private const val PREFS = "air_health"
    private const val KEY_COLLINEAR = "gage_collinear"
    private const val KEY_VO2 = "gage_vo2"
    private const val KEY_SLEEP_REG = "gage_sleep_reg"
    private const val KEY_IR = "gage_ir" // -1 = unset, 0 = false, 1 = true
    private const val KEY_MODE = "gage_sim_mode"
    private const val KEY_HEIGHT = "gage_height_cm"

    fun read(context: Context): GAgePrefs {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ir = when (p.getInt(KEY_IR, -1)) {
            0 -> false
            1 -> true
            else -> null
        }
        val mode = p.getString(KEY_MODE, null)?.let { name ->
            SimulationMode.entries.firstOrNull { it.name == name }
        } ?: SimulationMode.Analytical
        val height = p.getInt(KEY_HEIGHT, -1).takeIf { it in 100..250 }
        return GAgePrefs(
            collinearityAdjusted = p.getBoolean(KEY_COLLINEAR, true),
            includeVo2Max = p.getBoolean(KEY_VO2, true),
            includeSleepRegularity = p.getBoolean(KEY_SLEEP_REG, true),
            insulinResistant = ir,
            simulationMode = mode,
            heightCm = height,
        )
    }

    fun write(context: Context, prefs: GAgePrefs) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_COLLINEAR, prefs.collinearityAdjusted)
            .putBoolean(KEY_VO2, prefs.includeVo2Max)
            .putBoolean(KEY_SLEEP_REG, prefs.includeSleepRegularity)
            .putInt(
                KEY_IR,
                when (prefs.insulinResistant) {
                    null -> -1
                    false -> 0
                    true -> 1
                },
            )
            .putString(KEY_MODE, prefs.simulationMode.name)
            .putInt(KEY_HEIGHT, prefs.heightCm ?: -1)
            .apply()
    }
}
