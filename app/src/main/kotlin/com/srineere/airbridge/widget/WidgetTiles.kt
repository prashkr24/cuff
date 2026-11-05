package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.glance.layout.height
import androidx.glance.layout.ContentScale
import androidx.glance.LocalContext
import androidx.glance.ImageProvider
import androidx.glance.Image
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.SleepStage
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.ui.HealthStage
import com.srineere.airbridge.ui.asHoursMinutes
import com.srineere.airbridge.ui.toDailyMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a tile says, before anything has decided how big to draw it.
 *
 * Built once per widget, outside composition, so that every face of a kind
 * is drawing the same arithmetic -- and read by the catalogue under Device,
 * so a preview there is the widget's own reading rather than a second
 * drawing of it that can drift -- the only thing a size changes is how
 * much of this is shown. That is the promise the pick sheets make: the
 * label never moves, and a swipe or a resize changes the reading, not the
 * layout.
 */
internal data class Face(
    val kicker: String,
    val figure: String,
    val sub: String,
    val unit: String? = null,
    val detail: String? = null,
    val chart: Chart? = null,
    val meter: Float? = null,
    val chip: String? = null,
    /** The tab's head shows "\u00b7 30 days" once the card is wide enough. */
    val window: String? = null,
    /** The full name, for the widths that have room for it. */
    val longKicker: String? = null,
    /**
     * The figure where two columns cannot hold the long one.
     *
     * "−1,533" does not fit a Square and a clipped number is a wrong
     * number, so Energy steps down to "−1.5k" exactly as its tile does.
     */
    val shortFigure: String? = null,
    /**
     * A filled pill under the figure -- Energy's "Deficit", Nutrition's
     * nothing. The tab draws this as `TileChip`; the widget said the same
     * thing in plain grey words, which is a different component.
     */
    val badge: String? = null,
    /**
     * A second figure at the far end of the figure's line, with its name
     * under it. Sleep's score, and only at the size with room for it.
     */
    val trailingFigure: String? = null,
    val trailingLabel: String? = null,
    /**
     * A goal ring, where the tile draws one.
     *
     * Steps is the only tile on the tab with a ring, and it is the first
     * thing you see on it -- a 4dp meter said the same fraction and looked
     * like a different tile. [ringLabel] goes in the hole.
     */
    val ring: Float? = null,
    val ringLabel: String? = null,
    val ringSub: String? = null,
)

/**
 * Whether this card is wide enough for a chart to carry its scale.
 *
 * The tab passes `axes` to a tile's chart at L and XL and passes null
 * below that, so a small tile gets the bare shape. Wide and Tall are those
 * two sizes, and the rule is the same one.
 */
internal fun WidgetSize.scaled(): Boolean = this == WidgetSize.Wide || this == WidgetSize.Tall

internal sealed interface Chart {
    /**
     * Equal-width columns: the only chart Glance draws without a bitmap.
     *
     * [blankZeros] is for a series whose slots exist whether or not they
     * hold a reading -- the hours of today. A gap there means "nothing was
     * recorded then", which is not the same claim as a bar of zero height.
     */
    class Bars(
        val values: List<Float>,
        val blankZeros: Boolean = false,
        /** What a full-height bar means, when the series has a ceiling of its own. */
        val ceiling: Float? = null,
        /** The dashed line the tile draws across it, in the series' units. */
        val goal: Float? = null,
        val goalLabel: String? = null,
        /** Positions from 0 to 1 with their labels, for the footer. */
        val xTicks: List<Pair<Float, String>> = emptyList(),
        /** Formats a value for the gutter, or null for no gutter. */
        val yLabel: ((Float) -> String)? = null,
        /**
         * Whether the newest bar is picked out.
         *
         * False for a series where every bar is a finished thing: seven
         * scored nights are seven equals, and brightening the last one says
         * "this is today" about a night that ended hours ago.
         */
        val highlightLast: Boolean = true,
    ) : Chart

    /** A night, in proportion. Keeps the sleep palette whatever colour the tile is. */
    class Stages(val parts: List<Pair<Float, SleepStage>>) : Chart

    /**
     * A week of nights, each a bar divided into its stages.
     *
     * What the tile actually draws. One night as a stripe was a different
     * chart answering a different question: the tile's sleep chart is
     * about the week the night sits in, and the goal line is why.
     */
    class StageWeek(
        val nights: List<List<Pair<Float, SleepStage>>>,
        val goalMinutes: Float?,
        val xTicks: List<Pair<Float, String>> = emptyList(),
    ) : Chart

    /** Two or three shares that mean different things, each with its own colour. */
    class Split(val parts: List<Pair<Float, TileColour>>) : Chart

    /** Several readings that none of them fills a tile alone. */
    class Rows(val rows: List<Triple<String, String, TileColour>>) : Chart

    /**
     * Concentric arcs. The one chart here that cannot be made of
     * rectangles, so the one that costs a bitmap.
     */
    class Arcs(val parts: List<Pair<Float, TileColour>>) : Chart

    /**
     * One dot a reading, over the band half of them land in.
     *
     * A bar says "this much" and a dot says "here, against where you
     * usually are" -- and six of the tiles say the second thing. Glance
     * cannot draw a dot, so this one costs a bitmap.
     */
    class Scatter(val values: List<Double>, val low: Double?, val high: Double?) : Chart

    /**
     * One rail a reading: where it sits across its own range, over the
     * half of that range it usually lands in.
     *
     * What `VitalsTile` draws. Naming the readings and their figures was a
     * list; this is the tile's actual claim, which is positional.
     */
    class Rails(val rails: List<Pair<Trend?, TileColour>>) : Chart

    /**
     * A day of beats, each slice as the range within it.
     *
     * `DayHeartLine`'s shape. Binning the day into twenty-four fat columns
     * answered "what was the highest reading that hour", which is not the
     * question the tile's chart is about.
     */
    class Beats(val samples: List<com.srineere.airbridge.health.HeartSample>) : Chart

    /**
     * Two running totals on one axis, climbing against each other.
     *
     * [ticks] comes from the day rather than from the chart, because the
     * axis belongs to the reading: the screen marks every few hours the day
     * has actually reached and then the hour it is now, so the last label
     * sits under the end of the lines instead of at a round number they
     * have not got to.
     */
    class Climb(
        val first: List<Float>,
        val second: List<Float>,
        val firstColour: TileColour,
        val secondColour: TileColour,
        val ticks: List<Pair<Float, String>> = emptyList(),
    ) : Chart

    /** Arcs side by side, each with its figure in the middle. */
    class Dials(val parts: List<DialReading>) : Chart

    /**
     * The same readings across rather than down, divided by hairlines.
     *
     * A separate type from [Rows] only because a thumbnail has to tell the
     * two apart: the Rail widget and the Day widget show the same four
     * readings and are otherwise nothing alike, and two identical pictures
     * in a catalogue are a catalogue you cannot choose from.
     */
    class Rail(val parts: List<Triple<String, String, TileColour>>) : Chart
}

