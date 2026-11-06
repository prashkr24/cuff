package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.Readiness
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.asHoursMinutes
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.glance.text.FontWeight
import androidx.glance.layout.height

/**
 * What the Rings family can offer.
 *
 * Apple's grammar rather than Apple's design: three arcs that close, a list
 * that reads at arm's length, and a row of arrows against your own history.
 * Only the first needs a bitmap; the other two are the same Text and Row the
 * tiles already use, which is why all three arrive together.
 */
enum class RingFace(
    val title: String,
    val summary: String,
    val sizes: Set<WidgetSize>,
) {
    Arcs(
        "Three rings",
        "Steps, sleep and burn, as arcs that close",
        setOf(WidgetSize.Square, WidgetSize.Wide, WidgetSize.Tall),
    ),
    Summary(
        "Summary",
        "Four readings, one line each",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
    Trends(
        "Trends",
        "Each reading against your own fortnight",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
}


/**
 * How many rows of [each] dp fit under a face's head.
 *
 * Measured off the cell the launcher actually gave us, because launchers
 * do not agree on what a 4 x 2 is and a count tuned on one phone is a
 * clipped row on another. At least one, so a cramped cell shows something
 * rather than a head and a space.
 */
@Composable
private fun rowsThatFit(padding: Dp, each: Float): Int =
    ((innerHeight(padding).value - FACE_HEAD_HEIGHT) / each).toInt().coerceAtLeast(1)

/** The head, its gap, and the hairline under it. */
private const val FACE_HEAD_HEIGHT = 24f

/** A summary row: a caption, a figure and the gap above them. */
private const val SUMMARY_ROW_TALL = 48f
private const val SUMMARY_ROW_WIDE = 40f

/** A trend row: three lines and a rail. */
private const val TREND_ROW_TALL = 62f
private const val TREND_ROW_WIDE = 52f

/** One arc: how far round it has gone, and what it is. */
data class Ring(
    val label: String,
    val fraction: Float,
    val colour: TileColour,
    val figure: String,
    val of: String,
    /**
     * The window this ring is about.
     *
     * Three arcs side by side look like three answers to one question, and
     * they are not: steps is today, active minutes is the week, sleep is
     * last night. Saying so at the end of each line is the difference
     * between a summary and three numbers that happen to be adjacent.
     */
    val note: String,
)

/**
 * The three rings, and why they are these three.
 *
 * Apple closes Move, Exercise and Stand, all of them daily and all of them
 * against a target the owner set. Ours has to be built from what is actually
 * stored, which rules out the obvious copy: `Goals.activeMinutes` is a
 * *weekly* figure -- the WHO's lower bound -- and a weekly ring beside two
 * daily ones is a chart that lies about what a full circle means. Dividing
 * it by seven would invent a target nobody chose.
 *
 * So: steps and sleep, which have daily goals someone set, and burn, which
 * has no goal at all and is drawn against the fortnight behind it. A ring
 * with no target is a ring against your usual day, and the widget says so
 * rather than implying somebody recommended it.
 *
 * A ring with nothing behind it is absent, never a closed circle and never a
 * zero: both of those are claims.
 */
internal fun ringsOf(health: HealthData, goals: Goals): List<Ring> {
    val rings = mutableListOf<Ring>()

    if (goals.steps > 0) {
        val steps = health.stepsToday
        rings += Ring(
            label = "Steps",
            fraction = health.stepProgress(goals.steps),
            colour = TileColour.Steps,
            figure = "%,d".format(steps),
            of = "of %,d".format(goals.steps),
            note = "today",
        )
    }

    // Active minutes, not burn. Burn was measured against its own fortnight
    // average, which is a denominator this app invented; active minutes has
    // a goal the person actually set, which is what an arc that closes
    // needs. Burn keeps its place on the Summary face, where a figure does
    // not have to be a fraction of anything.
    val active = activeMinutesThisWeek(health)
    if (active != null && goals.activeMinutes > 0) {
        rings += Ring(
            label = "Active",
            fraction = (active.toFloat() / goals.activeMinutes).coerceIn(0f, 1f),
            colour = TileColour.Activity,
            figure = "$active",
            of = "of ${goals.activeMinutes} min",
            note = "this week",
        )
    }

    val asleep = health.lastNight?.asleep
    if (asleep != null && goals.sleepMinutes > 0) {
        val minutes = asleep.toMinutes()
        rings += Ring(
            label = "Sleep",
            fraction = (minutes.toFloat() / goals.sleepMinutes).coerceIn(0f, 1f),
            colour = TileColour.Sleep,
            figure = asleep.asHoursMinutes(),
            of = "of ${goals.sleepMinutes / 60}h",
            note = "last night",
        )
    }

    return rings
}

/** Minutes of logged exercise since the week began. */
internal fun activeMinutesThisWeek(health: HealthData): Long? {
    val sessions = health.snapshot.sessions
    if (sessions.isEmpty()) return null
    val zone = java.time.ZoneId.systemDefault()
    val monday = java.time.LocalDate.now(zone)
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        .atStartOfDay(zone).toInstant()
    return sessions
        .filter { !it.start.isBefore(monday) }
        .sumOf { java.time.Duration.between(it.start, it.end).toMinutes() }
}

/**
 * A usual day's burn, from the days before this one.
 *
 * Today is excluded deliberately: at nine in the morning it is a fifth of a
 * day, and letting it into its own average drags the thing it is measured
 * against down towards itself.
 */
internal fun usualBurn(health: HealthData): Double? {
    val daily = health.snapshot.burnedDaily
    val before = daily.dropLast(1).takeLast(14)
    if (before.size < 3) return null
    return before.map { it.value }.average().takeIf { it > 0.0 }
}

/**
 * One line of the Summary face.
 *
 * A name and a value was a list; this is a reading. The week behind each
 * figure is the part that says whether the figure is unusual, and it is
 * the one thing a list of four numbers could never tell you.
 */
internal data class SummaryRow(
    val label: String,
    val figure: String,
    /** The unit, or what the figure is a share of. */
    val of: String?,
    /** A change, or a score -- whichever that reading actually has. */
    val chip: String?,
    val colour: TileColour,
    /** The last seven, oldest first, for the bars beside it. */
    val spark: List<Float>,
)

/** The readings the Summary face lists, in the order it lists them. */
internal fun summaryRows(
    health: HealthData,
    goals: Goals,
    readiness: Readiness? = null,
): List<SummaryRow> = listOfNotNull(
    // First, because it is the one line here that is about this morning
    // rather than about a reading taken during it -- and because it is
    // the only one the other three are ingredients of.
    readiness?.score?.let { score ->
        SummaryRow(
            label = "Readiness",
            figure = score.toString(),
            of = null,
            chip = null,
            colour = TileColour.Hrv,
            spark = readiness.trend?.readings?.takeLast(7)?.map { it.value.toFloat() }.orEmpty(),
        )
    },
    health.restingTrend?.let { trend ->
        SummaryRow(
            label = "Resting heart",
            figure = trend.latest.roundToInt().toString(),
            of = "bpm",
            chip = arrowed(trend.change, 0, ""),
            colour = TileColour.Heart,
            spark = trend.readings.takeLast(7).map { it.value.toFloat() },
        )
    },
    health.snapshot.stepsToday?.let { steps ->
        SummaryRow(
            label = "Steps",
            figure = "%,d".format(steps),
            of = goals.steps.takeIf { it > 0 }?.let { "of %,d".format(it) },
            chip = null,
            colour = TileColour.Steps,
            spark = health.snapshot.steps.takeLast(7).map { it.value.toFloat() },
        )
    },
    health.lastNight?.let { night ->
        SummaryRow(
            label = "Sleep",
            figure = night.asleep.asHoursMinutes(),
            of = null,
            chip = health.sleepScore?.total?.toString(),
            colour = TileColour.Sleep,
            spark = health.nights.takeLast(7).map { it.asleep.toMinutes().toFloat() },
        )
    },
)

/**
 * The same four, as a direction and a size rather than a value.
 *
 * Each against its own window, in its own units, the way the tab's chips
 * read. A reading whose change rounds away says Steady, because an arrow
 * pointing at nothing is worse than no arrow.
 */
internal data class TrendRow(
    val label: String,
    val colour: TileColour,
    /** Null where the change rounds away, and the arrow goes with it. */
    val up: Boolean?,
    /** Where the reading stands now: "56 bpm". */
    val value: String,
    /** What moved, in words: "2 bpm lower". */
    val phrase: String,
    val trend: Trend,
    /** "usual 56-61", the band the dot is sitting against. */
    val usual: String,
)

/**
 * The same readings as a direction and a size rather than a value.
 *
 * Resting heart and HRV lead, because they are the two the rail says most
 * about: both are a single figure a night against a settled band, so "here
 * against where you usually are" is a sentence they can actually finish.
 */
internal fun trendRows(health: HealthData, readiness: Readiness? = null): List<TrendRow> =
    listOfNotNull(
    readiness?.trend?.takeIf { readiness.score != null }
        ?.let { trendRow("Readiness", TileColour.Hrv, it, "", 0) },
    health.restingTrend?.let { trendRow("Resting heart", TileColour.Heart, it, "bpm", 0) },
    health.hrvTrend?.let { trendRow("HRV", TileColour.Hrv, it, "ms", 0) },
    health.stepsTrend?.let { trendRow("Steps", TileColour.Steps, it, "", 0) },
    health.sleepTrend?.let { sleepTrendRow(it) },
)

private fun trendRow(
    label: String,
    colour: TileColour,
    trend: Trend,
    unit: String,
    decimals: Int,
): TrendRow {
    val size = if (decimals == 0) {
        abs(trend.change).roundToInt().toString()
    } else {
        "%.${decimals}f".format(abs(trend.change))
    }
    val moved = (size.toDoubleOrNull() ?: 0.0) > 0.0
    val suffix = if (unit.isEmpty()) "" else " $unit"
    return TrendRow(
        label = label,
        colour = colour,
        up = if (!moved) null else trend.change > 0,
        value = trend.latest.roundToInt().toString() + suffix,
        phrase = if (!moved) "steady" else "$size$suffix ${if (trend.change > 0) "higher" else "lower"}",
        trend = trend,
        usual = "usual ${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}",
    )
}

/** Sleep moves in minutes, which read as hours and minutes. */
private fun sleepTrendRow(trend: Trend): TrendRow {
    val minutes = trend.change.roundToInt()
    val moved = abs(minutes) >= 5
    val size = if (abs(minutes) < 60) "${abs(minutes)}m" else "${abs(minutes) / 60}h ${abs(minutes) % 60}m"
    return TrendRow(
        label = "Sleep",
        colour = TileColour.Sleep,
        up = if (!moved) null else minutes > 0,
        value = java.time.Duration.ofMinutes(trend.latest.roundToInt().toLong()).asHoursMinutes(),
        phrase = if (!moved) "steady" else "$size ${if (minutes > 0) "longer" else "shorter"}",
        trend = trend,
        usual = "usual ${trend.usual.low.roundToInt() / 60}h–${trend.usual.high.roundToInt() / 60}h",
    )
}

private fun arrowed(change: Double, decimals: Int, unit: String): String {
    val shown = if (decimals == 0) {
        abs(change).roundToInt().toString()
    } else {
        "%.${decimals}f".format(abs(change))
    }
    if ((shown.toDoubleOrNull() ?: 0.0) <= 0.0) return "Steady"
    val arrow = if (change > 0) "↑" else "↓"
    return if (unit.isEmpty()) "$arrow $shown" else "$arrow $shown $unit"
}

private fun arrowedMinutes(change: Double): String {
    val minutes = change.roundToInt()
    if (abs(minutes) < 5) return "Steady"
    val arrow = if (minutes > 0) "↑" else "↓"
    val size = abs(minutes)
    return if (size < 60) "$arrow ${size}m" else "$arrow ${size / 60}h ${size % 60}m"
}

// ------------------------------------------------------------- the drawing

/**
 * The Rings family, drawn.
 *
 * The arcs are a bitmap and everything else is ordinary Glance. That split
 * is the whole cost of this family: three rings are one Canvas per
 * composition, and the two list faces cost nothing at all -- which is why
 * all three shipped together rather than the arcs alone.
 */
@Composable
internal fun RingsWidget(
    face: RingFace,
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
        RingFace.Arcs -> ArcsFace(size, snapshot, colours, onClick)
        RingFace.Summary -> SummaryFace(size, snapshot, colours, onClick)
        RingFace.Trends -> TrendsFace(size, snapshot, colours, onClick)
    }
}

@Composable
private fun ArcsFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val rings = ringsOf(snapshot.health, snapshot.goals)
    if (rings.isEmpty()) {
        Empty(colours, "Today", "No goal has a reading behind it yet", onClick)
        return
    }
    val padding = 11.dp
    val inner = innerHeight(padding)
    // Square spends everything on the arcs; the wider faces give them the
    // card's height and keep the rest for the figures, which is the only
    // place the three readings can actually be named.
    val arcSide = if (size == WidgetSize.Square) {
        minOf(innerWidth(padding).value, inner.value).dp
    } else {
        inner.value.coerceAtMost(ARC_MAX.value).dp
    }

    WidgetCard(colours, onClick, padding) {
        if (size == WidgetSize.Square) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Arcs(rings, colours, arcSide)
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Arcs(rings, colours, arcSide)
                Spacer(GlanceModifier.width(14.dp))
                // One child per ring, and that is load-bearing: a Glance
                // container keeps ten children and silently drops the rest,
                // and three rings written as kicker-gap-row came to eleven
                // -- which is why Burn used to show its name and no figure.
                Column(modifier = GlanceModifier.defaultWeight()) {
                    rings.forEachIndexed { index, ring ->
                        RingRow(ring, size, colours, first = index == 0)
                    }
                }
            }
        }
    }
}

