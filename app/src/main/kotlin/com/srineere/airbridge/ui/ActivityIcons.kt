package com.srineere.airbridge.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.IceSkating
import androidx.compose.material.icons.outlined.Kayaking
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Paragliding
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Rowing
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.ScubaDiving
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Skateboarding
import androidx.compose.material.icons.outlined.Snowboarding
import androidx.compose.material.icons.outlined.Snowshoeing
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material.icons.outlined.SportsGolf
import androidx.compose.material.icons.outlined.SportsGymnastics
import androidx.compose.material.icons.outlined.SportsHandball
import androidx.compose.material.icons.outlined.SportsHockey
import androidx.compose.material.icons.outlined.SportsMartialArts
import androidx.compose.material.icons.outlined.SportsRugby
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.SportsVolleyball
import androidx.compose.material.icons.outlined.Stairs
import androidx.compose.material.icons.outlined.Surfing
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.health.connect.client.records.ExerciseSessionRecord as Ex
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.exerciseName

/**
 * What a workout looks like, from the type Health Connect filed it under.
 *
 * The rows used to carry the first letter of the workout's name, which
 * collapsed everything that started with the same one: on this phone two
 * entries read "R" and were racquet sports, while a run would have read
 * "R" too. A letter is only a label for a name you can already read on the
 * same line.
 *
 * Keyed on [Session.exerciseType] rather than on that name, which matters
 * more than it looks: `exerciseName` prefers the record's own title, so a
 * writer that titles a session "Morning loop" gives a W, an M or nothing
 * useful at all while the type underneath still says walking.
 *
 * Every exercise type this version of Health Connect defines has a branch
 * here, taken from the constants in connect-client rather than from
 * memory. Where a family shares one glyph it is because Material draws one
 * for the family and nothing for its members -- not because the rest were
 * missed. Anything a later version adds falls to the generic, which is the
 * right answer for a type this build cannot name.
 */
fun Session.exerciseIcon(): ImageVector =
    iconForType(exerciseType) ?: iconForName(exerciseName()) ?: Icons.Outlined.FitnessCenter

/**
 * The glyph for a type Health Connect names, or null when it does not.
 *
 * Null rather than a default, so the caller can go and ask the name. The
 * difference matters: OTHER_WORKOUT is not "a workout with no glyph", it
 * is "the writer had no constant for this", and the writer usually put
 * what it meant in the title instead.
 */