@Composable
internal fun TileWidget(
    kind: TileKind,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val colour = snapshot.colourFor(kind)
    if (!snapshot.granted) {
        EmptyTile(kind, colour, colours, notConnected(snapshot), onClick)
        return
    }
    val face = faceFor(kind, snapshot)
    if (face == null) {
        EmptyTile(kind, colour, colours, "Nothing writes this to Health Connect yet", onClick)
        return
    }
    DrawFace(face, size, colour, colours, onClick, snapshot.showTrends)
}

/**
 * A tile with nothing to say, as the tab says it.
 *
 * Not the shared [Empty], which leads with a big dash: `TileEmpty` keeps
 * the head and puts the reason under it in the same small type as any
 * other sub. A tile that has lost its reading still looks like that tile,
 * and a dash where the figure goes reads as a reading of nothing.
 */
@Composable
private fun EmptyTile(
    kind: TileKind,
    colour: TileColour,
    colours: WidgetColours,
    reason: String,
    onClick: Action?,
) {
    WidgetCard(
        colours,
        onClick,
        horizontal = TILE_PAD_H,
        vertical = TILE_PAD_V,
        border = colours.outline,
    ) {
        Kicker(kind.short, colours.tile(colour), size = KICKER, caps = false)
        Gap(6.dp)
        Sub(reason, colours.muted, size = SUB, lines = 3, weight = FontWeight.Medium)
    }
}

private fun notConnected(snapshot: WidgetSnapshot): String = when (snapshot.health.stage) {
    HealthStage.NotConnected -> "Open the app to connect Health Connect"
    HealthStage.NeedsUpdate -> "Health Connect needs an update"
    else -> "Not read yet"
}

// ----------------------------------------------------------------- drawing