/** One ring's line: a dot, its name, its figure, and the window it covers. */
@Composable
private fun RingRow(ring: Ring, size: WidgetSize, colours: WidgetColours, first: Boolean) {
    val tint = colours.tile(ring.colour)
    Column {
        if (!first) Gap(if (size == WidgetSize.Tall) 12.dp else 8.dp)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(tint, size = 7.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    Kicker(ring.label, tint, size = WidgetText.Caption)
                }
                Gap(1.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Figure(
                        ring.figure,
                        if (size == WidgetSize.Tall) WidgetText.FigureM else WidgetText.FigureS,
                        colours.onBackground,
                        weight = androidx.glance.text.FontWeight.Bold,
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Sub(ring.of, colours.faint, size = WidgetText.Label)
                }
            }
            Sub(ring.note, colours.faint, size = WidgetText.Caption, lines = 2)
        }
    }
}

@Composable
private fun Arcs(rings: List<Ring>, colours: WidgetColours, side: Dp) =
    RingImage(rings.map { it.fraction to it.colour }, colours, side)

/** The bitmap, and the only one in this app so far. */
@Composable
internal fun RingImage(
    parts: List<Pair<Float, TileColour>>,
    colours: WidgetColours,
    side: Dp,
    stroke: Float = RingBitmap.STROKE,
    /**
     * Forces the light half, for a family that paints its own pale card.
     * Null resolves the usual way, from the theme or the launcher.
     */
    light: Boolean? = null,
) {
    if (parts.isEmpty()) return
    val context = LocalContext.current
    val dark = if (light == true) false else colours.isDark(context)
    val density = context.resources.displayMetrics.density
    val sidePx = (side.value * density).toInt()
    val arcs = parts.map { (fraction, colour) -> fraction to colours.tileArgb(colour, dark) }
    val track = colours.trackArgb(dark)
    // Keyed on everything that changes a pixel, so scrolling past a widget
    // does not redraw a Canvas that would come out identical.
    val bitmap = remember(sidePx, arcs, track, stroke) {
        RingBitmap.draw(sidePx, arcs, track, stroke = stroke)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = GlanceModifier.size(side),
    )
}