private fun iconForType(exerciseType: Int): ImageVector? = when (exerciseType) {
    Ex.EXERCISE_TYPE_WALKING -> Icons.Outlined.DirectionsWalk
    Ex.EXERCISE_TYPE_RUNNING, Ex.EXERCISE_TYPE_RUNNING_TREADMILL -> Icons.Outlined.DirectionsRun
    Ex.EXERCISE_TYPE_BIKING, Ex.EXERCISE_TYPE_BIKING_STATIONARY -> Icons.Outlined.DirectionsBike
    Ex.EXERCISE_TYPE_HIKING -> Icons.Outlined.Hiking

    Ex.EXERCISE_TYPE_SWIMMING_POOL,
    Ex.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    Ex.EXERCISE_TYPE_WATER_POLO,
    -> Icons.Outlined.Pool

    // Racquet sports share a glyph on purpose. Material draws a tennis
    // racquet and nothing else in the family, and a badminton session
    // under a basketball would be worse than one under a racquet.
    Ex.EXERCISE_TYPE_TENNIS,
    Ex.EXERCISE_TYPE_BADMINTON,
    Ex.EXERCISE_TYPE_SQUASH,
    Ex.EXERCISE_TYPE_RACQUETBALL,
    Ex.EXERCISE_TYPE_TABLE_TENNIS,
    -> Icons.Outlined.SportsTennis

    Ex.EXERCISE_TYPE_STRENGTH_TRAINING,
    Ex.EXERCISE_TYPE_WEIGHTLIFTING,
    Ex.EXERCISE_TYPE_ELLIPTICAL,
    Ex.EXERCISE_TYPE_ROWING_MACHINE,
    -> Icons.Outlined.FitnessCenter

    Ex.EXERCISE_TYPE_YOGA,
    Ex.EXERCISE_TYPE_PILATES,
    Ex.EXERCISE_TYPE_STRETCHING,
    Ex.EXERCISE_TYPE_GUIDED_BREATHING,
    -> Icons.Outlined.SelfImprovement

    Ex.EXERCISE_TYPE_CALISTHENICS,
    Ex.EXERCISE_TYPE_GYMNASTICS,
    Ex.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
    Ex.EXERCISE_TYPE_BOOT_CAMP,
    Ex.EXERCISE_TYPE_EXERCISE_CLASS,
    -> Icons.Outlined.SportsGymnastics

    Ex.EXERCISE_TYPE_MARTIAL_ARTS,
    Ex.EXERCISE_TYPE_BOXING,
    Ex.EXERCISE_TYPE_FENCING,
    -> Icons.Outlined.SportsMartialArts

    Ex.EXERCISE_TYPE_BASKETBALL -> Icons.Outlined.SportsBasketball
    Ex.EXERCISE_TYPE_SOCCER -> Icons.Outlined.SportsSoccer
    Ex.EXERCISE_TYPE_BASEBALL, Ex.EXERCISE_TYPE_SOFTBALL -> Icons.Outlined.SportsBaseball
    Ex.EXERCISE_TYPE_CRICKET -> Icons.Outlined.SportsCricket
    Ex.EXERCISE_TYPE_RUGBY -> Icons.Outlined.SportsRugby
    Ex.EXERCISE_TYPE_VOLLEYBALL -> Icons.Outlined.SportsVolleyball
    Ex.EXERCISE_TYPE_HANDBALL, Ex.EXERCISE_TYPE_FRISBEE_DISC -> Icons.Outlined.SportsHandball
    Ex.EXERCISE_TYPE_GOLF -> Icons.Outlined.SportsGolf

    Ex.EXERCISE_TYPE_FOOTBALL_AMERICAN,
    Ex.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
    -> Icons.Outlined.SportsFootball

    Ex.EXERCISE_TYPE_ICE_HOCKEY, Ex.EXERCISE_TYPE_ROLLER_HOCKEY -> Icons.Outlined.SportsHockey

    Ex.EXERCISE_TYPE_ROWING -> Icons.Outlined.Rowing
    Ex.EXERCISE_TYPE_PADDLING -> Icons.Outlined.Kayaking
    Ex.EXERCISE_TYPE_SAILING -> Icons.Outlined.Sailing
    Ex.EXERCISE_TYPE_SURFING -> Icons.Outlined.Surfing
    Ex.EXERCISE_TYPE_SCUBA_DIVING -> Icons.Outlined.ScubaDiving
    Ex.EXERCISE_TYPE_PARAGLIDING -> Icons.Outlined.Paragliding

    Ex.EXERCISE_TYPE_SKIING -> Icons.Outlined.DownhillSkiing
    Ex.EXERCISE_TYPE_SNOWBOARDING -> Icons.Outlined.Snowboarding
    Ex.EXERCISE_TYPE_SNOWSHOEING -> Icons.Outlined.Snowshoeing
    Ex.EXERCISE_TYPE_ICE_SKATING -> Icons.Outlined.IceSkating
    Ex.EXERCISE_TYPE_SKATING -> Icons.Outlined.Skateboarding

    Ex.EXERCISE_TYPE_STAIR_CLIMBING,
    Ex.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
    -> Icons.Outlined.Stairs

    Ex.EXERCISE_TYPE_ROCK_CLIMBING -> Icons.Outlined.Terrain
    Ex.EXERCISE_TYPE_DANCING -> Icons.Outlined.MusicNote
    Ex.EXERCISE_TYPE_WHEELCHAIR -> Icons.Outlined.Accessible

    // OTHER_WORKOUT, and anything a later Health Connect adds. Both mean
    // the same thing here: nothing this build can name, so go and read
    // what it is called.
    else -> null
}

/**
 * The glyph for what a workout calls itself.
 *
 * Needed because Google Health logs most of its catalogue as
 * OTHER_WORKOUT. Health Connect has constants for tennis, squash,
 * badminton, racquetball and table tennis and none for "racquet sports",
 * which is the entry Google Health actually offers -- so a session that is
 * unmistakably racquet sports arrived with no type and came out as a
 * dumbbell. The same is true of circuit training, CrossFit, core
 * training, aerobics and most of that list.
 *
 * The name is only consulted when the type said nothing, so a record that
 * does carry a type is never second-guessed by its own title. A session
 * typed as running and titled "leg day" stays a run.
 *
 * Substring matching, in order, because these names come from other apps
 * and nobody is going to agree on them. First match wins, so order is
 * load-bearing: "racquet" sits above nothing that could swallow it, and
 * "walk" sits above "treadmill" so a treadmill walk stays a walk.
 */
private fun iconForName(name: String): ImageVector? {
    val said = name.lowercase()
    return NAMED.firstOrNull { (word, _) -> word in said }?.second
}

