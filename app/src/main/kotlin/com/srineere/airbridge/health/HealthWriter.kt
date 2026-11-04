package com.srineere.airbridge.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneId

/** Which meal an entry belongs to, in Health Connect's own terms. */
enum class Meal(val label: String, val type: Int) {
    Breakfast("Breakfast", MealType.MEAL_TYPE_BREAKFAST),
    Lunch("Lunch", MealType.MEAL_TYPE_LUNCH),
    Dinner("Dinner", MealType.MEAL_TYPE_DINNER),
    Snack("Snack", MealType.MEAL_TYPE_SNACK),
    ;

    companion object {
        /**
         * The meal this hour usually is, so the common case needs no tap.
         * A guess the person can override, not a rule.
         */
        fun at(hour: Int): Meal = when (hour) {
            in 4..10 -> Breakfast
            in 11..15 -> Lunch
            in 16..21 -> Dinner
            else -> Snack
        }
    }
}

/** What a food entry failed on, in words a screen can show. */
sealed interface LogResult {
    data object Saved : LogResult
    data class Refused(val why: String) : LogResult
}

/**
 * The only thing Air Bridge writes.
 *
 * Everything else on the Health tab is read, and that asymmetry is the point:
 * a relay that quietly wrote to your health record would be a different kind
 * of app. So the write is one record type, one call, and it goes in exactly
 * as typed — no estimating a calorie figure from macros, no filling a blank
 * field with an average.
 */
object HealthWriter {

    suspend fun logFood(
        context: Context,
        name: String,
        kcal: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        meal: Meal,
        at: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): LogResult {
        if (kcal == null && protein == null && carbs == null && fat == null) {
            return LogResult.Refused("Nothing to save: an entry needs at least a figure.")
        }
        val client = HealthConnect.clientOrNull(context)
            ?: return LogResult.Refused("Health Connect is not available on this phone.")
        val granted = HealthConnect.granted(client)
        if (HealthPermission.getWritePermission(NutritionRecord::class) !in granted) {
            return LogResult.Refused(
                "Air Bridge is not allowed to write food yet. Allow it in Health Connect.",
            )
        }
        val (start, end) = span(at, zone)
        val offset = zone.rules.getOffset(start)
        // Built inside the catch. A record constructor that rejects its own
        // arguments is a refusal like any other, and letting one reach the
        // top of the main thread took the whole app down the first time
        // anybody pressed Save.
        val record = runCatching {
            NutritionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                energy = kcal?.let { Energy.kilocalories(it) },
                protein = protein?.let { Mass.grams(it) },
                totalCarbohydrate = carbs?.let { Mass.grams(it) },
                totalFat = fat?.let { Mass.grams(it) },
                mealType = meal.type,
                name = name.trim().takeIf { it.isNotEmpty() },
                // Typed here, by hand, and the record says so: a reader can tell
                // this apart from something a scale or a band measured.
                metadata = Metadata.manualEntry(),
            )
        }.getOrElse { error ->
            Log.w("AirBridge", "Could not build food entry", error)
            return LogResult.Refused(
                "Health Connect would not take the entry: ${error.message ?: "no reason given"}.",
            )
        }
        return runCatching { client.insertRecords(listOf(record)) }
            .fold(
                onSuccess = { LogResult.Saved },
                onFailure = { error ->
                    Log.w("AirBridge", "Could not write food entry", error)
                    LogResult.Refused(
                        "Health Connect refused the entry: ${error.message ?: "no reason given"}.",
                    )
                },
            )
    }

    /**
     * Takes an entry back out.
     *
     * Counted as writing by Health Connect, so it needs no permission the
     * save did not already need. It deletes whatever wrote the record, not
     * only our own: the log shows the day as Health Connect holds it, and a
     * list you can see but not correct is worse than no list.
     */
    suspend fun deleteFood(context: Context, id: String): LogResult {
        val client = HealthConnect.clientOrNull(context)
            ?: return LogResult.Refused("Health Connect is not available on this phone.")
        val granted = HealthConnect.granted(client)
        if (HealthPermission.getWritePermission(NutritionRecord::class) !in granted) {
            return LogResult.Refused(
                "Air Bridge is not allowed to change food entries. Allow it in Health Connect.",
            )
        }
        return runCatching {
            client.deleteRecords(NutritionRecord::class, listOf(id), emptyList())
        }.fold(
            onSuccess = { LogResult.Saved },
            onFailure = { error ->
                Log.w("AirBridge", "Could not delete food entry", error)
                LogResult.Refused(
                    "Health Connect would not delete it: ${error.message ?: "no reason given"}.",
                )
            },
        )
    }

    /**
     * The second a meal occupies.
     *
     * Health Connect requires a nutrition record to start strictly before it
     * ends, so a thing eaten cannot be an instant however much it would like
     * to be -- passing the same time twice threw before the insert was even
     * attempted. One second is the smallest span that satisfies it and the
     * least invented duration available.
     *
     * The second never crosses midnight. A meal at 23:59:59 would otherwise
     * end on the following day and be counted in two days' aggregates, so
     * such a meal is logged as the last second of its own day instead.
     */
    internal fun span(at: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val end = at.plusSeconds(1)
        val sameDay = at.atZone(zone).toLocalDate() == end.atZone(zone).toLocalDate()
        return if (sameDay) at to end else at.minusSeconds(1) to at
    }

    /**
     * Whether the macros typed agree with the calories typed.
     *
     * Four, four and nine per gram — the Atwater factors every label uses.
     * When the two disagree the screen says so and keeps both: the person
     * read them off a packet, and silently replacing one with the other
     * would be the app deciding which of their numbers was the lie.
     */
    fun macroEnergy(protein: Double?, carbs: Double?, fat: Double?): Double? {
        if (protein == null && carbs == null && fat == null) return null
        return (protein ?: 0.0) * 4 + (carbs ?: 0.0) * 4 + (fat ?: 0.0) * 9
    }

    /** True when the two figures are close enough not to be worth mentioning. */
    fun agrees(kcal: Double?, fromMacros: Double?): Boolean {
        if (kcal == null || fromMacros == null) return true
        val gap = kotlin.math.abs(kcal - fromMacros)
        return gap <= kcal * TOLERANCE || gap <= 25
    }

    /** A tenth out is rounding on a packet; a third out is a different meal. */
    private const val TOLERANCE = 0.1
}