/**
 * Three readings, each with the week behind it.
 *
 * One child per row, as everywhere else in this family: a Glance container
 * keeps ten children and drops the rest without saying so.
 */
@Composable
private fun SummaryFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val rows = summaryRows(snapshot.health, snapshot.goals, snapshot.readiness)
    if (rows.isEmpty()) {
        Empty(colours, "Summary", "Nothing has been read yet", onClick)
        return
    }
    val padding = 12.dp
    val spark = (innerWidth(padding).value * 0.32f).coerceIn(56f, 120f).dp
    // However many fit, measured rather than assumed. This face had no cap
    // at all and drew every row it was given: four of them already ran off
    // the bottom of a 4 x 2, and a fifth would have made it worse on both.
    val shown = rows.take(
        rowsThatFit(padding, if (size == WidgetSize.Tall) SUMMARY_ROW_TALL else SUMMARY_ROW_WIDE),
    )
    WidgetCard(colours, onClick, padding) {
        FaceHead("Summary", snapshot.readAt, colours)
        shown.forEachIndexed { index, row ->
            SummaryLine(row, size, colours, spark, first = index == 0)
        }
    }
}

@Composable
private fun SummaryLine(
    row: SummaryRow,
    size: WidgetSize,
    colours: WidgetColours,
    sparkWidth: Dp,
    first: Boolean,
) {
    val tint = colours.tile(row.colour)
    Column {
        Gap(if (first) 8.dp else if (size == WidgetSize.Tall) 14.dp else 9.dp)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Kicker(row.label, tint, size = WidgetText.Caption)
                Gap(1.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Figure(row.figure, if (size == WidgetSize.Tall) WidgetText.FigureM else WidgetText.FigureS, colours.onBackground, weight = FontWeight.Bold)
                    row.of?.let {
                        Spacer(GlanceModifier.width(5.dp))
                        Sub(it, colours.faint, size = WidgetText.Label)
                    }
                    row.chip?.let {
                        Spacer(GlanceModifier.width(7.dp))
                        Chip(it, tint, colours.tileWash(row.colour), tight = true)
                    }
                }
            }
            Spark(row.spark, row.colour, colours, sparkWidth, if (size == WidgetSize.Tall) 30.dp else 24.dp)
        }
    }
}

