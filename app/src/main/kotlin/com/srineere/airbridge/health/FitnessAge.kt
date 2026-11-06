package com.srineere.airbridge.health

import android.content.Context
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Who the reference table is being read for.
 *
 * Kept because a VO₂ max figure means nothing on its own: 44 is strong for
 * a woman of fifty and ordinary for a man of twenty-five, and without both
 * of these there is no row in the table to look up. Stored on this phone
 * and used for nothing else.
 */
data class FitnessProfile(
    val birthYear: Int? = null,
    val sex: Sex? = null,
) {
    val age: Int? get() = birthYear?.let { LocalDate.now().year - it }
    val complete: Boolean get() = age != null && sex != null
}

enum class Sex(val label: String) { Female("Female"), Male("Male") }

/**
 * An estimate of fitness, expressed in years.
 *
 * Called fitness age, never biological age. The published models behind it
 * estimate cardiorespiratory fitness and nothing else — they do not read
 * your cells — and a name that claims otherwise is the claim, not the
 * number. It is not a measure of health, lifespan or ageing.
 *
 * It is also the one figure on this tab derived from a **population**
 * band rather than from your own readings, which is why it says so on its
 * own screen rather than in a footnote somewhere else.
 */
data class FitnessAge(
    val years: Int?,
    val vo2: Double?,
    val readAt: Long?,
    val profile: FitnessProfile,
    /** Readings this app has, for the line under the figure. */
    val points: Int,
) {
    val age: Int? get() = profile.age

    /** Years under your own, negative when the estimate is older. */
    val under: Int? get() = age?.let { own -> years?.let { own - it } }

    val word: String
        get() {
            val gap = under ?: return "no figure yet"
            return when {
                gap > 1 -> "$gap years under"
                gap < -1 -> "${-gap} years over"
                else -> "about your own age"
            }
        }
}

/**
 * The fifty-percent VO₂ max for an age, in millilitres per minute per
 * kilogram.
 *
 * A straight line through the published norms rather than a lookup table
 * with a step at every decade: a birthday is not a cliff, and a table that
 * moves someone's fitness age by two years overnight is a table arguing
 * with itself. Anchored at twenty-five, where the norms are flattest.
 *
 * These are the Cooper Institute's own middle values, which are the ones
 * most of the consumer trackers use. They are a population, and a
 * population is exactly what the rest of this app refuses to compare you
 * to — so this one figure says so out loud wherever it appears.
 */
internal fun referenceVo2(age: Double, sex: Sex): Double = when (sex) {
    Sex.Male -> 45.0 - 0.24 * (age - 25)
    Sex.Female -> 38.0 - 0.22 * (age - 25)
}

private fun slope(sex: Sex): Double = if (sex == Sex.Male) 0.24 else 0.22

/**
 * The age at which your VO₂ max would be the middle of the table.
 *
 * One measurement expressed in years, because years are easier to feel
 * than millilitres per kilogram. Resting heart rate, active minutes and
 * body composition are not in it: the published model is a VO₂ max model,
 * and weights of this app's own invention on top of it would make the
 * figure less defensible rather than more complete.
 */
fun fitnessAgeOf(snapshot: HealthSnapshot, profile: FitnessProfile): FitnessAge {
    val newest = snapshot.vo2Max.maxByOrNull { it.day }
    val vo2 = newest?.value
    val sex = profile.sex
    val years = if (vo2 == null || sex == null) null else {
        // Bounded, because the line has to stop somewhere and a fitness age
        // of nine is not a compliment.
        (25.0 + (referenceVo2(25.0, sex) - vo2) / slope(sex)).roundToInt().coerceIn(18, 85)
    }
    return FitnessAge(
        years = years,
        vo2 = vo2,
        readAt = newest?.day,
        profile = profile,
        points = snapshot.vo2Max.size,
    )
}

/** The profile, in the same preferences as everything else this tab knows. */
object FitnessProfileStore {
    private const val PREFS = "air_health"
    private const val KEY_BORN = "born_year"
    private const val KEY_SEX = "sex"

    fun read(context: Context): FitnessProfile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val born = p.getInt(KEY_BORN, -1).takeIf { it > 1900 }
        val sex = p.getString(KEY_SEX, null)?.let { name ->
            Sex.entries.firstOrNull { it.name == name }
        }
        return FitnessProfile(born, sex)
    }

    fun write(context: Context, profile: FitnessProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_BORN, profile.birthYear ?: -1)
            .putString(KEY_SEX, profile.sex?.name)
            .apply()
    }
}