@Composable
private fun DrawFace(
    face: Face,
    size: WidgetSize,
    colour: TileColour,
    colours: WidgetColours,
    onClick: Action?,
    showTrends: Boolean,
) {
    val tint = colours.tile(colour)
    // A launcher hands out whatever cell it likes, and the nearest declared
    // size is often a little shorter than the space. Centred, so a strip in a
    // slightly taller cell sits in it rather than clinging to the top edge.
    //
    // A stripe is centred for the opposite reason: it is a fixed 14dp
    // whatever the cell, so a card built round one cannot fill itself the
    // way a card built round bars does, and the slack is better shared
    // above and below than banked at the bottom.
    val alignment = when {
        size == WidgetSize.Strip || size == WidgetSize.Row -> Alignment.CenterVertically
        face.chart.isShort() -> Alignment.CenterVertically
        else -> Alignment.Top
    }
    WidgetCard(
        colours,
        onClick,
        verticalAlignment = alignment,
        horizontal = TILE_PAD_H,
        vertical = TILE_PAD_V,
        border = colours.outline,
    ) {
        // The one tile on the tab with a ring has a shape of its own, so
        // the widget borrows that shape rather than the generic one.
        if (face.ring != null && (size == WidgetSize.Wide || size == WidgetSize.Tall)) {
            RingFace(face, size, colour, tint, colours, showTrends)
            return@WidgetCard
        }
        when (size) {
            WidgetSize.Strip -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(face.kicker, tint, size = KICKER, caps = false)
                    // Two columns leave room for an arrow and nothing else,
                    // and the figure beside it would be cut in half. The
                    // direction is the part that survives at this width.
                    if (showTrends && face.chip != null) {
                        Spacer(GlanceModifier.defaultWeight())
                        Sub(arrowOf(face.chip), tint, size = WidgetText.Label, weight = FontWeight.Bold)
                    }
                }
                Gap(3.dp)
                // Not the tab's 26: a 2x1 cell is a third of the tile's
                // height and the figure is the only thing that can give way
                // -- the kicker is already as small as the tab draws one.
                FigureAndUnit(face, 22, colours)
            }

            WidgetSize.Row -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(face.kicker, tint, size = KICKER, caps = false)
                    Spacer(GlanceModifier.width(9.dp))
                    FigureAndUnit(face, FIGURE, colours)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(face.chip ?: face.sub, colours.muted, size = SUB, weight = FontWeight.Medium)
                }
            }

            WidgetSize.Square -> {
                Head(face, size, colour, tint, colours, showTrends, wide = false)
                Gap(5.dp)
                FigureAndUnit(face, FIGURE, colours, short = true)
                Gap(2.dp)
                Badged(face, tint, colours, colour)
                face.meter?.let {
                    Gap(7.dp)
                    Meter(it, innerWidth(TILE_PAD_H), tint, colours.track, height = 4.dp)
                }
                Grow()
                DrawChart(
                    face.chart, colour, colours,
                    // What is left under the figure, rather than a constant
                    // that is right in one cell and wrong in every other.
                    height = (innerHeight(TILE_PAD_V).value - SQUARE_ABOVE_CHART)
                        .coerceIn(20f, 72f).dp,
                    width = innerWidth(TILE_PAD_H),
                    scaled = size.scaled(),
                )
            }

            // The tall face stacks, as the tile's XL does: the figure across
            // the top and the chart under it with the whole width. Only the
            // wide face puts them side by side.
            WidgetSize.Tall -> {
                Head(face, size, colour, tint, colours, showTrends, wide = true)
                Gap(5.dp)
                // The tab's hero is displaySmall, which in this app is 26sp
                // -- the same as the headlineSmall every other tile size
                // uses. Thirty-two was a hero the tab does not draw.
                // The score sits at the far end of the hours it scores, as
                // the tile puts it, rather than under them as another sub.
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column { FigureAndUnit(face, FIGURE, colours) }
                    face.trailingFigure?.let {
                        Spacer(GlanceModifier.defaultWeight())
                        Column(horizontalAlignment = Alignment.End) {
                            Figure(it, FIGURE, tint, weight = FontWeight.Bold)
                            face.trailingLabel?.let { name ->
                                Sub(name, colours.muted, size = SUB, weight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Gap(2.dp)
                Badged(face, tint, colours, colour)
                face.detail?.let {
                    Gap(3.dp)
                    Sub(it, colours.faint, size = SUB, weight = FontWeight.Medium)
                }
                face.meter?.let {
                    Gap(8.dp)
                    Meter(it, innerWidth(TILE_PAD_H), tint, colours.track, height = 5.dp)
                }
                Gap(10.dp)
                DrawChart(
                    face.chart, colour, colours,
                    height = (innerHeight(TILE_PAD_V).value - TALL_ABOVE_CHART)
                        .coerceIn(40f, 260f).dp,
                    width = innerWidth(TILE_PAD_H),
                    scaled = size.scaled(),
                )
            }

            WidgetSize.Wide -> {
                Head(face, size, colour, tint, colours, showTrends, wide = true)
                Gap(6.dp)
                // All of it. A cap here was what left a field of empty card
                // under the chart: a launcher's 4x4 is taller than the 300dp
                // the face was drawn for, and a widget should spend the
                // space it was given.
                val chartHeight = (innerHeight(TILE_PAD_V).value - WIDE_ABOVE_CHART)
                    .coerceIn(44f, 400f).dp
                @Suppress("UNUSED_EXPRESSION") size
                // Centred, not sat on the floor: every L tile on the tab
                // puts its figure column level with the chart beside it.
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = GlanceModifier.width(FIGURE_COLUMN)) {
                        FigureAndUnit(face, FIGURE, colours)
                        Gap(2.dp)
                        // The tab's `TileSub` is onSurfaceVariant at every
                        // size. Tinting it here made the one line under the
                        // figure the only coloured sub in either place.
                        Badged(face, tint, colours, colour)
                        face.detail?.let {
                            Gap(4.dp)
                            Sub(it, colours.faint, size = SUB, lines = 2, weight = FontWeight.Medium)
                        }
                        face.meter?.let {
                            Gap(7.dp)
                            Meter(it, FIGURE_COLUMN, tint, colours.track, height = 4.dp)
                        }
                    }
                    Spacer(GlanceModifier.width(12.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        DrawChart(
                            face.chart, colour, colours,
                            height = chartHeight,
                            width = innerWidth(TILE_PAD_H).value.minus(FIGURE_COLUMN.value + 12).dp,
                            scaled = size.scaled(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether this chart draws to a fixed height rather than filling what it
 * is given. Stripes do; bars, scatters and the sleep week do not.
 */
private fun Chart?.isShort(): Boolean =
    this is Chart.Split || this is Chart.Stages || this is Chart.Rail

/**
 * The tile's own heading.
 *
 * The full name and the window appear together once the card is wide
 * enough, exactly as `TileHead` does it: of name, window and chip, the
 * window is the one the screen behind the tile repeats in full, so it goes
 * in before the long name does.
 */
@Composable
private fun Head(
    face: Face,
    size: WidgetSize,
    colour: TileColour,
    tint: androidx.glance.unit.ColorProvider,
    colours: WidgetColours,
    showTrends: Boolean,
    wide: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Kicker(
            if (wide) face.longKicker ?: face.kicker else face.kicker,
            tint,
            size = KICKER,
            caps = false,
        )
        if (wide) {
            // One run beginning with a space, as `TileHead` writes it: a
            // 4dp spacer put the separator a hair further out than the tab
            // does, which on two cards side by side is a wobble.
            face.window?.let { Sub(" · $it", colours.faint, size = KICKER) }
        }
        if (showTrends && face.chip != null) {
            Spacer(GlanceModifier.defaultWeight())
            // Tight at Square, which is the tab's M; Wide and Tall are its
            // L and XL, where `ChangeChip` is the roomier of its two.
            Chip(chipAt(face.chip, size), tint, colours.tileWash(colour), tight = !wide)
        }
    }
}

/**
 * The chip's words at this width, by `ChangeChip`'s own rule.
 *
 * At M the unit goes and the figure stays: the head already names what is
 * being measured and the value under it carries the unit, so "↑ 2" is the
 * only part of "↑ 2 bpm" that is not already on the card. A chip whose
 * last word holds a digit -- sleep's "↑ 1h 20m" -- has no unit to drop.
 */
internal fun chipAt(chip: String, size: WidgetSize): String {
    if (size != WidgetSize.Square) return chip
    val last = chip.substringAfterLast(' ', "")
    val isUnit = last.isNotEmpty() && last.none { it.isDigit() }
    return if (isUnit && chip.count { it == ' ' } >= 2) chip.substringBeforeLast(' ') else chip
}

/**
 * A tile built round its goal ring, as `StepsTile` builds one.
 *
 * At Wide the ring stands beside the head, the week and the summary, which
 * is the tile's L. At Tall the ring sits with the head and the figure on
 * one line and the week takes everything under them, which is its XL.
 */
@Composable
private fun RingFace(
    face: Face,
    size: WidgetSize,
    colour: TileColour,
    tint: androidx.glance.unit.ColorProvider,
    colours: WidgetColours,
    showTrends: Boolean,
) {
    val progress = face.ring ?: return
    val side = if (size == WidgetSize.Tall) 72.dp else 64.dp
    val ring = @Composable {
        Box(contentAlignment = Alignment.Center) {
            RingImage(listOf(progress to colour), colours, side, stroke = RING_STROKE)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                face.ringLabel?.let { Figure(it, WidgetText.FigureXS, colours.onBackground, weight = FontWeight.Bold) }
                // The count under the share, as the tile's L has it. At XL
                // the tile drops it -- the figure beside the ring says it.
                if (size == WidgetSize.Wide) {
                    face.ringSub?.let { Sub(it, tint, size = WidgetText.Label, weight = FontWeight.Medium) }
                }
            }
        }
    }
    if (size == WidgetSize.Wide) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ring()
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Head(face, size, colour, tint, colours, showTrends, wide = true)
                Gap(4.dp)
                DrawChart(
                    face.chart, colour, colours,
                    height = (innerHeight(TILE_PAD_V).value - RING_WIDE_ABOVE_CHART)
                        .coerceIn(28f, 90f).dp,
                    width = (innerWidth(TILE_PAD_H).value - side.value - 10f).dp,
                    scaled = size.scaled(),
                )
                face.detail?.let {
                    Gap(3.dp)
                    Sub(it, colours.muted, size = SUB, weight = FontWeight.Medium)
                }
            }
        }
        return
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ring()
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Head(face, size, colour, tint, colours, showTrends, wide = true)
            FigureAndUnit(face, FIGURE, colours)
            Gap(2.dp)
            face.detail?.let { Sub(it, colours.muted, size = SUB, weight = FontWeight.Medium) }
        }
    }
    Gap(8.dp)
    DrawChart(
        face.chart, colour, colours,
        height = (innerHeight(TILE_PAD_V).value - side.value - 20f).coerceIn(40f, 260f).dp,
        width = innerWidth(TILE_PAD_H),
        scaled = size.scaled(),
    )
}

/**
 * One arc, so it can be thicker than the three the Rings family stacks.
 *
 * `GoalRing` on the tab is a heavier stroke than a third of a bullseye
 * needs, and at 64dp the default came out as a wire.
 */
private const val RING_STROKE = 0.135f

/** The head and the summary, either side of a ring tile's week. */
private const val RING_WIDE_ABOVE_CHART = 46f

/**
 * The sub, or the filled pill that stands where the sub would.
 *
 * `TileChip` is a pill at fourteen per cent, a step paler than the change
 * chip in the head -- the tab draws the two at different strengths and so
 * does this.
 */
@Composable
private fun Badged(
    face: Face,
    tint: androidx.glance.unit.ColorProvider,
    colours: WidgetColours,
    colour: TileColour,
) {
    face.badge?.let {
        Chip(it, tint, colours.tileChip(colour), tight = true)
        return
    }
    if (face.sub.isEmpty()) return
    Sub(face.sub, colours.muted, size = SUB, weight = FontWeight.Medium)
}

/**
 * The figure and its unit, as `TileValue` sets them.
 *
 * The unit is labelMedium against the figure's bottom whatever the figure
 * is -- it does not scale with it, because on the tab it never did.
 */
@Composable
private fun FigureAndUnit(
    face: Face,
    size: Int,
    colours: WidgetColours,
    /** True where two columns cannot hold the long figure. */
    short: Boolean = false,
) {
    val shown = if (short) face.shortFigure ?: face.figure else face.figure
    // The unit goes with the long figure. `EnergyTile` writes "-1.5k" bare
    // at S, M and W and "-1,533 kcal" at L and XL, because two columns
    // that cannot hold the number cannot hold its unit either.
    val abbreviated = short && face.shortFigure != null
    if (face.unit == null || abbreviated) {
        Figure(shown, size, colours.onBackground, weight = FontWeight.Bold)
        return
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Figure(shown, size, colours.onBackground, weight = FontWeight.Bold)
        Spacer(GlanceModifier.width(4.dp))
        Sub(face.unit, colours.muted, size = KICKER)
    }
}

@Composable
private fun DrawChart(
    chart: Chart?,
    colour: TileColour,
    colours: WidgetColours,
    height: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    /** Null below Wide, where the tab draws the bare shape too. */
    scaled: Boolean = false,
) {
    when (chart) {
        null -> Unit
        is Chart.Bars -> BarsImage(chart, colour, colours, height, width, scaled)
        is Chart.Stages -> StripeImage(
            chart.parts.map { (share, stage) -> share to colours.sleepStageArgb(stage, colours.darkNow()) },
            height.value.coerceAtMost(STRIPE_MAX).dp,
            width,
        )
        is Chart.Split -> StripeImage(
            chart.parts.map { (share, each) -> share to colours.tileArgb(each, colours.darkNow()) },
            height.value.coerceAtMost(STRIPE_MAX).dp,
            width,
        )
        is Chart.StageWeek -> StageWeekImage(chart, colours, height, width, scaled)

        is Chart.Rails -> RailsImage(chart, colours, height, width)

        is Chart.Climb -> ClimbImage(chart, colours, height, width, scaled)

        is Chart.Beats -> BeatsImage(chart, colour, colours, height, width, scaled)

        is Chart.Scatter -> ScatterImage(chart, colour, colours, height, width)

        is Chart.Arcs -> RingImage(chart.parts, colours, height)

        // Only the thumbnail draws this; the widget lays its own
        // dials out, because it has room for their labels.
        is Chart.Dials -> RingImage(
            chart.parts.map { it.fraction to it.colour }, colours, height,
        )

        // The Paper widget lays its own rail out, with room for hairlines
        // the thumbnail has no space for. This is the fallback shape.
        is Chart.Rail -> Row(modifier = GlanceModifier.fillMaxWidth()) {
            chart.parts.forEach { (name, value, each) ->
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Sub(name, colours.muted, size = WidgetText.Caption)
                    Gap(3.dp)
                    Sub(value, colours.tile(each), size = WidgetText.Body)
                }
            }
        }

        is Chart.Rows -> Column(modifier = GlanceModifier.fillMaxWidth()) {
            chart.rows.forEach { (name, value, each) ->
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Sub(name, colours.muted, size = WidgetText.Label)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(value, colours.tile(each), size = WidgetText.Body)
                }
                Gap(5.dp)
            }
        }
    }
}

