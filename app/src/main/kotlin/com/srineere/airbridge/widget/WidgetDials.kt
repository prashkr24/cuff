package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.sleepScoreOf
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.health.Goals
import kotlin.math.roundToInt

/**
 * The Dials family.
 *
 * Whoop's grammar rather than Whoop's design: a dark card, one thick arc,
 * and a numeral large enough to read across a room with its label shrunk to
 * a whisper underneath. Where Rings stacks three arcs inside each other and
 * never names a number, a dial is one reading with its figure in the middle
 * of it.
 *
 * **Only a reading with a real ceiling gets one.** A dial says "this far out
 * of all the way", so it needs an all-the-way that somebody actually chose
 * or that this app actually computes. Sleep has a score out of a hundred
 * that this app works out itself; steps have a goal; burn has a fortnight to
 * sit against. Resting heart rate, HRV and skin temperature have no ceiling
 * at all, and a dial for them would be a denominator nobody picked.
 */
enum class DialFace(
    val title: String,
    val summary: String,
    val sizes: Set<WidgetSize>,
) {
    Score(
        "Sleep score",
        "Last night out of a hundred, in the middle of its dial",
        setOf(WidgetSize.Strip, WidgetSize.Square, WidgetSize.Wide),
    ),
    Three(
        "Three dials",
        "Sleep, steps and burn, each with its figure",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
    Week(
        "Seven nights",
        "A week of sleep scores, tallest the best",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
}

/** One dial: how far round, what colour, and what goes in the middle. */
data class DialReading(
    val fraction: Float,
    val colour: TileColour,
    val centre: String,
    val label: String,
)

/**
 * The sleep score, which is the only figure here out of a literal hundred.
 *
 * Null until the score has nights enough to stand on. `SleepScore` already
 * refuses to invent one, and a dial showing an empty circle where a refusal
 * belongs would be the widget making the claim the arithmetic declined to.
 */
internal fun sleepDial(health: HealthData): DialReading? {
    val total = health.sleepScore?.total ?: return null
    return DialReading(
        fraction = (total / 100f).coerceIn(0f, 1f),
        colour = TileColour.Sleep,
        centre = "$total",
        label = "Sleep",
    )
}

/**
 * The three that have a ceiling, in the order they are read.
 *
 * Steps and burn are shown as a percentage rather than as their own figure:
 * inside a 40dp circle "8,240" is four characters too many, and the point of
 * a dial is the proportion anyway. The widget's own label row carries the
 * real number at the sizes that have room.
 */
internal fun dialsOf(health: HealthData, goals: Goals): List<DialReading> {
    val dials = mutableListOf<DialReading>()
    sleepDial(health)?.let { dials += it }

    if (goals.steps > 0 && health.snapshot.stepsToday != null) {
        val fraction = health.stepProgress(goals.steps)
        dials += DialReading(
            fraction = fraction,
            colour = TileColour.Steps,
            centre = "${(fraction * 100).roundToInt()}",
            label = "Steps",
        )
    }

    val burned = health.snapshot.burnedToday
    val usual = usualBurn(health)
    if (burned != null && usual != null && usual > 0) {
        val fraction = (burned / usual).toFloat().coerceIn(0f, 1f)
        dials += DialReading(
            fraction = fraction,
            colour = TileColour.Energy,
            centre = "${(fraction * 100).roundToInt()}",
            label = "Burn",
        )
    }
    return dials
}

/**
 * A score for each of the last seven nights.
 *
 * Scored one night at a time against the whole history, which is how the
 * tab scores last night -- not against the six beside it, or a bad week
 * would quietly re-base itself and every night in it would look ordinary.
 */
internal fun nightScores(health: HealthData): List<Float> {
    val nights = health.nights
    if (nights.isEmpty()) return emptyList()
    return nights.takeLast(7).mapNotNull { night ->
        sleepScoreOf(night, nights).total?.toFloat()
    }
}

// ------------------------------------------------------------- the drawing

@Composable
internal fun DialsWidget(
    face: DialFace,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    if (!snapshot.granted) {
        Empty(colours, face.title, "Open the app to connect Health Connect", onClick)
        return
    }
    when (face) {
        DialFace.Score -> ScoreFace(size, snapshot, colours, onClick)
        DialFace.Three -> ThreeFace(size, snapshot, colours, onClick)
        DialFace.Week -> WeekFace(size, snapshot, colours, onClick)
    }
}

@Composable
private fun ScoreFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val dial = sleepDial(snapshot.health)
    if (dial == null) {
        Empty(colours, "Sleep", "Five nights gives a score", onClick)
        return
    }
    val padding = 11.dp
    val side = minOf(innerWidth(padding).value, innerHeight(padding).value)
        .coerceAtMost(DIAL_MAX.value).dp
    WidgetCard(colours, onClick, padding) {
        if (size == WidgetSize.Strip) {
            Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Dial(dial, colours, side, centreSize = 13, showLabel = false)
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Kicker("Sleep", colours.tile(TileColour.Sleep), size = 9)
                    Gap(2.dp)
                    Figure(dial.centre, 19, colours.onBackground)
                }
            }
        } else {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Dial(dial, colours, side, centreSize = if (size == WidgetSize.Wide) 30 else 34)
            }
        }
    }
}