/**
 * Words that give a workout away, most specific first.
 *
 * Taken from the list Google Health offers under "All exercises", which is
 * where most of these records are going to come from.
 */
private val NAMED: List<Pair<String, ImageVector>> = listOf(
    "racquet" to Icons.Outlined.SportsTennis,
    "racket" to Icons.Outlined.SportsTennis,
    "tennis" to Icons.Outlined.SportsTennis,
    "badminton" to Icons.Outlined.SportsTennis,
    "squash" to Icons.Outlined.SportsTennis,
    "pickleball" to Icons.Outlined.SportsTennis,
    "padel" to Icons.Outlined.SportsTennis,

    "jog" to Icons.Outlined.DirectionsRun,
    "run" to Icons.Outlined.DirectionsRun,
    "walk" to Icons.Outlined.DirectionsWalk,
    // After both, so "treadmill walking" is a walk and a bare "treadmill"
    // still runs.
    "treadmill" to Icons.Outlined.DirectionsRun,
    "hike" to Icons.Outlined.Hiking,
    "trek" to Icons.Outlined.Hiking,

    "spin" to Icons.Outlined.DirectionsBike,
    "cycl" to Icons.Outlined.DirectionsBike,
    "bike" to Icons.Outlined.DirectionsBike,
    "biking" to Icons.Outlined.DirectionsBike,

    "swim" to Icons.Outlined.Pool,
    "water polo" to Icons.Outlined.Pool,
    "snorkel" to Icons.Outlined.ScubaDiving,
    "dive" to Icons.Outlined.ScubaDiving,
    "surf" to Icons.Outlined.Surfing,
    "sail" to Icons.Outlined.Sailing,
    "kayak" to Icons.Outlined.Kayaking,
    "canoe" to Icons.Outlined.Kayaking,
    "paddle" to Icons.Outlined.Kayaking,
    "row" to Icons.Outlined.Rowing,

    "yoga" to Icons.Outlined.SelfImprovement,
    "pilates" to Icons.Outlined.SelfImprovement,
    "stretch" to Icons.Outlined.SelfImprovement,
    "meditat" to Icons.Outlined.SelfImprovement,
    "breath" to Icons.Outlined.SelfImprovement,

    "crossfit" to Icons.Outlined.SportsGymnastics,
    "circuit" to Icons.Outlined.SportsGymnastics,
    "bootcamp" to Icons.Outlined.SportsGymnastics,
    "boot camp" to Icons.Outlined.SportsGymnastics,
    "hiit" to Icons.Outlined.SportsGymnastics,
    "interval" to Icons.Outlined.SportsGymnastics,
    "aerobic" to Icons.Outlined.SportsGymnastics,
    "calisthenic" to Icons.Outlined.SportsGymnastics,
    "gymnastic" to Icons.Outlined.SportsGymnastics,
    "core" to Icons.Outlined.SportsGymnastics,

    "weight" to Icons.Outlined.FitnessCenter,
    "strength" to Icons.Outlined.FitnessCenter,
    "elliptical" to Icons.Outlined.FitnessCenter,
    "stair" to Icons.Outlined.Stairs,

    "box" to Icons.Outlined.SportsMartialArts,
    "martial" to Icons.Outlined.SportsMartialArts,
    "karate" to Icons.Outlined.SportsMartialArts,
    "fenc" to Icons.Outlined.SportsMartialArts,

    "basketball" to Icons.Outlined.SportsBasketball,
    "football" to Icons.Outlined.SportsFootball,
    "soccer" to Icons.Outlined.SportsSoccer,
    "cricket" to Icons.Outlined.SportsCricket,
    "baseball" to Icons.Outlined.SportsBaseball,
    "softball" to Icons.Outlined.SportsBaseball,
    "rugby" to Icons.Outlined.SportsRugby,
    "volleyball" to Icons.Outlined.SportsVolleyball,
    "handball" to Icons.Outlined.SportsHandball,
    "frisbee" to Icons.Outlined.SportsHandball,
    "hockey" to Icons.Outlined.SportsHockey,
    "golf" to Icons.Outlined.SportsGolf,

    "ski" to Icons.Outlined.DownhillSkiing,
    "snowboard" to Icons.Outlined.Snowboarding,
    "snowshoe" to Icons.Outlined.Snowshoeing,
    "skating" to Icons.Outlined.IceSkating,
    "skateboard" to Icons.Outlined.Skateboarding,

    "climb" to Icons.Outlined.Terrain,
    "danc" to Icons.Outlined.MusicNote,
    "wheelchair" to Icons.Outlined.Accessible,
    "paraglid" to Icons.Outlined.Paragliding,
)