// ------------------------------------------------------------- the reading

/**
 * What a tile says, as the tile itself says it.
 *
 * The head's name and the full title both come from the kind here rather
 * than from each branch, so the rule the tab uses -- short where the card
 * is narrow, full where it is wide -- cannot drift one kind at a time.
 */
internal fun faceFor(kind: TileKind, snapshot: WidgetSnapshot): Face? =
    bodyFor(kind, snapshot)?.copy(kicker = kind.short, longKicker = kind.title)

private fun bodyFor(kind: TileKind, snapshot: WidgetSnapshot): Face? {
    val health = snapshot.health
    val data = health.snapshot
    return when (kind) {
        TileKind.Heart -> health.restingTrend?.let { trend ->
            Face(
                kicker = "",
                window = "$WIDGET_WINDOW_DAYS days",
                figure = trend.latest.roundToInt().toString(),
                unit = "bpm",
                sub = usualBand(trend, 0),
                // The whole month, as the tile plots it.
                chart = scatterOf(trend, WIDGET_WINDOW_DAYS),
                chip = chipOf(trend, "bpm"),
            )
        }

        TileKind.Zones -> data.heartToday.takeIf { it.isNotEmpty() }?.let { samples ->
            val latest = samples.maxByOrNull { it.atMillis }?.bpm ?: return@let null
            val low = samples.minOf { it.bpm }
            val high = samples.maxOf { it.bpm }
            Face(
                kicker = "",
                window = "today",
                figure = latest.toString(),
                unit = "bpm",
                sub = "$low–$high today",
                chart = Chart.Beats(samples),
            )
        }

        TileKind.Sleep -> health.lastNight?.let { night ->
            val score = health.sleepScore?.total
            Face(
                kicker = "",
                window = "${health.nights.size} nights",
                figure = night.asleep.asHoursMinutes(),
                // The tile's sub is how well the night went, not its score:
                // the score is the big figure at the other end of the line.
                sub = night.efficiency
                    ?.let { "${(it * 100).roundToInt()}% efficient" }
                    ?: "no efficiency yet",
                trailingFigure = score?.toString(),
                trailingLabel = if (score != null) "score" else null,
                detail = "woke " + night.end.atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) },
                chart = health.nights.takeLast(7)
                    .map { it.stageParts() }
                    .takeIf { week -> week.any { it.isNotEmpty() } }
                    ?.let {
                        Chart.StageWeek(
                            it,
                            snapshot.goals.sleepMinutes.toFloat(),
                            weekdayTicks(health.nights.takeLast(7).map { night -> night.epochDay() }),
                        )
                    }
                    ?: night.stageChart(),
                chip = health.sleepTrend?.let { chipOf(it, "min") },
            )
        }

        TileKind.Steps -> {
            val goal = snapshot.goals.steps
            val percent = if (goal > 0) (health.stepProgress(goal) * 100).roundToInt() else 0
            // The tile's own summary line, word for word, for the widths
            // that draw a ring. "5,707 to go" was the widget's own idea.
            val week = data.steps.takeLast(7)
            val met = week.count { it.value >= goal }
            val average = week.map { it.value }.average().takeIf { week.isNotEmpty() }
            val summary = buildString {
                average?.let { append("avg %,d".format(it.roundToInt())) }
                if (average != null) append(" · ")
                append(
                    when (met) {
                        0 -> "goal not met"
                        1 -> "goal met once"
                        else -> "goal met $met days"
                    },
                )
            }
            Face(
                kicker = "",
                window = "7 days",
                figure = "%,d".format(health.stepsToday),
                // The tab's own line: a share of the goal, not the remainder.
                sub = if (goal > 0) "$percent%% of %,d".format(goal) else "today",
                detail = summary,
                chart = Chart.Bars(
                    data.steps.tail(7),
                    goal = goal.takeIf { it > 0 }?.toFloat(),
                    goalLabel = goal.takeIf { it > 0 }?.let { shortFigure(it.toDouble()) },
                    xTicks = weekdayTicks(week.map { it.day }),
                    yLabel = { shortFigure(it.toDouble()) },
                ),
                // A ring where the tile draws one, and nothing where it
                // draws nothing: the tab has no meter on a steps tile at
                // any size, and the widget's bar was a shape of its own.
                ring = if (goal > 0) health.stepProgress(goal) else null,
                ringLabel = if (goal > 0) "$percent%" else null,
                ringSub = "%,d".format(health.stepsToday),
                // A share, not a count of steps. "318 fewer steps" means
                // nothing without a usual day, which is why the tile gives
                // the drift as a percentage -- and why the tile said
                // "Steady" where the widget said "↓ 6".
                chip = health.stepsTrend
                    ?.let { if (it.average > 0) it.change / it.average * 100 else 0.0 }
                    ?.let { chipOfChange(it, "%", decimals = 0) },
            )
        }

        TileKind.Energy -> {
            val balance = health.energyBalance ?: return null
            val eaten = data.eatenToday ?: 0.0
            val burned = data.burnedToday ?: 0.0
            Face(
                kicker = "",
                figure = signed(balance.roundToInt()),
                shortFigure = shortSigned(balance),
                unit = "kcal",
                // The tab puts the word in a filled pill and nothing in the
                // sub; "under" in grey text was the same claim drawn as a
                // different thing.
                sub = "",
                badge = if (balance < 0) "Deficit" else "Surplus",
                detail = "%,d in · %,d out".format(eaten.roundToInt(), burned.roundToInt()),
                chart = Chart.Split(
                    listOf(
                        eaten.toFloat().coerceAtLeast(1f) to TileColour.Hrv,
                        burned.toFloat().coerceAtLeast(1f) to TileColour.Energy,
                    ),
                ),
                chip = health.energyTrend?.let { chipOf(it, "kcal", decimals = 0) },
            )
        }

        TileKind.Activity -> {
            val sessions = data.sessions
            // The tile totals the sessions themselves rather than a daily
            // series, so the two agreed on the week and disagreed on today.
            val minutes = sessions.sumOf {
                java.time.Duration.between(it.start, it.end).toMinutes()
            }
            Face(
                kicker = "",
                window = "7 days",
                figure = minutes.toString(),
                unit = "min",
                sub = sessionNote(sessions.size),
                chart = Chart.Bars(
                    sessions.map {
                        java.time.Duration.between(it.start, it.end).toMinutes().toFloat()
                    }.takeLast(7),
                    highlightLast = false,
                    yLabel = { it.roundToInt().toString() },
                ),
                chip = health.activityTrend?.let { chipOf(it, "min", decimals = 0) },
            )
        }

        TileKind.Vitals -> {
            // Five rails in the order the Vitals screen lists them, so a
            // metric nothing writes keeps its empty place rather than
            // shifting the rest up -- `VitalsTile`'s own rule.
            val rows = listOf(
                health.restingTrend to TileColour.Heart,
                health.hrvTrend to TileColour.Hrv,
                health.breathingTrend to TileColour.Activity,
                health.skinTrend to TileColour.Energy,
                health.oxygenTrend to TileColour.Slate,
            )
            if (rows.none { it.first != null }) return null
            val inRange = health.vitals.count { it.latestIsUsual }
            Face(
                kicker = "",
                // The tile's own sentence: how many of them are where they
                // usually are, which is the only figure Vitals has.
                figure = inRange.toString(),
                unit = "of ${health.vitals.size}",
                sub = "in range",
                chart = Chart.Rails(rows),
            )
        }

        TileKind.Hrv -> health.hrvTrend?.let { trend ->
            Face(
                kicker = "",
                window = "14 nights",
                figure = trend.latest.roundToInt().toString(),
                unit = "ms",
                sub = usualBand(trend, 0),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "ms"),
            )
        }

        TileKind.Balance -> {
            val day = balanceToday(data)
            if (!day.anything) return null
            Face(
                kicker = "",
                window = "today",
                figure = signed(day.gap.roundToInt()),
                unit = "kcal",
                sub = if (day.gap < 0) "behind, so far today" else "ahead, so far today",
                detail = "%,d in · %,d out".format(day.lastEaten, day.lastBurned),
                // Two climbs on one axis, which is the whole reading: the
                // gap between them is what the figure above says in words.
                chart = Chart.Climb(
                    day.burned,
                    day.eaten,
                    TileColour.Activity,
                    TileColour.Energy,
                    day.hourTicks,
                ),
            )
        }

        TileKind.Nutrition -> {
            val eaten = data.eatenToday ?: return null
            val protein = data.proteinToday ?: 0.0
            val carbs = data.carbsToday ?: 0.0
            val fat = data.fatToday ?: 0.0
            Face(
                kicker = "",
                figure = "%,d".format(eaten.roundToInt()),
                unit = "kcal",
                // The tile's own line. Meals were the widget's own idea of
                // what a food tile says, and the two never agreed.
                sub = when {
                    data.proteinToday == null -> "no macros logged"
                    snapshot.goals.proteinGrams != null ->
                        "${protein.roundToInt()} of ${snapshot.goals.proteinGrams} g protein"
                    else -> "${protein.roundToInt()} g protein"
                },
                detail = "P ${protein.roundToInt()} · C ${carbs.roundToInt()} · F ${fat.roundToInt()}",
                chart = if (protein + carbs + fat <= 0) Chart.Bars(data.eatenDaily.tail(7)) else Chart.Split(
                    listOf(
                        protein.toFloat() to TileColour.Activity,
                        carbs.toFloat() to TileColour.Hrv,
                        fat.toFloat() to TileColour.Heart,
                    ),
                ),
                chip = health.nutritionTrend?.let { chipOf(it, "kcal", decimals = 0) },
            )
        }

        TileKind.Weight -> health.weightTrend?.let { trend ->
            Face(
                kicker = "",
                window = "$WIDGET_WINDOW_DAYS days",
                figure = "%.1f".format(trend.latest),
                unit = "kg",
                sub = usualBand(trend, 1),
                chart = scatterOf(trend, WIDGET_WINDOW_DAYS),
                chip = chipOf(trend, "kg", decimals = 1),
            )
        }

        TileKind.Breathing -> health.breathingTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 nights",
                figure = "%.1f".format(trend.latest),
                unit = "br/min",
                sub = usualBand(trend, 1),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "", decimals = 1),
            )
        }

        TileKind.Skin -> health.skinTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 nights",
                figure = signedDecimal(trend.latest),
                unit = "°C",
                // A band either side of zero: a dash between two signed
                // numbers reads as a subtraction, so it is spelled out.
                sub = usualBand(trend, 1, signed = true),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "", decimals = 1),
            )
        }

        TileKind.Oxygen -> health.oxygenTrend?.let { trend ->
            Face(
                kicker = "",
                window = "30 nights",
                figure = trend.latest.roundToInt().toString(),
                unit = "%",
                sub = usualBand(trend, 0),
                chart = scatterOf(trend, 14),
                chip = chipOf(trend, "", decimals = 0),
            )
        }
    }
}

