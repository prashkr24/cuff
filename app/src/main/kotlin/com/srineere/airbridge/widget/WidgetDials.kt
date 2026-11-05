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
import androidx.compose.runtime.remember
import androidx.glance.LocalContext
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import kotlin.math.abs

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
    Burn(
        "Burned against eaten",
        "Today's burn and today's food, with the gap between them shaded",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
    Overview(
        "The overview",
        "Three dials, the week under them, and a sentence",
        setOf(WidgetSize.Tall),
    ),
}

/**
 * Today's burn, hour by hour, against where yesterday's total would put it.
 *
 * The only widget in this family that answers "am I ahead", which is a
 * different question from "how far round has the arc gone" -- so it is the
 * only one here with no arc on it.
 */
internal fun burnToday(health: HealthData): List<Float> {
    val hourly = health.snapshot.burnedHourly.takeIf { it.size == 24 } ?: return emptyList()
    val now = java.time.LocalTime.now(java.time.ZoneId.systemDefault()).hour
    var sum = 0.0
    return (0..now.coerceIn(0, 23)).map { hour ->
        sum += hourly.getOrElse(hour) { 0.0 }
        sum.toFloat()
    }
}

/** Yesterday's whole day, which the dashed line is spread across. */
internal fun burnYesterday(health: HealthData): Float? =
    health.snapshot.burnedDaily.dropLast(1).lastOrNull()?.value?.toFloat()?.takeIf { it > 0f }

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
        DialFace.Burn -> BurnFace(size, snapshot, colours, onClick)
        DialFace.Overview -> OverviewFace(snapshot, colours, onClick)
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

/**
 * Burned against eaten, the way the screen of that name draws it.
 *
 * Today's climb against yesterday's pace was the old face here, and the
 * comparison was this app's own invention: a straight line drawn to a
 * total from a day that is finished, laid over a day that is not. The
 * screen behind the Energy tile sets today's burn against today's food --
 * both real, both still running -- and the distance between the two lines
 * at any hour is the figure at the top of the card.
 *
 * So this is that screen at widget size: the same two series, the same
 * shaded gap, the same stepped food line, and the key underneath saying
 * which line is which.
 */
@Composable
private fun BurnFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val day = balanceToday(snapshot.health.snapshot)
    if (!day.anything || day.burned.size < 2) {
        Empty(
            colours,
            "Burned against eaten",
            "Nothing burned or eaten has been read for today yet",
            onClick,
        )
        return
    }
    val padding = 12.dp
    val tall = size == WidgetSize.Tall
    val burnInk = colours.tile(TileColour.Activity)
    val foodInk = colours.tile(TileColour.Energy)
    val context = LocalContext.current
    val dark = colours.isDark(context)
    val density = context.resources.displayMetrics.density
    // The heading, the figure and the key under the chart. Over-estimated
    // on purpose: the chart is followed by a Grow, so guessing high costs
    // the chart a few pixels while guessing low clips the key off the card.
    val above = if (tall) 118f else 92f
    val chartHeight = (innerHeight(padding).value - above).coerceIn(44f, 200f)
    val widthPx = (innerWidth(padding).value * density).toInt()
    val heightPx = (chartHeight * density).toInt()
    val scale = Scale(
        ink = colours.faintArgb(dark),
        line = colours.trackArgb(dark),
        xTicks = day.hourTicks,
        yLabel = { shortFigure(it.toDouble()) },
    )
    val burnArgb = colours.tileArgb(TileColour.Activity, dark)
    val foodArgb = colours.tileArgb(TileColour.Energy, dark)
    val bitmap = remember(widthPx, heightPx, day.burned, day.eaten, dark) {
        ChartBitmap.pair(
            widthPx, heightPx, day.burned, day.eaten, burnArgb, foodArgb, density, scale,
        )
    }
    val gap = day.gap
    WidgetCard(colours, onClick, padding) {
        Kicker("Burned against eaten", foodInk, size = 10)
        Gap(if (tall) 7.dp else 5.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            Figure(
                signed(gap.roundToInt()),
                if (tall) 34 else 27,
                colours.onBackground,
                weight = FontWeight.Bold,
            )
            Spacer(GlanceModifier.width(6.dp))
            // "kcal behind, so far today" -- the screen's own line, which
            // carries the unit so the numeral above it does not have to.
            Sub(
                "kcal " + (if (gap < 0) "behind" else "ahead") + ", so far today",
                colours.faint,
                size = 11,
            )
        }
        Gap(8.dp)
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(chartHeight.dp),
        )
        Grow()
        Gap(8.dp)
        // The key, because two unlabelled lines are a picture rather than a
        // reading. Three Rows and not eleven children: a container here
        // keeps ten and drops the rest without saying so.
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Key("Burned", day.lastBurned, burnInk, colours)
            Spacer(GlanceModifier.defaultWeight())
            Key("Eaten", day.lastEaten, foodInk, colours)
            if (tall) {
                Spacer(GlanceModifier.defaultWeight())
                Key("Entries", day.entries, colours.faint, colours, plain = true)
            }
        }
    }
}

