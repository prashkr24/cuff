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
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = GlanceModifier
        .fillMaxSize()
        .background(
            imageProvider = ImageProvider(R.drawable.widget_card),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(colours.background),
        )
        .padding(padding)
    Column(
        modifier = if (onClick != null) card.clickable(onClick) else card,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** The small capitalised line that names the reading. */
@Composable
internal fun Kicker(text: String, colour: ColorProvider, size: Int = 10) {
    // Uppercased here because Glance has no text transform, and tracked at
    // nothing because it has no letter spacing either -- the tab's kickers
    // sit at 1sp and these cannot be made to match.
    Text(
        text.uppercase(),
        style = TextStyle(color = colour, fontSize = size.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

/** The one number the widget is about. */
@Composable
internal fun Figure(
    text: String,
    size: Int,
    colour: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(color = colour, fontSize = size.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

/** The line under it: the unit, the goal, the age of the reading. */
@Composable
internal fun Sub(text: String, colour: ColorProvider, size: Int = 11, lines: Int = 1) {
    Text(text, style = TextStyle(color = colour, fontSize = size.sp), maxLines = lines)
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
) {
    if (values.isEmpty()) return
    val top = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            if (index > 0) Spacer(GlanceModifier.width(gap))
            val tall = (height.value * (value / top)).coerceAtLeast(MIN_BAR).dp
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
        Figure("–", 26, colours.faint)
        Gap(6.dp)
        Sub(reason, colours.muted, lines = 3)
    }
}

private const val MIN_BAR = 2f