/** The tile's own scatter: the newest [count] readings, and the usual band. */
private fun scatterOf(trend: Trend, count: Int): Chart.Scatter = Chart.Scatter(
    values = trend.readings.takeLast(count).map { it.value },
    low = trend.usual.low,
    high = trend.usual.high,
)

/**
 * "usual 54-59", at the decimals that metric is worth.
 *
 * Where a bound is negative the dash is spelled out, because a hyphen
 * between two signed numbers reads as a subtraction: "-0.2--0.4".
 */
private fun usualBand(trend: Trend, decimals: Int, signed: Boolean = false): String {
    val from = bound(trend.usual.low, decimals, signed)
    val to = bound(trend.usual.high, decimals, signed)
    val join = if (trend.usual.low < 0 || trend.usual.high < 0) " to " else "–"
    return "usual $from$join$to"
}

private fun bound(value: Double, decimals: Int, signed: Boolean): String {
    val text = if (decimals == 0) {
        value.roundToInt().toString()
    } else {
        "%.${decimals}f".format(value)
    }
    val typographic = text.replace("-", "−")
    return if (signed && value > 0) "+$typographic" else typographic
}

// ------------------------------------------------------------------ pieces

private const val STRIPE_MAX = 14f

/**
 * The tab's own tile padding, from `TileFrame`.
 *
 * Twelve across and ten down rather than eleven both ways: the difference
 * is small on one card and is the reason a widget's kicker did not line up
 * with the tile it was copying.
 */