/** The week behind a figure, as bars. The newest is picked out. */
@Composable
private fun Spark(
    values: List<Float>,
    colour: TileColour,
    colours: WidgetColours,
    width: Dp,
    height: Dp,
) {
    if (values.size < 2) return
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val ink = colours.tileArgb(colour, dark)
    val dim = colours.tileDimArgb(colour, dark)
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val bitmap = remember(widthPx, heightPx, values, ink, dim) {
        ChartBitmap.bars(widthPx, heightPx, values, null, ink, dim, true, false, density)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.width(width).height(height),
    )
}

/**
 * Each reading against its own fortnight, as a sentence and a rail.
 *
 * The rail is the part a list could not do: an arrow says something moved
 * and a figure says how far, but neither says whether where you have
 * landed is anywhere unusual for you.
 */
@Composable
private fun TrendsFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val all = trendRows(snapshot.health, snapshot.readiness)
    if (all.isEmpty()) {
        Empty(colours, "Trends", "A fortnight of nights gives a trend", onClick)
        return
    }
    val padding = 12.dp
    // A trend row is three lines tall, and a row squeezed to one line is
    // the list this replaced -- so the count comes off the cell's real
    // height rather than off a constant that was right for four rows and
    // is now being asked about five.
    val rows = all.take(
        rowsThatFit(padding, if (size == WidgetSize.Tall) TREND_ROW_TALL else TREND_ROW_WIDE),
    )
    val railWidth = (innerWidth(padding).value - ARROW_COLUMN - USUAL_COLUMN).coerceAtLeast(40f).dp
    WidgetCard(colours, onClick, padding) {
        FaceHead("Trends · your fortnight", snapshot.readAt, colours)
        rows.forEachIndexed { index, row ->
            TrendLine(row, colours, railWidth, first = index == 0)
        }
    }
}

