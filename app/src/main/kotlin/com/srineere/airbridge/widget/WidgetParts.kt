package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.srineere.airbridge.R

/**
 * The card every widget sits in.
 *
 * Not `GlanceModifier.cornerRadius`, which wants API 31 against this app's
 * 28: the rounding is a tinted shape drawable, which works everywhere and
 * costs one resource.
 */
@Composable
internal fun WidgetCard(
    colours: WidgetColours,
    onClick: Action?,
    padding: Dp = 11.dp,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    /** Apart on the two axes, because the tab's tile is 12 by 10, not 11. */
    horizontal: Dp = padding,
    vertical: Dp = padding,
    /**
     * A hairline round the card, as `TileFrame` draws one.
     *
     * Drawn as a card behind the card rather than as a `<stroke>`: the shape
     * is tinted by a single `ColorFilter`, so a stroke inside it would come
     * out the same colour as the fill and be no edge at all.
     */
    border: ColorProvider? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = GlanceModifier
        .fillMaxSize()
        .background(
            imageProvider = ImageProvider(R.drawable.widget_card),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colours.background),
        )
        .padding(horizontal = horizontal, vertical = vertical)
    val column = @Composable {
        Column(
            modifier = if (onClick != null && border == null) card.clickable(onClick) else card,
            verticalAlignment = verticalAlignment,
            content = content,
        )
    }
    if (border == null) {
        column()
        return
    }
    val edge = GlanceModifier
        .fillMaxSize()
        .background(
            imageProvider = ImageProvider(R.drawable.widget_card),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(border),
        )
        .padding(1.dp)
    Box(modifier = if (onClick != null) edge.clickable(onClick) else edge) { column() }
}

/**
 * The small line that names the reading.
 *
 * [caps] is the Tiles family's exception, and it is the tab's own rule:
 * every other kicker in the app is uppercased, but `TileHead` is not. A
 * tile widget that shouted its name was the first thing you saw was
 * different from the card it was copying. Tracked at nothing either way,
 * because Glance has no letter spacing and the tab's kickers sit at 1sp.
 */
@Composable
internal fun Kicker(
    text: String,
    colour: ColorProvider,
    size: Int = 10,
    caps: Boolean = true,
) {
    Text(
        if (caps) text.uppercase() else text,
        style = TextStyle(color = colour, fontSize = size.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

/**
 * The one number the widget is about.
 *
 * The tab sets its figures SemiBold and Glance has no such weight: Medium
 * and Bold sit a hundred either side of it, so neither is nearer by the
 * numbers. The Tiles family asks for [FontWeight.Bold] because it is the one
 * of the two that renders as a weight rather than as a named family a
 * launcher may not have.
 */
@Composable
internal fun Figure(
    text: String,
    size: Int,
    colour: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    weight: FontWeight = FontWeight.Medium,
) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(color = colour, fontSize = size.sp, fontWeight = weight),
        maxLines = 1,
    )
}

/** The line under it: the unit, the goal, the age of the reading. */
@Composable
internal fun Sub(
    text: String,
    colour: ColorProvider,
    size: Int = 11,
    lines: Int = 1,
    /** The tab's `TileSub` is labelSmall, which is Medium rather than Normal. */
    weight: FontWeight = FontWeight.Normal,
) {
    Text(
        text,
        style = TextStyle(color = colour, fontSize = size.sp, fontWeight = weight),
        maxLines = lines,
    )
}

@Composable
internal fun Gap(height: Dp) = Spacer(GlanceModifier.height(height))

@Composable
internal fun ColumnScope.Grow() = Spacer(GlanceModifier.defaultWeight())

/**
 * The width inside the card, which is what every proportional piece needs.
 *
 * [LocalSize] reports the cell the launcher gave us, so this is recomputed
 * on every resize rather than assumed from the declared size.
 */
@Composable
internal fun innerHeight(padding: Dp): Dp =
    (LocalSize.current.height.value - padding.value * 2).coerceAtLeast(1f).dp

@Composable
internal fun innerWidth(padding: Dp): Dp =
    (LocalSize.current.width.value - padding.value * 2).coerceAtLeast(1f).dp

/**
 * A row of bars, lowest common denominator of every chart here.
 *
 * Heights are computed in dp rather than asked for as a fraction, because
 * Glance weight is an equal share and nothing else: there are no fractional
 * weights and no percentage heights. Equal *widths* are exactly what weight
 * does give, so the widths are weighted and the heights are arithmetic.
 */
@Composable
internal fun Bars(
    values: List<Float>,
    colour: ColorProvider,
    dim: ColorProvider,
    height: Dp,
    highlightLast: Boolean = true,
    gap: Dp = 3.dp,
    blankZeros: Boolean = false,
    /**
     * The value a full-height bar means, when the series has one of its own.
     *
     * A week of sleep scores drawn against its own best is a week where the
     * best night is full and every other looks poor -- including a week
     * where every night scored ninety. Out of a hundred, the week reads as
     * what it was.
     */
    top: Float? = null,
) {
    if (values.isEmpty()) return
    val ceiling = top ?: values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    // A day is twenty-four slots, and twenty-three gaps of three points
    // would be most of the chart. The more columns there are the less room
    // there is to spend between them.
    val between = if (values.size > 12) 1.5.dp else gap
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            if (index > 0) Spacer(GlanceModifier.width(between))
            // An hour with no reading keeps its slot and draws nothing. The
            // slot is the point: three readings on a day that is two hours
            // old should be three narrow bars at the left of a day, not
            // three slabs filling the card.
            if (blankZeros && value <= 0f) {
                // With a height, because a weighted child with no size at
                // all is not measured and collapses: the five hours that
                // held a reading shared the whole chart between them as if
                // the day were five hours long. One transparent pixel is
                // enough to make it a view that takes its share.
                Box(modifier = GlanceModifier.defaultWeight().height(1.dp)) {}
                return@forEachIndexed
            }
            val tall = (height.value * (value / ceiling)).coerceAtLeast(MIN_BAR).dp
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(tall)
                    .background(
                        imageProvider = ImageProvider(R.drawable.widget_bar),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = ColorFilter.tint(
                            if (highlightLast && index == values.lastIndex) colour else dim,
                        ),
                    ),
            ) {}
        }
    }
}