private val TILE_PAD_H = 12.dp
private val TILE_PAD_V = 10.dp

/** labelMedium, which is what `TileHead` and `TileValue`'s unit are. */
private const val KICKER = 11

/** labelSmall, which is what `TileSub` is. */
private const val SUB = 11

/**
 * headlineSmall, and displaySmall, which in this app are both 26sp.
 *
 * So the tab draws the same figure at S as at XL, and a widget that grew
 * its hero to 32 was the one card in either place with a different number
 * on it.
 */
private const val FIGURE = 26

/**
 * What stands above each chart, which is what is left for the chart itself.
 *
 * Measured against the declared cell rather than the one a launcher
 * happens to give, because the declared cell is the smallest it can be.
 * The head is the chip's height, not the kicker's -- a `ChangeChip` is
 * taller than the name beside it -- and all three went up when the type
 * came into line with the tab: a kicker at 11 rather than 9, a sub at 11
 * rather than 10, and a chip with the tab's padding under it.
 */
private const val SQUARE_ABOVE_CHART = 83f

/** The head and its gap: the rest of a wide card is the chart's row. */
private const val WIDE_ABOVE_CHART = 30f

/**
 * The head, the figure and its two lines, above a tall card's chart.
 *
 * Generous rather than exact: a Column that overruns its card does not
 * scroll or shrink, it loses its last child -- which on the sleep week was
 * the footer, so the chart kept its bars and lost the days they were.
 */
private const val TALL_ABOVE_CHART = 132f
private val FIGURE_COLUMN = 96.dp

/**
 * A weekday letter under each bar, evenly spaced.
 *
 * The tab works these out from the days themselves rather than counting
 * back from today, because a week with a gap in it is still seven bars and
 * the letters have to name the days that are actually drawn.
 *
 * [days] are epoch *days*, which is what `Reading.day` holds -- reading
 * them as milliseconds put every bar on 1 January 1970 and lettered the
 * whole week T.
 */
private fun weekdayTicks(days: List<Long>): List<Pair<Float, String>> {
    if (days.size < 2) return emptyList()
    val locale = java.util.Locale.getDefault()
    return days.mapIndexed { index, day ->
        val at = index / (days.size - 1).toFloat()
        at to java.time.LocalDate.ofEpochDay(day).dayOfWeek
            .getDisplayName(java.time.format.TextStyle.NARROW, locale)
    }
}

/** The night's own day, in the same epoch days the readings use. */
private fun Night.epochDay(): Long =
    end.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

/**
 * Today's burn and today's food, each as a running total up to this hour.
 *
 * The Burned-against-eaten screen's own arithmetic, in one place because
 * three things draw it now -- the Balance tile, the Balance widget and the
 * Dials burn face -- and three copies of a running total is three chances
 * for one of them to drift an hour away from the others.
 *
 * Both lines stop at the hour it is now. Carrying burn out to midnight
 * would be a forecast, and none of these has any business making one.
 */
internal class DayBalance(
    val burned: List<Float>,
    val eaten: List<Float>,
    val entries: Int,
    val hourNow: Int,
) {
    val lastBurned: Int get() = (burned.lastOrNull() ?: 0f).roundToInt()
    val lastEaten: Int get() = (eaten.lastOrNull() ?: 0f).roundToInt()

    /** Eaten less burned: negative is behind, which is the usual way round. */
    val gap: Float get() = (eaten.lastOrNull() ?: 0f) - (burned.lastOrNull() ?: 0f)

    val anything: Boolean get() = lastBurned > 0 || lastEaten > 0

    /**
     * The screen's axis: a few hours the day has reached, and then now.
     *
     * Fixed marks at six, twelve and eighteen were the old answer, and at
     * nine in the morning that is one label on a chart with nothing under
     * the end of it.
     */
    val hourTicks: List<Pair<Float, String>>
        get() {
            if (hourNow < 1) return listOf(0f to "00")
            val every = (hourNow / 4).coerceAtLeast(1)
            val hours = (0..hourNow step every).toMutableList()
            if (hours.last() != hourNow) hours += hourNow
            return hours.map { it / hourNow.toFloat() to "%02d".format(it) }
        }
}

internal fun balanceToday(data: HealthSnapshot): DayBalance {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone).toEpochDay()
    val hourNow = java.time.LocalTime.now(zone).hour.coerceIn(0, 23)
    // Food arrives as entries at a time rather than as an hourly total, so
    // the eaten line is built from the entries, as the screen builds it.
    val eatenByHour = DoubleArray(24)
    val todays = data.food.filter { it.day == today }
    todays.forEach { eatenByHour[it.at.atZone(zone).hour] += it.kcal ?: 0.0 }
    val burnedByHour = data.burnedHourly.takeIf { it.size == 24 } ?: List(24) { 0.0 }

    fun running(hours: List<Double>): List<Float> {
        var sum = 0.0
        return (0..hourNow).map { hour ->
            sum += hours.getOrElse(hour) { 0.0 }
            sum.toFloat()
        }
    }
    return DayBalance(
        burned = running(burnedByHour),
        eaten = running(eatenByHour.toList()),
        entries = todays.size,
        hourNow = hourNow,
    )
}

/** "7.5k", the way the tab shortens a figure for an axis. */
internal fun shortFigure(value: Double): String = when {
    value >= 10_000 -> "${(value / 1000).roundToInt()}k"
    value >= 1_000 -> "%.1fk".format(value / 1000).removeSuffix(".0k").let {
        if (it.endsWith("k")) it else it + "k"
    }
    else -> value.roundToInt().toString()
}

private fun List<Reading>.tail(count: Int): List<Float> =
    takeLast(count).map { it.value.toFloat() }

