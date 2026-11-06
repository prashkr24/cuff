package com.srineere.airbridge.health

import androidx.health.connect.client.records.ExerciseSessionRecord as Ex

/**
 * What to call a workout.
 *
 * Lived on the activity screen until the exporter needed it: a CSV column
 * reading "76" is a column nobody can use, and naming a type is a fact
 * about the data rather than about any one screen.
 *
 * The record's own title wins where it has one, because a writer that
 * bothered to name a session knows more about it than a lookup table
 * does -- which is also why the icons are keyed on the type instead.
 */
fun Session.exerciseName(): String = title?.takeIf { it.isNotBlank() } ?: when (exerciseType) {
    Ex.EXERCISE_TYPE_WALKING -> "Walk"
    Ex.EXERCISE_TYPE_RUNNING,
    Ex.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> "Run"
    Ex.EXERCISE_TYPE_BIKING,
    Ex.EXERCISE_TYPE_BIKING_STATIONARY,
    -> "Cycle"
    Ex.EXERCISE_TYPE_HIKING -> "Hike"
    Ex.EXERCISE_TYPE_SWIMMING_POOL,
    Ex.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> "Swim"
    Ex.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"
    Ex.EXERCISE_TYPE_YOGA -> "Yoga"
    else -> "Workout"
}
