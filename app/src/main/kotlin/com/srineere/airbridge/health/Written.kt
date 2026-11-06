package com.srineere.airbridge.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import kotlin.reflect.KClass

/**
 * The readings worth asking "who wrote this?" about.
 *
 * Not every kind of reading. Only the ones where two writers produce a
 * figure that is wrong rather than a figure that is merely from two
 * places, which is the distinction that decides whether filtering helps
 * or harms:
 *
 *  - **Additive.** Steps, distance, calories and food are summed. A phone
 *    and a band both counting the same walk make one walk into two, and
 *    the goal buzzes early. This is the fault the step picker was built
 *    for, and the other three have it for the same reason.
 *  - **Duplicating.** Sleep and workouts are sessions. Two writers do not
 *    inflate a total; they put the same night, or the same run, on the
 *    screen twice.
 *
 * Everything else is deliberately absent, and the absence is the point.
 * Heart rate, HRV, resting rate, breathing, skin temperature, VO2 max and
 * weight are point measurements: a second source there is more readings,
 * not a wrong number. Filtering them would only ever remove data, and in
 * the case of weight it would be somebody's scale -- an app that writes
 * weight has often never written a step, so a filter built from step
 * writers would drop it entirely.
 *
 * That is also why each of these carries its own [metric]: the writers
 * are enumerated per reading, never borrowed from another.
 */
enum class Written(
    val key: String,
    val type: KClass<out Record>,
    val metric: AggregateMetric<*>,
    /** What the picker calls this, when it has to say. */
    val label: String,
) {
    Steps("steps", StepsRecord::class, StepsRecord.COUNT_TOTAL, "Steps"),
    Distance("distance", DistanceRecord::class, DistanceRecord.DISTANCE_TOTAL, "Distance"),
    Burned(
        "calories",
        TotalCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
        "Calories burned",
    ),
    Eaten("food", NutritionRecord::class, NutritionRecord.ENERGY_TOTAL, "Food"),
    Sleep(
        "sleep",
        SleepSessionRecord::class,
        SleepSessionRecord.SLEEP_DURATION_TOTAL,
        "Sleep",
    ),
    Workouts(
        "workouts",
        ExerciseSessionRecord::class,
        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
        "Workouts",
    ),
}