/**
 * Today's beats, one column an hour, each the highest reading in it.
 *
 * The tab draws a line through every sample; rectangles in a Row cannot
 * make a line, so the widget bins instead. The highest rather than the mean,
 * because the point of the shape is where the day asked something of you.
 */
private fun List<com.srineere.airbridge.health.HeartSample>.byHour(): List<Float> {
    if (isEmpty()) return emptyList()
    val calendar = java.util.Calendar.getInstance()
    val byHour = groupBy {
        calendar.timeInMillis = it.atMillis
        calendar.get(java.util.Calendar.HOUR_OF_DAY)
    }
    // The whole day, always: twenty-four slots, most of them empty at
    // breakfast. Binning only the hours that hold a reading drew three
    // columns the width of a thumb at two in the morning, which says nothing
    // about a day. Three thin columns at the left of twenty-four says it is
    // early, which is the true thing about that chart.
    return (0..23).map { hour -> byHour[hour]?.maxOf { it.bpm }?.toFloat() ?: 0f }
}

/**
 * One night as its four stages, bottom up, as `fourStages` divides it.
 *
 * A night written without a hypnogram is one plain bar rather than an
 * invented breakdown -- the tab's rule, kept here so a week of widget bars
 * and a week of tile bars are the same week.
 */
private fun Night.stageParts(): List<Pair<Float, SleepStage>> {
    if (!hasStages) {
        return listOf(asleep.toMinutes().toFloat() to SleepStage.Light).filter { it.first > 0f }
    }
    return listOf(
        deep.toMinutes().toFloat() to SleepStage.Deep,
        light.toMinutes().toFloat() to SleepStage.Light,
        rem.toMinutes().toFloat() to SleepStage.Rem,
        awake.toMinutes().toFloat() to SleepStage.Awake,
    ).filter { it.first > 0f }
}

/** Last night in proportion, or null for a night written as one block. */
private fun Night.stageChart(): Chart? {
    if (segments.isEmpty()) {
        // No hypnogram: the three totals still say what the night was made
        // of, which is more than one undifferentiated bar would.
        val parts = listOf(
            deep.toMinutes().toFloat() to SleepStage.Deep,
            light.toMinutes().toFloat() to SleepStage.Light,
            rem.toMinutes().toFloat() to SleepStage.Rem,
            awake.toMinutes().toFloat() to SleepStage.Awake,
        ).filter { it.first > 0f }
        return parts.takeIf { it.isNotEmpty() }?.let { Chart.Stages(it) }
    }
    // Sixty offset boxes would compose and would be a grey smear under
    // 150dp, so the night is reduced to its longest stretches.
    return Chart.Stages(
        segments.take(MAX_SEGMENTS).map { it.length.toMinutes().toFloat().coerceAtLeast(1f) to it.stage },
    )
}

private const val MAX_SEGMENTS = 14

private fun sessionNote(count: Int): String = when (count) {
    0 -> "no sessions"
    1 -> "1 session"
    else -> "$count sessions"
}

private fun mealNote(count: Int): String = when (count) {
    0 -> "nothing logged"
    1 -> "1 meal"
    else -> "$count meals"
}

internal fun signed(value: Int): String =
    if (value < 0) "−${abs(value)}" else "+$value"

private fun signedDecimal(value: Double): String =
    if (value < 0) "−%.1f".format(abs(value)) else "+%.1f".format(value)

/**
 * The change chip, in the metric's own units.
 *
 * [Trend.change] is the oldest third against the newest, as a difference and
 * not a percentage, which is what the tab's chip shows too. Below the point
 * where it would round away it says Steady rather than an arrow pointing at
 * nothing.
 */
private fun chipOf(trend: Trend, unit: String, decimals: Int = 0): String =
    chipOfChange(trend.change, unit, decimals)

/** The same chip, where the change is worked out rather than read off. */
private fun chipOfChange(change: Double, unit: String, decimals: Int = 0): String {
    val shown = if (decimals == 0) abs(change).roundToInt().toString() else "%.${decimals}f".format(abs(change))
    if (shown.toDoubleOrNull()?.takeIf { it > 0.0 } == null) return "Steady"
    val arrow = if (change > 0) "↑" else "↓"
    return if (unit.isEmpty()) "$arrow $shown" else "$arrow $shown $unit"
}

/** Energy's figure where the card is narrow, as `EnergyTile` shortens it. */
private fun shortSigned(balance: Double): String {
    val sign = if (balance < 0) "−" else "+"
    if (abs(balance) < 1000) return sign + abs(balance).roundToInt().toString()
    return sign + "%.1fk".format(abs(balance) / 1000)
}

/** The direction on its own, for the width that cannot hold the figure. */
private fun arrowOf(chip: String): String = chip.take(1).takeIf { it == "↑" || it == "↓" } ?: ""

/**
 * The tile's scatter, drawn to a bitmap and shown as an Image.
 *
 * Sized from the cell rather than a constant, and keyed on everything that
 * changes a pixel so scrolling past a widget does not redraw a Canvas that
 * would come out identical.
 */