/** One line of the key: its colour, its name, and where it has got to. */
@Composable
private fun Key(
    label: String,
    value: Int,
    ink: androidx.glance.unit.ColorProvider,
    colours: WidgetColours,
    plain: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!plain) {
            Dot(ink, size = 7.dp)
            Spacer(GlanceModifier.width(5.dp))
        }
        Sub(label, colours.faint, size = 10)
        Spacer(GlanceModifier.width(5.dp))
        Figure("%,d".format(value), 14, colours.onBackground, weight = FontWeight.Medium)
    }
}

/**
 * Everything at once: the dials, the week under them, and a sentence.
 *
 * The only widget here that writes English. A number tells you what
 * happened and a sentence tells you whether it was unusual, which is the
 * thing a glance at a home screen is actually asking.
 */
@Composable
private fun OverviewFace(
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val dials = dialsOf(snapshot.health, snapshot.goals)
    if (dials.isEmpty()) {
        Empty(colours, "Today", "Nothing here has a ceiling to measure against", onClick)
        return
    }
    val padding = 12.dp
    val scores = nightScores(snapshot.health)
    val across = (innerWidth(padding).value - DIAL_GAP.value * (dials.size - 1)) / dials.size
    WidgetCard(colours, onClick, padding) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            dials.forEachIndexed { index, dial ->
                if (index > 0) Spacer(GlanceModifier.width(DIAL_GAP))
                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RingImage(listOf(dial.fraction to dial.colour), colours, across.coerceAtMost(76f).dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Figure(dial.centre, 20, colours.onBackground, weight = FontWeight.Bold)
                            Kicker(dial.label, colours.tile(dial.colour), size = 8)
                        }
                    }
                }
            }
        }
        if (scores.isNotEmpty()) {
            Gap(14.dp)
            Kicker("Seven nights", colours.tile(TileColour.Sleep), size = 9, caps = false)
            Gap(6.dp)
            Bars(
                values = scores,
                colour = colours.tile(TileColour.Sleep),
                dim = colours.tileDim(TileColour.Sleep),
                height = 46.dp,
                highlightLast = false,
                top = 100f,
            )
        }
        Grow()
        Sub(overviewSentence(snapshot), colours.muted, size = 12, lines = 3)
    }
}

/**
 * One sentence of plain English about today.
 *
 * Built from whichever halves are actually there: a phone with no food
 * logged still has something true to say about last night.
 */
private fun overviewSentence(snapshot: WidgetSnapshot): String {
    val health = snapshot.health
    val parts = mutableListOf<String>()
    health.sleepTrend?.let { trend ->
        val minutes = trend.change.roundToInt()
        if (abs(minutes) >= 5) {
            val size = if (abs(minutes) < 60) "${abs(minutes)} minutes" else "${abs(minutes) / 60}h ${abs(minutes) % 60}m"
            parts += "You slept $size ${if (minutes > 0) "longer" else "shorter"} than your usual night"
        }
    }
    burnYesterday(health)?.let { yesterday ->
        val climb = burnToday(health)
        val now = climb.lastOrNull() ?: 0f
        val hours = climb.size
        val paceNow = yesterday * ((hours - 1).coerceAtLeast(1) / 23f)
        val gap = (now - paceNow).roundToInt()
        if (abs(gap) >= 40) {
            parts += "you are ${abs(gap)} kcal ${if (gap > 0) "ahead of" else "behind"} yesterday at this hour"
        }
    }
    if (parts.isEmpty()) return "Nothing unusual so far today."
    return parts.joinToString(", and ") + "."
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