@Composable
private fun TrendLine(row: TrendRow, colours: WidgetColours, railWidth: Dp, first: Boolean) {
    val tint = colours.tile(row.colour)
    Column {
        Gap(if (first) 10.dp else 14.dp)
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            // The direction, large, at the left: the one thing worth
            // reading from across a room.
            Figure(
                when (row.up) {
                    true -> "↑"
                    false -> "↓"
                    null -> "–"
                },
                22,
                tint,
                weight = FontWeight.Bold,
            )
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(row.label, tint, size = WidgetText.Caption)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(row.value, colours.muted, size = WidgetText.Label)
                }
                Gap(2.dp)
                Sub(row.phrase, colours.onBackground, size = WidgetText.Body)
                Gap(5.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = GlanceModifier.width(railWidth)) {
                        RailsImage(
                            Chart.Rails(listOf(row.trend to row.colour)),
                            colours,
                            height = 6.dp,
                            width = railWidth,
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Sub(row.usual, colours.faint, size = WidgetText.Caption)
                }
            }
        }
    }
}

/** A face's name, and the clock time the reading behind it was taken. */
@Composable
private fun FaceHead(title: String, readAt: Long, colours: WidgetColours) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Kicker(title, colours.muted, size = WidgetText.Label)
        Spacer(GlanceModifier.defaultWeight())
        if (readAt > 0) {
            val at = java.time.Instant.ofEpochMilli(readAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            Sub("%02d:%02d".format(at.hour, at.minute), colours.faint, size = WidgetText.Caption)
        }
    }
}

/** The arrow and the gutter the usual band's words need. */
private const val ARROW_COLUMN = 32f
private const val USUAL_COLUMN = 74f

/** An arc larger than this stops looking like a widget and starts looking like a clock. */
private val ARC_MAX = 132.dp
