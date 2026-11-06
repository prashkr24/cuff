package com.srineere.airbridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Band
import com.srineere.airbridge.health.HeartSample
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.Trend
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Every chart here is one Canvas, axis labels included. Compose draws them
 * without laying out a child per data point or per tick, which is what a row
 * of thirty composables would cost, and nothing below allocates inside the
 * draw pass beyond the paths it must.
 *
 * `axes` is optional throughout. Passing it gives the chart a gutter, a
 * baseline and labels; leaving it out gives the bare shape, which is all
 * there is room for in a two-column tile. `fill` is for a chart inside a
 * tile: the tile's height is fixed so the grid stays square, and the chart
 * takes whatever is left under the numbers.
 */

@Composable
private fun ChartCanvas(
    modifier: Modifier,
    height: Dp,
    fill: Boolean,
    draw: DrawScope.() -> Unit,
) = Canvas(
    modifier.fillMaxWidth().then(if (fill) Modifier.fillMaxHeight() else Modifier.height(height)),
    draw,
)


/**
 * The line behind the point a finger has picked.
 *
 * Drawn behind the series, full height, so the thing being read stays on
 * top of the thing marking it. Nothing else changes colour: the selection
 * is where you are looking, not a judgement about that day.
 */
private fun DrawScope.drawMarker(plot: Plot, at: Float, colour: Color) {
    drawLine(
        color = colour.copy(alpha = 0.45f),
        start = Offset(at, 0f),
        end = Offset(at, plot.bottom),
        strokeWidth = 1.5.dp.toPx(),
    )
}

/**
 * A reading a night, as dots, over the band of what is usual, with a line
 * through the middle for the direction.
 *
 * The dots are the point: a metric recorded once a night is a set of nights
 * you can pick out, and a filled area line hides exactly that. The band is
 * what turns a dip into information rather than a wiggle.
 */
@Composable
internal fun ScatterTrend(
    trend: Trend,
    colour: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    showBand: Boolean = true,
    fill: Boolean = false,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    yLabel: (Double) -> String = { it.roundToInt().toString() },
    selected: Int? = null,
) {
    ChartCanvas(modifier, height, fill) {
        val values = trend.readings.map { it.value }
        if (values.isEmpty()) return@ChartCanvas

        // Pad the scale so the extremes are not drawn on the frame itself.
        val span = (trend.highest - trend.lowest).takeIf { it > 0.0 } ?: 1.0
        val low = trend.lowest - span * 0.18
        val high = trend.highest + span * 0.18
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, low, high)
        } else {
            with(axes) {
                frame(
                    low = low,
                    high = high,
                    yTicks = niceTicks(trend.lowest, trend.highest),
                    xTicks = xTicks,
                    yLabel = yLabel,
                )
            }
        }

        if (showBand) {
            val top = plot.y(trend.usual.high)
            drawRect(
                color = colour.copy(alpha = 0.12f),
                topLeft = Offset(plot.left, top),
                size = Size(plot.width, (plot.y(trend.usual.low) - top).coerceAtLeast(1f)),
            )
        }
        selected?.takeIf { it in values.indices }?.let {
            drawMarker(plot, plot.xOf(it, values.size, 2.4.dp.toPx()), colour)
        }
        drawSeries(plot, values, colour, selected)
    }
}

/**
 * Dots, with a line through where they are heading.
 *
 * The line runs through chunk averages rather than through every point: a
 * line joining the dots would say exactly what the dots already say, and
 * hide the direction underneath its own zigzag.
 */
