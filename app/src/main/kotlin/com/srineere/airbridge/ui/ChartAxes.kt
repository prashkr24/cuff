package com.srineere.airbridge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The scales and labels every chart on the Health tab shares.
 *
 * A chart without them is a shape: you can see that something went up, and
 * not by how much or since when. Reading the number off the axis is the whole
 * difference between a sparkline and a chart, so the axis toolkit lives here
 * and each chart draws through it rather than inventing its own margins.
 *
 * Everything is one Canvas still -- the labels are drawn, not laid out as
 * composables -- because a row of text nodes per tick is a layout pass the
 * tab does not need to pay for while it scrolls.
 */
@Immutable
internal class Axes(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    val labelColour: Color,
    val gridColour: Color,
) {
    /**
     * Draws the gutter, the gridlines and the baseline, and returns the box
     * the series itself gets.
     *
     * [yTicks] and [xTicks] are given in the series' own units and in
     * fractions of the width; the caller never converts to pixels.
     */
    fun DrawScope.frame(
        low: Double,
        high: Double,
        yTicks: List<Double> = emptyList(),
        xTicks: List<Pair<Float, String>> = emptyList(),
        yLabel: (Double) -> String = { it.toInt().toString() },
        gutter: Dp = DEFAULT_GUTTER,
        baseline: Boolean = true,
    ): Plot {
        val gutterPx = if (yTicks.isEmpty()) 0f else gutter.toPx()
        val footer = if (xTicks.isEmpty()) 0f else FOOTER.toPx()
        val plot = Plot(
            left = gutterPx,
            top = 0f,
            right = size.width,
            bottom = size.height - footer,
            low = low,
            high = high,
        )

        yTicks.forEach { value ->
            val y = plot.y(value)
            if (y.isNaN() || y < 0f || y > plot.bottom) return@forEach
            drawLine(
                color = gridColour,
                start = Offset(plot.left, y),
                end = Offset(plot.right, y),
                strokeWidth = 1f,
            )
            val text = measurer.measure(yLabel(value), style)
            drawText(
                textLayoutResult = text,
                color = labelColour,
                topLeft = Offset(
                    // Right-aligned against the plot, so a three-digit label
                    // and a one-digit label share an edge.
                    (plot.left - text.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                    (y - text.size.height / 2f).coerceIn(0f, plot.bottom - text.size.height),
                ),
            )
        }

        if (baseline && xTicks.isNotEmpty()) {
            drawLine(
                color = gridColour,
                start = Offset(plot.left, plot.bottom),
                end = Offset(plot.right, plot.bottom),
                strokeWidth = 1f,
            )
        }

        xTicks.forEach { (at, label) ->
            if (label.isEmpty()) return@forEach
            val text = measurer.measure(label, style)
            val centre = plot.x(at)
            drawText(
                textLayoutResult = text,
                color = labelColour,
                topLeft = Offset(
                    // Clamped to the chart, so the first and last labels sit
                    // inside the card rather than half off it.
                    (centre - text.size.width / 2f).coerceIn(0f, size.width - text.size.width),
                    plot.bottom + 4.dp.toPx(),
                ),
            )
        }

        return plot
    }

    /** A dashed line across the plot at one value, labelled in the gutter. */
    fun DrawScope.goalLine(plot: Plot, value: Double, colour: Color, label: String? = null) {
        val y = plot.y(value)
        if (y.isNaN() || y < 0f || y > plot.bottom) return
        drawLine(
            color = colour.copy(alpha = 0.55f),
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        if (label != null) {
            // At the right-hand end of its own line, not in the gutter: the
            // gutter already holds the scale, and a goal that lands near a
            // tick printed one label on top of the other.
            val text = measurer.measure(label, style)
            drawText(
                textLayoutResult = text,
                color = colour,
                topLeft = Offset(
                    plot.right - text.size.width,
                    (y - text.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )
        }
    }

    /** A band across the plot between two values, for heart rate zones. */
    fun DrawScope.band(plot: Plot, from: Double, until: Double, colour: Color, label: String?) {
        val top = plot.y(until).coerceIn(0f, plot.bottom)
        val bottom = plot.y(from).coerceIn(0f, plot.bottom)
        if (bottom - top < 0.5f) return
        drawRect(
            color = colour.copy(alpha = 0.13f),
            topLeft = Offset(plot.left, top),
            size = androidx.compose.ui.geometry.Size(plot.right - plot.left, bottom - top),
        )
        if (label != null) {
            // Inside the plot, not in the gutter: the gutter belongs to the
            // scale, and a band label sitting in it printed "Light" across
            // the "100" it was meant to sit beside.
            val text = measurer.measure(label, style)
            if (bottom - top < text.size.height) return
            drawText(
                textLayoutResult = text,
                color = colour,
                topLeft = Offset(
                    plot.left + 4.dp.toPx(),
                    top + (bottom - top - text.size.height) / 2f,
                ),
            )
        }
    }

    private companion object {
        /** Room for a three or four character label and its padding. */
        val DEFAULT_GUTTER = 28.dp

        /** Room for one line of x labels under the baseline. */
        val FOOTER = 15.dp
    }
}

/** The box a series is drawn in, and the two conversions it needs. */
@Immutable
internal class Plot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    private val low: Double,
    private val high: Double,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** A value in the series' own units, as a y pixel. */
    fun y(value: Double): Float {
        val span = (high - low).takeIf { it != 0.0 } ?: 1.0
        return (bottom - (value - low) / span * height).toFloat()
    }

    /** A fraction across the plot, as an x pixel. */
    fun x(at: Float): Float = left + width * at.coerceIn(0f, 1f)

    /** The nth of [count] evenly spaced points, inset so dots are not clipped. */
    fun xOf(index: Int, count: Int, inset: Float = 0f): Float {
        if (count <= 1) return left + width / 2f
        val usable = (width - inset * 2).coerceAtLeast(1f)
        return left + inset + usable * index / (count - 1).toFloat()
    }
}

@Composable
internal fun rememberAxes(
    labelColour: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    gridColour: Color = MaterialTheme.colorScheme.outline,
): Axes {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
    return remember(measurer, style, labelColour, gridColour) {
        Axes(measurer, style, labelColour, gridColour)
    }
}

/**
 * Two or three round numbers inside a range.
 *
 * Round, not evenly spaced: an axis labelled 57.3 and 61.8 is an axis nobody
 * reads. The step is the first of 1, 2, 5, 10, 25, 50... that gives at most
 * four lines across the range, which is what keeps a 30-bpm chart and a
 * 12,000-step chart both legible with the same code.
 */
internal fun niceTicks(low: Double, high: Double, most: Int = 3): List<Double> {
    val span = high - low
    if (span <= 0.0) return listOf(low)
    val steps = listOf(
        1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 250.0, 500.0,
        1000.0, 2000.0, 2500.0, 5000.0, 10000.0,
    )
    val step = steps.firstOrNull { span / it <= most } ?: (span / most)
    val first = Math.ceil(low / step) * step
    return generateSequence(first) { it + step }
        .takeWhile { it <= high + step * 0.001 }
        .take(most + 1)
        .toList()
}