/**
 * Segments in proportion -- sleep stages, a macro split.
 *
 * Widths in dp for the same reason bar heights are: a weight cannot be 3/17
 * of a row. The width comes from [LocalSize], which is the cell the launcher
 * reported, so this is recomputed whenever the widget is resized.
 */
@Composable
internal fun Stripe(
    parts: List<Pair<Float, ColorProvider>>,
    height: Dp,
    width: Dp,
    gap: Dp = 2.dp,
) {
    if (parts.isEmpty()) return
    val total = parts.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: return
    val available = width.value - gap.value * (parts.size - 1)
    Row(modifier = GlanceModifier.fillMaxWidth().height(height)) {
        parts.forEachIndexed { index, (share, colour) ->
            if (index > 0) Spacer(GlanceModifier.width(gap))
            Box(
                modifier = GlanceModifier
                    .width((available * (share / total)).coerceAtLeast(MIN_BAR).dp)
                    .height(height)
                    .background(
                        imageProvider = ImageProvider(R.drawable.widget_bar),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = ColorFilter.tint(colour),
                    ),
            ) {}
        }
    }
}

/**
 * A goal bar.
 *
 * `LinearProgressIndicator` is the one determinate component Glance has --
 * `CircularProgressIndicator` takes a colour and a modifier and no progress
 * at all, so it can only spin. Every ring on the design canvas is therefore
 * a bitmap, and every bar is this.
 */
@Composable
internal fun Meter(
    progress: Float,
    width: Dp,
    colour: ColorProvider,
    track: ColorProvider,
    height: Dp = 5.dp,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .background(
                imageProvider = ImageProvider(R.drawable.widget_pill),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(track),
            ),
    ) {
        val filled = width.value * progress.coerceIn(0f, 1f)
        if (filled > 0.5f) {
            Box(
                modifier = GlanceModifier
                    .width(filled.dp)
                    .height(height)
                    .background(
                        imageProvider = ImageProvider(R.drawable.widget_pill),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = ColorFilter.tint(colour),
                    ),
            ) {}
        }
    }
}

/**
 * A filled pill, which is what the tab's `ChangeChip` is.
 *
 * Coloured words were close enough to read and not close enough to be the
 * same component; side by side on a home screen and a tab the difference is
 * the first thing you see. The measurements are `ChangeChip`'s own, down to
 * [tight]: at S and M it is labelMedium in a six-point pill, and everywhere
 * else labelLarge in a ten-point one.
 */
@Composable
internal fun Chip(
    text: String,
    ink: ColorProvider,
    wash: ColorProvider,
    tight: Boolean = false,
) {
    Box(
        modifier = GlanceModifier.background(
            imageProvider = ImageProvider(R.drawable.widget_pill),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(wash),
        ).padding(
            horizontal = if (tight) 6.dp else 10.dp,
            vertical = if (tight) 2.dp else 3.dp,
        ),
    ) {
        Text(
            text,
            style = TextStyle(
                color = ink,
                fontSize = (if (tight) WidgetText.Label else WidgetText.Body).sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

/** The state dot: relaying, quiet, paused, off. */
@Composable
internal fun Dot(colour: ColorProvider, size: Dp = 8.dp) {
    Box(
        modifier = GlanceModifier
            .width(size)
            .height(size)
            .background(
                imageProvider = ImageProvider(R.drawable.widget_pill),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colour),
            ),
    ) {}
}

/**
 * What a widget says when it has nothing true to say.
 *
 * Never a zero. "0%" on a battery widget reads as a flat band and "0" on a
 * steps widget reads as a day spent sitting down; both are claims this app
 * cannot make. A gap is drawn as a gap, with the reason under it.
 */
@Composable
internal fun Empty(colours: WidgetColours, title: String, reason: String, onClick: Action?) {
    WidgetCard(colours, onClick) {
        Kicker(title, colours.faint)
        Grow()
        Figure("–", WidgetText.FigureL, colours.faint)
        Gap(6.dp)
        Sub(reason, colours.muted, lines = 3)
    }
}

private const val MIN_BAR = 2f