private fun DrawScope.drawSeries(
    plot: Plot,
    values: List<Double>,
    colour: Color,
    selected: Int? = null,
) {
    val radius = 2.4.dp.toPx()
    val step = (values.size / 6).coerceAtLeast(1)
    val smoothed = values.chunked(step).map { it.average() }
    if (smoothed.size > 1) {
        val path = Path()
        smoothed.forEachIndexed { index, value ->
            val x = plot.xOf(index, smoothed.size, radius)
            val y = plot.y(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            colour.copy(alpha = 0.5f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    values.forEachIndexed { index, value ->
        val big = index == values.lastIndex || index == selected
        drawCircle(
            color = if (big) colour else colour.copy(alpha = 0.85f),
            radius = if (big) radius * 1.7f else radius,
            center = Offset(plot.xOf(index, values.size, radius), plot.y(value)),
        )
    }
}

/** A sparkline small enough for a tile: the same dots, no band, no axis. */
@Composable
internal fun MiniScatter(
    readings: List<Reading>,
    usual: Band?,
    colour: Color,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    fill: Boolean = false,
) {
    ChartCanvas(modifier, height, fill) {
        if (readings.isEmpty()) return@ChartCanvas
        val values = readings.map { it.value }
        val lowest = values.min()
        val highest = values.max()
        val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
        val plot = Plot(0f, 0f, size.width, size.height, lowest - span * 0.25, highest + span * 0.25)

        if (usual != null) {
            val top = plot.y(usual.high)
            drawRect(
                color = colour.copy(alpha = 0.12f),
                topLeft = Offset(0f, top),
                size = Size(size.width, (plot.y(usual.low) - top).coerceAtLeast(1f)),
            )
        }
        val radius = 2.2.dp.toPx()
        values.forEachIndexed { index, value ->
            val last = index == values.lastIndex
            drawCircle(
                color = if (last) colour else colour.copy(alpha = 0.8f),
                radius = if (last) radius * 1.5f else radius,
                center = Offset(plot.xOf(index, values.size, radius), plot.y(value)),
            )
        }
    }
}

/**
 * One bar a day, against a goal line when there is one.
 *
 * A day is a discrete total, so bars; the line is the goal, which is the only
 * reason the number has a shape at all. Bars that reach it are drawn in full
 * colour -- the one place on this tab where colour carries a fact, and even
 * here it means "met", not "good".
 */
@Composable
internal fun GoalBars(
    values: List<Double>,
    target: Double?,
    colour: Color,
    dim: Color,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    fill: Boolean = false,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    goalLabel: String? = null,
    highlightLast: Boolean = true,
    yLabel: (Double) -> String = { shortNumber(it) },
    selected: Int? = null,
) {
    ChartCanvas(modifier, height, fill) {
        if (values.isEmpty()) return@ChartCanvas
        val top = maxOf(values.max(), target ?: 0.0).takeIf { it > 0.0 } ?: 1.0
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, 0.0, top * 1.12)
        } else {
            with(axes) {
                frame(
                    low = 0.0,
                    high = top * 1.12,
                    yTicks = niceTicks(0.0, top, most = 2),
                    xTicks = xTicks,
                    yLabel = yLabel,
                )
            }
        }

        val gap = (plot.width / values.size * 0.28f).coerceIn(1.5.dp.toPx(), 5.dp.toPx())
        val barWidth = ((plot.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val radius = CornerRadius(2.dp.toPx())
        selected?.takeIf { it in values.indices }?.let {
            drawMarker(plot, plot.left + it * (barWidth + gap) + barWidth / 2f, colour)
        }
        values.forEachIndexed { index, value ->
            if (value <= 0.0) return@forEachIndexed
            val y = plot.y(value)
            val reached = target != null && value >= target
            drawRoundRect(
                color = when {
                    index == selected -> colour
                    highlightLast && index == values.lastIndex -> colour
                    reached -> colour.copy(alpha = 0.78f)
                    else -> dim
                },
                topLeft = Offset(plot.left + index * (barWidth + gap), y),
                size = Size(barWidth, (plot.bottom - y).coerceAtLeast(1f)),
                cornerRadius = radius,
            )
        }
        if (target != null) drawGoal(plot, target, colour, axes, goalLabel)
    }
}

/** The goal line, labelled in the gutter when there is a gutter to label. */
private fun DrawScope.drawGoal(
    plot: Plot,
    target: Double,
    colour: Color,
    axes: Axes?,
    label: String?,
) {
    if (axes != null) {
        with(axes) { goalLine(plot, target, colour, label) }
        return
    }
    val y = plot.y(target)
    if (y.isNaN() || y < 0f || y > plot.bottom) return
    drawLine(
        color = colour.copy(alpha = 0.5f),
        start = Offset(plot.left, y),
        end = Offset(plot.right, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
    )
}

/** One bar a night, stacked deep, light, REM and awake, against a goal. */
@Composable
internal fun StageBars(
    nights: List<FourStages>,
    deep: Color,
    light: Color,
    rem: Color,
    awake: Color,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    fill: Boolean = false,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    goalMinutes: Double? = null,
    goalColour: Color = rem,
    goalLabel: String? = null,
    selected: Int? = null,
) {
    ChartCanvas(modifier, height, fill) {
        if (nights.isEmpty()) return@ChartCanvas
        val longest = maxOf(nights.maxOf { it.total }, goalMinutes ?: 0.0).takeIf { it > 0.0 } ?: 1.0
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, 0.0, longest * 1.08)
        } else {
            with(axes) {
                frame(
                    low = 0.0,
                    high = longest * 1.08,
                    yTicks = niceTicks(0.0, longest, most = 2).map { it },
                    xTicks = xTicks,
                    yLabel = { "${(it / 60).roundToInt()}h" },
                )
            }
        }

        val gap = (plot.width / nights.size * 0.26f).coerceIn(1.5.dp.toPx(), 5.dp.toPx())
        val barWidth = ((plot.width - gap * (nights.size - 1)) / nights.size).coerceAtLeast(1f)
        selected?.takeIf { it in nights.indices }?.let {
            drawMarker(plot, plot.left + it * (barWidth + gap) + barWidth / 2f, goalColour)
        }

        nights.forEachIndexed { index, night ->
            if (night.total <= 0.0) return@forEachIndexed
            val tall = plot.bottom - plot.y(night.total)
            val left = plot.left + index * (barWidth + gap)
            var top = plot.bottom - tall
            // Top to bottom: awake, REM, light, deep -- the order a hypnogram
            // reads, so the two charts agree about which end is which.
            listOf(
                night.awake to awake,
                night.rem to rem,
                night.light to light,
                night.deep to deep,
            ).forEach { (share, tint) ->
                if (share <= 0.0) return@forEach
                val segment = (tall * (share / night.total)).toFloat()
                drawRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, segment.coerceAtLeast(1f)),
                )
                top += segment
            }
        }
        if (goalMinutes != null) drawGoal(plot, goalMinutes, goalColour, axes, goalLabel)
    }
}

/** A night reduced to four shares, so the chart needs no Duration maths. */
internal data class FourStages(
    val deep: Double,
    val light: Double,
    val rem: Double,
    val awake: Double,
) {
    val total: Double get() = deep + light + rem + awake
}

/** Bars either side of zero: the one shape that reads as a direction. */
@Composable
internal fun DivergingBars(
    values: List<Double>,
    below: Color,
    above: Color,
    axis: Color,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    fill: Boolean = false,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    target: Double? = null,
    goalLabel: String? = null,
    selected: Int? = null,
) {
    ChartCanvas(modifier, height, fill) {
        if (values.isEmpty()) return@ChartCanvas
        val reach = maxOf(values.maxOf { abs(it) }, abs(target ?: 0.0)).takeIf { it > 0.0 } ?: 1.0
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, -reach, reach)
        } else {
            with(axes) {
                frame(
                    low = -reach * 1.12,
                    high = reach * 1.12,
                    yTicks = listOf(-reach, 0.0, reach),
                    xTicks = xTicks,
                    yLabel = { shortNumber(it) },
                    baseline = false,
                )
            }
        }

        val middle = plot.y(0.0)
        val gap = (plot.width / values.size * 0.26f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
        val barWidth = ((plot.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val radius = CornerRadius(1.5.dp.toPx())

        drawLine(
            color = axis,
            start = Offset(plot.left, middle),
            end = Offset(plot.right, middle),
            strokeWidth = 1.dp.toPx(),
        )
        selected?.takeIf { it in values.indices }?.let {
            drawMarker(plot, plot.left + it * (barWidth + gap) + barWidth / 2f, below)
        }
        values.forEachIndexed { index, value ->
            val y = plot.y(value)
            drawRoundRect(
                color = if (value < 0) below else above,
                topLeft = Offset(plot.left + index * (barWidth + gap), minOf(y, middle)),
                size = Size(barWidth, abs(y - middle).coerceAtLeast(1f)),
                cornerRadius = radius,
            )
        }
        if (target != null) drawGoal(plot, target, below, axes, goalLabel)
    }
}

/**
 * Each night against a goal, and what those nights add up to.
 *
 * Two readings in one frame deliberately. The bars are what happened and
 * the line is what it came to, and "how am I two hours down" is answered by
 * seeing which nights did it -- which is one picture, not two.
 *
 * One scale for both, which flatters neither. A night three quarters of an
 * hour short is genuinely small beside a balance of two hours, and giving
 * each its own scale would say otherwise.
 *
 * A null night leaves a gap in the bars and leaves the line level. Nothing
 * was recorded, and a zero there would be a claim that nothing was slept.
 */
@Composable
internal fun BankChart(
    nightly: List<Double?>,
    running: List<Double>,
    short: Color,
    over: Color,
    axis: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    label: (Double) -> String = { shortNumber(it) },
    selected: Int? = null,
) {
    ChartCanvas(modifier, height, false) {
        if (nightly.isEmpty()) return@ChartCanvas
        val reach = maxOf(
            nightly.filterNotNull().maxOfOrNull { abs(it) } ?: 0.0,
            running.maxOfOrNull { abs(it) } ?: 0.0,
        ).takeIf { it > 0.0 } ?: 1.0
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, -reach * 1.12, reach * 1.12)
        } else {
            with(axes) {
                frame(
                    low = -reach * 1.12,
                    high = reach * 1.12,
                    yTicks = listOf(-reach, 0.0, reach),
                    xTicks = xTicks,
                    yLabel = label,
                    baseline = false,
                )
            }
        }

        val middle = plot.y(0.0)
        val gap = (plot.width / nightly.size * 0.26f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
        val barWidth = ((plot.width - gap * (nightly.size - 1)) / nightly.size).coerceAtLeast(1f)
        val radius = CornerRadius(1.5.dp.toPx())
        fun centre(index: Int) = plot.left + index * (barWidth + gap) + barWidth / 2f

        // Dashed, because it is the goal met exactly rather than a reading.
        drawLine(
            color = axis,
            start = Offset(plot.left, middle),
            end = Offset(plot.right, middle),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        selected?.takeIf { it in nightly.indices }?.let { drawMarker(plot, centre(it), short) }
        nightly.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val y = plot.y(value)
            drawRoundRect(
                // Behind the line, so the bars read as the working and the
                // line as the answer.
                color = (if (value < 0) short else over).copy(alpha = 0.45f),
                topLeft = Offset(plot.left + index * (barWidth + gap), minOf(y, middle)),
                size = Size(barWidth, abs(y - middle).coerceAtLeast(1f)),
                cornerRadius = radius,
            )
        }
        if (running.size >= 2) {
            val path = Path()
            running.forEachIndexed { index, value ->
                val x = centre(index)
                val y = plot.y(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = short,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // Where it stands now: the figure at the top of the screen,
            // which ought to be findable on the chart under it.
            drawCircle(
                color = short,
                radius = 4.dp.toPx(),
                center = Offset(centre(running.lastIndex), plot.y(running.last())),
            )
        }
    }
}

/**
 * A progress ring. Only a metric with a goal gets one.
 *
 * A full ring lights its own track. At 100% the arc closes on a grey circle
 * nobody can see behind it, so the finished state looked like the state one
 * step before it; now the whole ring carries the colour and the difference
 * is visible from across the tab.
 */
@Composable
internal fun GoalRing(
    progress: Float,
    colour: Color,
    track: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 7.dp,
    content: @Composable () -> Unit,
) {
    val met = progress >= 1f
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(
                color = if (met) colour.copy(alpha = 0.3f) else track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width),
            )
            if (progress > 0f) {
                drawArc(
                    color = colour,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * One interval's worth of beats, reduced to what a few pixels can show.
 */
internal data class BeatBand(val at: Long, val lowest: Int, val highest: Int)

/**
 * Buckets samples into [count] equal slices of time.
 *
 * Eight hours of beats is about eighteen thousand readings, and a phone
 * gives the chart some eight hundred pixels. Drawn as a line that is twenty
 * readings a pixel: the strokes overlap into a solid block and the shape
 * disappears into its own ink. Each slice keeps its lowest and highest
 * instead, which is the honest summary of what a pixel column contains --
 * and the one people actually read a heart chart for, since the question is
 * how high it went and when, not what it was at 10:43:07.
 */
internal fun bandsOf(samples: List<HeartSample>, count: Int = 80): List<BeatBand> {
    if (samples.size < 2) return emptyList()
    val ordered = samples.sortedBy { it.atMillis }
    val first = ordered.first().atMillis
    val span = (ordered.last().atMillis - first).takeIf { it > 0 } ?: return emptyList()
    val slice = span / count.coerceAtLeast(1)
    if (slice <= 0) return emptyList()

    val bands = ArrayList<BeatBand>(count)
    var index = 0
    for (bucket in 0 until count) {
        val until = first + slice * (bucket + 1)
        var lowest = Int.MAX_VALUE
        var highest = Int.MIN_VALUE
        while (index < ordered.size && ordered[index].atMillis < until) {
            val bpm = ordered[index].bpm
            if (bpm < lowest) lowest = bpm
            if (bpm > highest) highest = bpm
            index++
        }
        // A slice the band was not recording stays out, so a gap in the
        // data is a gap in the chart rather than a line drawn through it.
        if (highest >= lowest) {
            bands += BeatBand(first + slice * bucket + slice / 2, lowest, highest)
        }
    }
    return bands
}

/**
 * Heart rate over a stretch of time, over the zones as bands.
 *
 * Zones are a property of the axis, not of the data, so they are drawn behind
 * the series rather than recolouring it into four pieces: a line that changes
 * colour reads as four different measurements.
 *
 * Dense stretches are drawn as the range within each slice of time; a short
 * run of readings, like a live measurement, keeps its line, because with
 * fourteen beats the line *is* the data.
 */
@Composable
internal fun DayHeartLine(
    samples: List<HeartSample>,
    colour: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    zones: List<ZoneBand> = emptyList(),
    markPeak: Boolean = false,
    peakColour: Color = colour,
    /** Where the finger is, as a fraction across the plot. */
    selected: Float? = null,
) {
    // Bucketed once per set of readings rather than once per frame: a drag
    // across the chart would otherwise re-walk eighteen thousand samples
    // sixty times a second to draw the same hundred and fifty bars.
    val bands = remember(samples) {
        if (samples.size <= 400) emptyList() else {
            // About a slice a minute, capped at what the width can show.
            // That is what makes a shorter window a more detailed chart
            // rather than the same chart stretched: an hour gets
            // minute-by-minute bars, a whole day gets twelve-minute ones.
            val minutes =
                ((samples.last().atMillis - samples.first().atMillis) / 60_000L).toInt()
            bandsOf(samples, minutes.coerceIn(20, 120))
        }
    }
    ChartCanvas(modifier, height, false) {
        if (samples.size < 2) return@ChartCanvas
        val ordered = samples.sortedBy { it.atMillis }
        val lowest = ordered.minOf { it.bpm }.toDouble()
        val highest = ordered.maxOf { it.bpm }.toDouble()
        val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
        val low = lowest - span * 0.12
        val high = highest + span * 0.12

        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, low, high)
        } else {
            with(axes) {
                frame(low = low, high = high, yTicks = niceTicks(lowest, highest), xTicks = xTicks)
            }
        }
        if (axes != null) {
            zones.forEach { zone ->
                with(axes) { band(plot, zone.from, zone.until, zone.colour, zone.label) }
            }
        }

        val first = ordered.first().atMillis
        val across = (ordered.last().atMillis - first).takeIf { it > 0 } ?: 1L
        fun xAt(at: Long): Float = plot.left + plot.width * (at - first).toFloat() / across.toFloat()

        selected?.let { at ->
            val x = plot.x(at)
            drawMarker(plot, x, colour)
            val nearest = ordered.minBy { abs(xAt(it.atMillis) - x) }
            drawCircle(
                color = colour,
                radius = 4.dp.toPx(),
                center = Offset(xAt(nearest.atMillis), plot.y(nearest.bpm.toDouble())),
            )
        }

        if (bands.isEmpty()) {
            val path = Path()
            ordered.forEachIndexed { index, sample ->
                val x = xAt(sample.atMillis)
                val y = plot.y(sample.bpm.toDouble())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, colour, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        } else {
            // One rounded bar a slice, from its lowest beat to its highest,
            // with a hairline of space between them: the gaps are what stop
            // the chart filling in solid.
            // Wide enough to read as a bar, with real space between: at a
            // hundred and fifty slices the gaps closed up and the chart went
            // back to being a block of colour.
            val width = (plot.width / bands.size * 0.66f).coerceIn(2f, 9.dp.toPx())
            val cap = CornerRadius(width / 2f)
            bands.forEach { band ->
                val top = plot.y(band.highest.toDouble())
                val bottom = plot.y(band.lowest.toDouble())
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(xAt(band.at) - width / 2f, top),
                    size = Size(width, (bottom - top).coerceAtLeast(width)),
                    cornerRadius = cap,
                )
            }
        }

        if (markPeak) {
            val peak = ordered.maxBy { it.bpm }
            drawCircle(
                color = peakColour,
                radius = 3.4.dp.toPx(),
                center = Offset(xAt(peak.atMillis), plot.y(peak.bpm.toDouble())),
            )
        }
    }
}

/** One zone, in beats, for the bands behind a heart rate line. */
internal data class ZoneBand(
    val from: Double,
    val until: Double,
    val colour: Color,
    val label: String?,
)


/**
 * A session's shape, in the width of a thumbnail.
 *
 * The bands behind it are the same heart rate zones the detail screen draws,
 * so a hard session looks hard here too and the row answers "was that a
 * push?" without being opened. No axis: at this size a scale would be three
 * unreadable numbers, and the question is the shape, not the values.
 */
@Composable
internal fun SparkLine(
    values: List<Double>,
    colour: Color,
    bands: List<Pair<ClosedFloatingPointRange<Double>, Color>> = emptyList(),
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
) {
    if (values.size < 2) return
    Canvas(modifier.height(height)) {
        val lowest = values.min()
        val highest = values.max()
        val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
        val plot = Plot(0f, 0f, size.width, size.height, lowest - span * 0.15, highest + span * 0.15)

        bands.forEach { (range, tint) ->
            val top = plot.y(range.endInclusive).coerceIn(0f, size.height)
            val bottom = plot.y(range.start).coerceIn(0f, size.height)
            if (bottom - top < 1f) return@forEach
            drawRoundRect(
                color = tint.copy(alpha = 0.16f),
                topLeft = Offset(0f, top),
                size = Size(size.width, bottom - top),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }

        val path = Path()
        values.forEachIndexed { index, value ->
            val x = plot.xOf(index, values.size, 1.dp.toPx())
            val y = plot.y(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, colour, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * One rail per metric: the usual band as a lit segment, the latest reading as
 * a dot on it.
 *
 * Five unlike units -- bpm, ms, breaths, degrees, percent -- cannot share an
 * axis, so each rail is scaled to its own range and all that is compared is
 * position: where today sits inside what is usual for that one metric.
 */
@Composable
internal fun BandRows(
    rails: List<BandRail?>,
    track: Color,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 4.dp,
    gap: Dp = 4.dp,
) {
    if (rails.isEmpty()) return
    val total = rowHeight * rails.size + gap * (rails.size - 1)
    Canvas(modifier.fillMaxWidth().height(total)) {
        val railHeight = rowHeight.toPx()
        val spacing = gap.toPx()
        val radius = (railHeight * 0.9f).coerceAtLeast(2.5.dp.toPx())
        // The dot overhangs the rail by its radius at either end, so the rail
        // stops short of the edges rather than clipping the newest reading.
        val inset = radius
        val usable = (size.width - inset * 2).coerceAtLeast(1f)

        rails.forEachIndexed { index, rail ->
            val top = index * (railHeight + spacing)
            val middle = top + railHeight / 2f
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, top),
                size = Size(size.width, railHeight),
                cornerRadius = CornerRadius(railHeight / 2f),
            )
            if (rail == null) return@forEachIndexed
            drawRoundRect(
                color = rail.colour.copy(alpha = 0.3f),
                topLeft = Offset(inset + usable * rail.bandFrom, top),
                size = Size(
                    (usable * (rail.bandTo - rail.bandFrom)).coerceAtLeast(railHeight),
                    railHeight,
                ),
                cornerRadius = CornerRadius(railHeight / 2f),
            )
            drawCircle(
                color = rail.colour,
                radius = radius,
                center = Offset(inset + usable * rail.at, middle),
            )
        }
    }
}

/**
 * One rail's geometry, already in 0..1 of its own range. A null entry is a
 * metric nothing writes on this phone, drawn as an empty rail rather than
 * left out, so the row count keeps matching what Vitals says it has.
 */
internal data class BandRail(
    val at: Float,
    val bandFrom: Float,
    val bandTo: Float,
    val colour: Color,
)

/** Where a trend's latest reading and usual band sit across its own range. */
internal fun Trend.rail(colour: Color): BandRail {
    val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
    fun at(value: Double): Float = ((value - lowest) / span).toFloat().coerceIn(0f, 1f)
    return BandRail(
        at = at(latest),
        bandFrom = at(usual.low),
        bandTo = at(usual.high),
        colour = colour,
    )
}

/**
 * Today's one reading, split into the parts it is made of.
 *
 * Horizontal, and about a single day: energy in against energy out, or a
 * plate's three macros, is a proportion of today, and a week of vertical
 * bars answers a question the figure above it did not ask. The widgets
 * drew it this way first and were right to.
 *
 * Colour carries meaning here -- which part is which -- so these keep
 * their own palette whatever colour the tile is wearing, exactly as the
 * sleep stages do.
 */
@Composable
internal fun SplitBar(
    parts: List<Pair<Double, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    gap: Dp = 3.dp,
) {
    val shown = parts.filter { it.first > 0.0 }
    if (shown.isEmpty()) return
    ChartCanvas(modifier, height, false) {
        val total = shown.sumOf { it.first }.toFloat().takeIf { it > 0f } ?: return@ChartCanvas
        val between = gap.toPx()
        val available = (size.width - between * (shown.size - 1)).coerceAtLeast(1f)
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        var x = 0f
        shown.forEach { (share, colour) ->
            // A sliver still has to be visible: a day with four grams of fat
            // on it is a true thing about that day, and a segment rounded
            // away to nothing reads as a macro that was not eaten at all.
            val wide = (available * (share.toFloat() / total)).coerceAtLeast(MIN_SEGMENT.toPx())
            drawRoundRect(
                color = colour,
                topLeft = Offset(x, 0f),
                size = Size(wide, size.height),
                cornerRadius = radius,
            )
            x += wide + between
        }
    }
}

private val MIN_SEGMENT = 3.dp

/**
 * Bars made of parts, each bar as tall as its own total.
 *
 * Not normalised: a day of 2,600 calories and a day of 900 are different
 * days, and drawing both as full-height bars that differ only in their
 * stripes throws away the one thing anyone reads a calorie chart for. The
 * split is still there, as the proportions inside each bar.
 */
@Composable
internal fun StackedBars(
    bars: List<List<Pair<Double, Color>>>,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    goal: Double? = null,
    goalColour: Color = Color.Gray,
    goalLabel: String? = null,
    selected: Int? = null,
) {
    if (bars.isEmpty()) return
    ChartCanvas(modifier, height, false) {
        val totals = bars.map { parts -> parts.sumOf { it.first } }
        val top = maxOf(totals.max(), goal ?: 0.0).takeIf { it > 0.0 } ?: 1.0
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, 0.0, top * 1.1)
        } else {
            with(axes) {
                frame(
                    low = 0.0,
                    high = top * 1.1,
                    yTicks = niceTicks(0.0, top, most = 2),
                    xTicks = xTicks,
                    yLabel = { shortNumber(it) },
                )
            }
        }

        val gap = (plot.width / bars.size * 0.24f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
        val barWidth = ((plot.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        selected?.takeIf { it in bars.indices }?.let {
            drawMarker(plot, plot.left + it * (barWidth + gap) + barWidth / 2f, goalColour)
        }
        bars.forEachIndexed { index, parts ->
            if (totals[index] <= 0.0) return@forEachIndexed
            val left = plot.left + index * (barWidth + gap)
            var y = plot.bottom
            parts.forEach { (share, colour) ->
                if (share <= 0.0) return@forEach
                val segment = (plot.bottom - plot.y(share)).coerceAtLeast(1f)
                drawRect(
                    color = colour,
                    topLeft = Offset(left, y - segment),
                    size = Size(barWidth, segment),
                )
                y -= segment
            }
        }
        if (goal != null) drawGoal(plot, goal, goalColour, axes, goalLabel)
    }
}

/**
 * Two running totals on one axis, with the gap between them shaded.
 *
 * The shading is the point: it is the balance, drawn as an area rather than
 * stated as a number, so the hour it opened up is visible.
 */
@Composable
internal fun RunningTotals(
    first: List<Double>,
    second: List<Double>,
    firstColour: Color,
    secondColour: Color,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    axes: Axes? = null,
    xTicks: List<Pair<Float, String>> = emptyList(),
    stepped: Boolean = true,
    selected: Int? = null,
) {
    if (first.size < 2) return
    ChartCanvas(modifier, height, false) {
        val top = maxOf(first.max(), second.maxOrNull() ?: 0.0, 1.0)
        val plot = if (axes == null) {
            Plot(0f, 0f, size.width, size.height, 0.0, top * 1.08)
        } else {
            with(axes) {
                frame(
                    low = 0.0,
                    high = top * 1.08,
                    yTicks = niceTicks(0.0, top, most = 2),
                    xTicks = xTicks,
                    yLabel = { shortNumber(it) },
                )
            }
        }
        fun x(index: Int) = plot.xOf(index, first.size)

        if (second.size == first.size) {
            val between = Path().apply {
                moveTo(x(0), plot.y(first.first()))
                first.indices.forEach { lineTo(x(it), plot.y(first[it])) }
                for (index in second.indices.reversed()) lineTo(x(index), plot.y(second[index]))
                close()
            }
            drawPath(
                between,
                color = (if (second.last() < first.last()) firstColour else secondColour)
                    .copy(alpha = 0.14f),
            )
        }

        selected?.takeIf { it in first.indices }?.let {
            drawMarker(plot, x(it), firstColour)
        }
        listOf(first to firstColour, second to secondColour).forEach { (series, colour) ->
            val path = Path()
            series.forEachIndexed { index, value ->
                val px = x(index)
                val py = plot.y(value)
                when {
                    index == 0 -> path.moveTo(px, py)
                    // Eating is events, not a flow: the eaten line steps up
                    // at a meal and stays flat between, which is what
                    // actually happened.
                    stepped && series === second -> {
                        path.lineTo(px, plot.y(series[index - 1]))
                        path.lineTo(px, py)
                    }
                    else -> path.lineTo(px, py)
                }
            }
            drawPath(path, colour, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(
                colour,
                radius = 3.5.dp.toPx(),
                center = Offset(x(series.lastIndex), plot.y(series.last())),
            )
        }
    }
}

/**
 * One night as it happened: each stretch drawn at its own time and at the
 * height its stage sits at, deep lowest and awake highest.
 *
 * This is the one sleep chart that is not a total. A stacked bar says you
 * got an hour of deep; a hypnogram says you got it in the first two hours
 * and never again, which is the part worth knowing.
 */
@Composable
internal fun Hypnogram(
    segments: List<HypnogramBar>,
    modifier: Modifier = Modifier,
    height: Dp = 86.dp,
    selected: Float? = null,
    markerColour: Color = Color.Gray,
) {
    if (segments.isEmpty()) return
    Canvas(modifier.fillMaxWidth().height(height)) {
        val first = segments.first().fromMillis
        val span = (segments.last().toMillis - first).toFloat().coerceAtLeast(1f)
        val lanes = 4
        val laneHeight = size.height / lanes
        val barHeight = laneHeight * 0.62f
        val radius = CornerRadius(2.dp.toPx())

        selected?.let { at ->
            val x = size.width * at.coerceIn(0f, 1f)
            drawLine(
                color = markerColour.copy(alpha = 0.55f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        segments.forEach { bar ->
            val left = (bar.fromMillis - first) / span * size.width
            val right = (bar.toMillis - first) / span * size.width
            val top = bar.lane * laneHeight + (laneHeight - barHeight) / 2f
            drawRoundRect(
                color = bar.colour,
                topLeft = Offset(left, top),
                size = Size((right - left).coerceAtLeast(1.5.dp.toPx()), barHeight),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * One stretch, already placed. [lane] is 0 at the top, which is awake, so
 * the chart reads the same way round as the stacked bars beside it.
 */
internal data class HypnogramBar(
    val fromMillis: Long,
    val toMillis: Long,
    val lane: Int,
    val colour: Color,
)

/** 10,400 as "10k", 2,350 as "2.4k", 870 as "870": an axis label, not a figure. */
internal fun shortNumber(value: Double): String {
    val size = abs(value)
    val sign = if (value < 0) "−" else ""
    return when {
        size >= 10_000 -> "$sign${(size / 1000).roundToInt()}k"
        // 5k, not 5.0k: the decimal is noise on an axis and the figures that
        // need it are the ones in the card, not the ones on the scale.
        size >= 1_000 -> {
            val thousands = size / 1000
            val rounded = Math.round(thousands * 10) / 10.0
            sign + (if (rounded % 1.0 == 0.0) "${rounded.roundToInt()}k" else "${rounded}k")
        }
        else -> "$sign${size.roundToInt()}"
    }
}