@Composable
private fun ScatterImage(
    chart: Chart.Scatter,
    colour: TileColour,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val argb = colours.tileArgb(colour, colours.isDark(context))
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val bitmap = remember(widthPx, heightPx, chart.values, chart.low, chart.high, argb) {
        ScatterBitmap.draw(widthPx, heightPx, chart.values, chart.low, chart.high, argb, density)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/** Which half of the palette a bitmap should be painted from. */
@Composable
internal fun WidgetColours.darkNow(): Boolean = isDark(LocalContext.current)

/**
 * A bar chart as one Image.
 *
 * Not as a Row of Boxes: Glance containers hold ten children and quietly
 * drop the rest, so a week came out as five days and a day as five hours.
 */
@Composable
private fun BarsImage(
    chart: Chart.Bars,
    colour: TileColour,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
    scaled: Boolean = false,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val ink = colours.tileArgb(colour, dark)
    val dim = colours.tileDimArgb(colour, dark)
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val scale = scaleOf(chart, colours, dark, scaled)
    val bitmap = remember(widthPx, heightPx, chart.values, chart.ceiling, ink, dim, scale) {
        ChartBitmap.bars(
            widthPx, heightPx, chart.values, chart.ceiling, ink, dim,
            chart.highlightLast, chart.blankZeros, density, scale,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/**
 * The scale a chart carries, or null where the card is too small.
 *
 * Built outside `remember` and keyed into it, because a Scale holds a
 * lambda and two colours and would otherwise be a new object every draw --
 * which would make the key change every time and redraw a bitmap that came
 * out identical.
 */
private fun scaleOf(
    chart: Chart.Bars,
    colours: WidgetColours,
    dark: Boolean,
    scaled: Boolean,
): Scale? {
    if (!scaled) return null
    if (chart.goal == null && chart.xTicks.isEmpty() && chart.yLabel == null) return null
    return Scale(
        ink = colours.faintArgb(dark),
        line = colours.trackArgb(dark),
        xTicks = chart.xTicks,
        yLabel = chart.yLabel,
        goal = chart.goal,
        goalLabel = chart.goalLabel,
    )
}

/** A week of nights, stacked, with the goal across them. */
@Composable
private fun StageWeekImage(
    chart: Chart.StageWeek,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
    scaled: Boolean = false,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    // Deep at the floor and awake on top, which is the order the tab
    // stacks them in and the order they read in.
    val bars = chart.nights.map { night ->
        night.map { (minutes, stage) -> minutes to colours.sleepStageArgb(stage, dark) }
    }
    val goalInk = colours.trackArgb(dark)
    // Hours, as the tile labels them: minutes in the gutter would be a
    // four-digit number against a tile that says "8h".
    val scale = if (!scaled) null else Scale(
        ink = colours.faintArgb(dark),
        line = goalInk,
        xTicks = chart.xTicks,
        yLabel = { "${(it / 60f).roundToInt()}h" },
    )
    val bitmap = remember(widthPx, heightPx, bars, chart.goalMinutes, goalInk, scale) {
        ChartBitmap.stacked(widthPx, heightPx, bars, chart.goalMinutes, goalInk, density, scale)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/** Two climbs on one axis, as one Image. */
@Composable
private fun ClimbImage(
    chart: Chart.Climb,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
    scaled: Boolean,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val first = colours.tileArgb(chart.firstColour, dark)
    val second = colours.tileArgb(chart.secondColour, dark)
    val scale = if (!scaled) null else Scale(
        ink = colours.faintArgb(dark),
        line = colours.trackArgb(dark),
        xTicks = chart.ticks,
        yLabel = { shortFigure(it.toDouble()) },
    )
    val bitmap = remember(widthPx, heightPx, chart.first, chart.second, first, second, scale) {
        ChartBitmap.pair(widthPx, heightPx, chart.first, chart.second, first, second, density, scale)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/** The tile's rails, as one Image. */
@Composable
internal fun RailsImage(
    chart: Chart.Rails,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val widthPx = (width.value * density).toInt()
    // Five rails and the gaps between them, at the tile's proportions, so
    // a rail is a rail rather than a stripe however tall the cell is.
    val heightPx = (height.value.coerceAtMost(RAILS_MAX) * density).toInt()
    val quads = chart.rails.map { (trend, colour) ->
        trend?.let {
            val ink = colours.tileArgb(colour, dark)
            val span = (it.highest - it.lowest).takeIf { range -> range > 0.0 } ?: 1.0
            fun at(value: Double): Float = ((value - it.lowest) / span).toFloat().coerceIn(0f, 1f)
            Quad(
                at = at(it.latest),
                bandFrom = at(it.usual.low),
                bandTo = at(it.usual.high),
                dot = ink,
                // The band at the tab's thirty per cent, mixed rather than
                // made transparent, as everything on a wallpaper must be.
                band = colours.tileBandArgb(colour, dark),
            )
        }
    }
    val track = colours.railTrackArgb(dark)
    val bitmap = remember(widthPx, heightPx, quads.size, track, chart.rails) {
        ChartBitmap.rails(widthPx, heightPx, quads, track, density)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height.coerceAtMost(RAILS_MAX.dp)),
    )
}

/** A day of beats as the tile draws it: the range inside each slice. */
@Composable
private fun BeatsImage(
    chart: Chart.Beats,
    colour: TileColour,
    colours: WidgetColours,
    height: Dp,
    width: Dp,
    scaled: Boolean,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val dark = colours.isDark(context)
    val ink = colours.tileDimArgb(colour, dark)
    val newest = colours.tileArgb(colour, dark)
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val spans = beatSpans(chart.samples)
    if (spans.isEmpty()) return
    val lowest = spans.minOf { it.second }
    val highest = spans.maxOf { it.third }
    // A little air either side, and a floor that is not zero: the tile's
    // own plot sits around the day's readings, not under them.
    val floor = (lowest - (highest - lowest) * 0.12f).coerceAtLeast(0f)
    val ceiling = highest + (highest - lowest) * 0.12f
    val scale = if (!scaled) null else Scale(
        ink = colours.faintArgb(dark),
        line = colours.trackArgb(dark),
        xTicks = chart.samples.hourTicks(),
        yLabel = { it.roundToInt().toString() },
        low = floor,
    )
    val bitmap = remember(widthPx, heightPx, spans, ink, newest, scale) {
        ChartBitmap.beats(
            widthPx, heightPx, spans, floor, ceiling, ink, newest, density, scale,
        )
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}

/**
 * The day sliced into eighty, each slice its lowest and highest beat.
 *
 * `bandsOf` on the tab, to the same count, so the widget's chart and the
 * tile's are the same reduction of the same readings. A slice the band was
 * not recording stays out, so a gap is a gap rather than a line through it.
 */
private fun beatSpans(
    samples: List<com.srineere.airbridge.health.HeartSample>,
): List<Triple<Float, Float, Float>> {
    if (samples.size < 2) return emptyList()
    val ordered = samples.sortedBy { it.atMillis }
    val first = ordered.first().atMillis
    val span = (ordered.last().atMillis - first).takeIf { it > 0 } ?: return emptyList()
    val slice = span / BEAT_SLICES
    if (slice <= 0) return emptyList()
    val out = ArrayList<Triple<Float, Float, Float>>(BEAT_SLICES)
    var index = 0
    for (bucket in 0 until BEAT_SLICES) {
        val until = first + slice * (bucket + 1)
        var low = Int.MAX_VALUE
        var high = Int.MIN_VALUE
        while (index < ordered.size && ordered[index].atMillis < until) {
            val bpm = ordered[index].bpm
            if (bpm < low) low = bpm
            if (bpm > high) high = bpm
            index++
        }
        if (high >= low) {
            out += Triple(bucket / (BEAT_SLICES - 1f), low.toFloat(), high.toFloat())
        }
    }
    return out
}

private const val BEAT_SLICES = 80

/** Every fourth hour the day actually spans, as the tile ticks it. */
private fun List<com.srineere.airbridge.health.HeartSample>.hourTicks(): List<Pair<Float, String>> {
    if (size < 2) return emptyList()
    val zone = java.time.ZoneId.systemDefault()
    val first = minOf { it.atMillis }
    val last = maxOf { it.atMillis }
    val span = (last - first).takeIf { it > 0 } ?: return emptyList()
    val from = java.time.Instant.ofEpochMilli(first).atZone(zone).hour
    val to = java.time.Instant.ofEpochMilli(last).atZone(zone).hour
    return (from..to).filter { it % 4 == 1 }.mapNotNull { hour ->
        val at = java.time.Instant.ofEpochMilli(first).atZone(zone)
            .withHour(hour).withMinute(0).toInstant().toEpochMilli()
        ((at - first).toFloat() / span).takeIf { it in 0f..1f }?.let { it to "%02d".format(hour) }
    }
}

internal const val RAILS_MAX = 44f

/** Proportional segments as one Image, for the same reason. */
@Composable
private fun StripeImage(parts: List<Pair<Float, Int>>, height: Dp, width: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()
    val bitmap = remember(widthPx, heightPx, parts) {
        ChartBitmap.stripe(widthPx, heightPx, parts, density)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxWidth().height(height),
    )
}
