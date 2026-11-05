package com.srineere.airbridge.widget

import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.asHoursMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The pale family.
 *
 * Big figures on a soft card, one accent apiece, and a sentence where the
 * other families put a label. It is the only one here that does not follow
 * the phone's light-or-dark: a pale card is its premise, and drawn dark at
 * night it would just be the other families with rounder corners.
 *
 * Two things about it are worth knowing before changing it.
 *
 * **Every colour comes from the light half of the palette**, through
 * [WidgetColours.paperTile] and never [WidgetColours.tile]. That is not
 * tidiness: the mint that carries steps on a dark tile is 1.81:1 on white,
 * which is not dim, it is gone.
 *
 * **The tracking is not here and cannot be.** What makes this look work on
 * the design sheets is figures set at -0.03 em, and Glance's `TextStyle`
 * has no letter spacing at all -- size, weight, colour, alignment,
 * decoration, family, and nothing for tracking. The only way to get it
 * would be to draw each numeral to a bitmap, which costs a bitmap per
 * figure per resize and freezes the numeral against the system text size.
 * So these are the same shapes at default tracking, and that is most of the
 * difference between this family and the sheets.
 */
enum class PaperFace(
    val title: String,
    val summary: String,
    val sizes: Set<WidgetSize>,
) {
    Figure(
        "One figure",
        "Steps on a pale card, and nothing else",
        setOf(WidgetSize.Strip, WidgetSize.Square, WidgetSize.Wide),
    ),
    Sentence(
        "The sentence",
        "Last night, and a line saying what it means",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
    Rail(
        "The rail",
        "Four readings behind hairlines, no colour at all",
        setOf(WidgetSize.Row, WidgetSize.Wide),
    ),
    Day(
        "The day",
        "Four soft panes inside one widget",
        setOf(WidgetSize.Tall),
    ),
}

/** One pane of the Day face, and one column of the Rail. */
data class PaperReading(
    val label: String,
    val figure: String,
    val detail: String,
    val colour: TileColour,
)

/**
 * The four readings this family shows, in the order it shows them.
 *
 * Fewer than four when fewer than four are known. A pane saying nothing is
 * worse than a widget with three panes, and a dash in a family this quiet
 * reads as a fault rather than as an absence.
 */
internal fun paperReadings(health: HealthData, goals: Goals): List<PaperReading> =
    listOfNotNull(
        health.lastNight?.let {
            PaperReading(
                "Sleep",
                it.asleep.asHoursMinutes(),
                health.sleepScore?.total?.let { score -> "score $score" } ?: "last night",
                TileColour.Sleep,
            )
        },
        health.snapshot.stepsToday?.let {
            PaperReading(
                "Steps",
                "%,d".format(it),
                if (goals.steps > 0) "of %,d".format(goals.steps) else "today",
                TileColour.Steps,
            )
        },
        health.restingTrend?.let {
            PaperReading("Resting", "${it.latest.roundToInt()}", "bpm", TileColour.Heart)
        },
        health.snapshot.burnedToday?.let {
            PaperReading("Burn", "%,d".format(it.roundToInt()), "kcal", TileColour.Energy)
        },
    )

/**
 * One sentence about today, in the order a person would notice things.
 *
 * The rule is deliberately small and in one place, because a sentence is
 * the easiest thing in this app to make dishonest. It says only what the
 * readings already support:
 *
 * 1. A night outside the hours you usually sleep is the most notable thing
 *    about a morning, so it goes first, with the size of the difference.
 * 2. Otherwise a goal already met is worth saying, since nothing else will.
 * 3. Otherwise how far there is to go.
 * 4. Otherwise: nothing is unusual, and saying so is better than saying
 *    nothing. "Everything is where it usually is" is a real statement about
 *    a day and the one this app is best placed to make.
 *
 * Never a cause, never a suggestion, and never a comparison with anybody
 * else.
 */
internal fun paperSentence(health: HealthData, goals: Goals): String {
    val night = health.lastNight
    val usual = health.sleepTrend?.usual
    if (night != null && usual != null) {
        val minutes = night.asleep.toMinutes().toDouble()
        if (minutes > usual.high) {
            return "You slept ${spellMinutes(minutes - usual.high)} longer than your usual night."
        }
        if (minutes < usual.low) {
            return "You slept ${spellMinutes(usual.low - minutes)} less than your usual night."
        }
    }

    val steps = health.stepsToday
    if (goals.steps > 0 && health.snapshot.stepsToday != null) {
        val toGo = health.stepsToGo(goals.steps)
        if (toGo == 0L) return "You passed your step goal, with %,d.".format(steps)
        return "%,d steps to go.".format(toGo)
    }

    return "Everything is where it usually is."
}

/** "42 minutes", "an hour", "1h 20m" -- however a person would say it. */
private fun spellMinutes(value: Double): String {
    val minutes = abs(value).roundToInt()
    return when {
        minutes < 60 -> "$minutes minutes"
        minutes < 75 -> "an hour"
        minutes % 60 == 0 -> "${minutes / 60} hours"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** How far round the ring beside the sentence has gone. */
internal fun paperRing(health: HealthData, goals: Goals): Pair<Float, TileColour>? {
    val night = health.lastNight ?: return null
    if (goals.sleepMinutes <= 0) return null
    val fraction = (night.asleep.toMinutes().toFloat() / goals.sleepMinutes).coerceIn(0f, 1f)
    return fraction to TileColour.Sleep
}