@Composable
private fun ThreeFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val dials = dialsOf(snapshot.health, snapshot.goals)
    if (dials.isEmpty()) {
        Empty(colours, "Today", "Nothing here has a ceiling to measure against", onClick)
        return
    }
    val padding = 11.dp
    // Three across a card, with a gap between them: whatever is left after
    // the gaps, divided three ways, and never taller than the card.
    val across = (innerWidth(padding).value - DIAL_GAP.value * (dials.size - 1)) / dials.size
    val side = minOf(across, innerHeight(padding).value - 14f)
        .coerceIn(36f, DIAL_MAX.value).dp
    WidgetCard(colours, onClick, padding) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                dials.forEachIndexed { index, dial ->
                    if (index > 0) Spacer(GlanceModifier.width(DIAL_GAP))
                    Dial(dial, colours, side, centreSize = if (size == WidgetSize.Tall) 22 else 17)
                }
            }
        }
    }
}

@Composable
private fun WeekFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val scores = nightScores(snapshot.health)
    if (scores.isEmpty()) {
        Empty(colours, "Seven nights", "Five nights gives a score", onClick)
        return
    }
    val padding = 11.dp
    val colour = TileColour.Sleep
    WidgetCard(colours, onClick, padding) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Seven nights", colours.tile(colour))
            Spacer(GlanceModifier.defaultWeight())
            Sub("average ${scores.average().roundToInt()}", colours.muted, size = 10)
        }
        Gap(8.dp)
        // Out of a hundred, not out of the best night: a week of good sleep
        // drawn against its own best looks like a week of bad sleep.
        Bars(
            values = scores,
            colour = colours.tile(colour),
            dim = colours.tileDim(colour),
            height = (innerHeight(padding).value - 34f).coerceIn(28f, 180f).dp,
            highlightLast = false,
            top = 100f,
        )
        if (size == WidgetSize.Tall) {
            Gap(10.dp)
            Sub(
                "Each night scored against every night behind it, never against the six beside it.",
                colours.faint,
                size = 10,
                lines = 3,
            )
        }
    }
}

/** One arc with its figure inside, which is the whole idea of the family. */
@Composable
private fun Dial(
    dial: DialReading,
    colours: WidgetColours,
    side: Dp,
    centreSize: Int,
    showLabel: Boolean = true,
) {
    Box(contentAlignment = Alignment.Center) {
        RingImage(listOf(dial.fraction to dial.colour), colours, side, stroke = DIAL_STROKE)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Real Text over the image, never painted into it: baked in, the
            // numeral would freeze against the system text size, which
            // someone who has turned text up has asked for.
            Figure(dial.centre, centreSize, colours.onBackground)
            if (showLabel) {
                Gap(2.dp)
                Kicker(dial.label, colours.tile(dial.colour), size = 8)
            }
        }
    }
}

/** Thicker than a ring, because a dial is one arc and has the room. */
private const val DIAL_STROKE = 0.125f
private val DIAL_GAP = 10.dp
private val DIAL_MAX = 116.dp
