package com.srineere.airbridge.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Bars and stripes, drawn to a bitmap rather than built out of views.
 *
 * **Glance containers hold ten children and silently drop the rest.** The
 * generated layouts it translates into stop at `..._10children.xml`, so a
 * Row asked for more keeps the first ten and discards everything after
 * them, without an error anywhere. A day binned into twenty-four hours --
 * twenty-four bars and twenty-three spacers between them -- came out as
 * five fat columns, and a night's eleven stages came out as five. Every bar
 * chart in every widget was quietly truncated, which is most of why they
 * never looked like the tiles they were copied from.
 *
 * A bitmap is one child. It also buys the things a Row could not give: a
 * rounded cap on a bar two pixels wide, a goal line, and bars that reach
 * the target drawn in full colour while the rest stay dim -- which is what
 * `GoalBars` does on the tab.
 */
/**
 * A chart's scale, where the card is wide enough to carry one.
 *
 * The tab gives a chart a gutter and a footer only at L and XL and draws
 * the bare shape at the smaller sizes; the widget follows that at Wide and
 * Tall. The measurements are the tab's own -- a 28dp gutter, a 15dp footer
 * -- so a widget's scale lines up with the tile's.
 *
 * [yLabel] formats a value for the gutter; [xTicks] are positions from 0
 * to 1 across the plot, already chosen by the caller.
 */
internal class Scale(
    val ink: Int,
    val line: Int,
    val xTicks: List<Pair<Float, String>> = emptyList(),
    val yLabel: ((Float) -> String)? = null,
    val goal: Float? = null,
    val goalLabel: String? = null,
    /**
     * The floor, where zero is not the right one.
     *
     * A bar chart counts up from nothing, so steps and sleep start at zero.
     * A day of heartbeats does not: drawn from zero, seventy beats against
     * a hundred and twenty is a flat band across the top of the card, and
     * the tile's own chart starts near the lowest reading for that reason.
     */
    val low: Float = 0f,
)

/** One rail: the reading, its usual band, and the two colours they take. */
internal class Quad(
    val at: Float,
    val bandFrom: Float,
    val bandTo: Float,
    val dot: Int,
    val band: Int,
)

internal object ChartBitmap {
    const val MAX_WIDTH = 640
    const val MAX_HEIGHT = 320

    /** The tab's DEFAULT_GUTTER and FOOTER, in dp. */
    private const val GUTTER = 28f
    private const val FOOTER = 15f
    private const val TEXT = 9f

    /**
     * The eighth of the plot the tab leaves empty above the tallest point.
     *
     * Without it the highest reading in a series is the topmost pixel of
     * the card: a line ends welded to the edge, and a dashed pace drawn to
     * a total nobody has reached yet runs straight into the corner. The
     * tab's `RunningTotals` scales to `top * 1.08` for exactly that
     * reason, so a widget line takes the same figure.
     */
    private const val HEADROOM = 1.08f

    /** The tab's line, its end dot, and the shading between two lines. */
    private const val LINE = 2.2f
    private const val DOT = 3.5f
    private const val GAP_ALPHA = 36

    /**
     * Lays the plot out inside the bitmap and draws the scale round it.
     *
     * Returns the box the series gets: the whole bitmap where there is no
     * scale, and what is left once the gutter and footer are taken where
     * there is one.
     */
    private fun frame(
        canvas: Canvas,
        width: Int,
        height: Int,
        top: Float,
        scale: Scale?,
        density: Float,
        /**
         * The ceiling the gutter labels count up to, where that is not the
         * ceiling the plot is scaled to. A chart with headroom above its
         * tallest point still wants its ticks chosen from the readings, or
         * the labels are rounded off a number nobody will ever reach.
         */
        tickTop: Float = top,
        /** Room kept at the right-hand edge, for a dot that ends a line. */
        rightInset: Float = 0f,
    ): RectF {
        if (scale == null) {
            return RectF(0f, 0f, width - rightInset, height.toFloat())
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT * density
            strokeWidth = 1f * density
        }
        val gutter = if (scale.yLabel == null) 0f else GUTTER * density
        val footer = if (scale.xTicks.isEmpty()) 0f else FOOTER * density
        val plot = RectF(gutter, 0f, width - rightInset, height - footer)

        scale.yLabel?.let { label ->
            // Rounded values, as the tab's `niceTicks` picks them, not the
            // series' own ceiling: a steps chart labelled 7.5k at the very
            // top is a label with nowhere to sit and a number nobody chose.
            val span = (top - scale.low).coerceAtLeast(1f)
            niceTicks(scale.low, tickTop).forEach { value ->
                val y = plot.bottom - plot.height() * ((value - scale.low) / span)
                val text = label(value)
                paint.color = scale.line
                canvas.drawLine(plot.left, y, plot.right, y, paint)
                paint.color = scale.ink
                canvas.drawText(
                    text,
                    (gutter - paint.measureText(text) - 3f * density).coerceAtLeast(0f),
                    // Never off the top edge: a baseline at y would clip a
                    // label whose tick sits on the ceiling.
                    (y + paint.textSize / 3f).coerceIn(paint.textSize, height.toFloat()),
                    paint,
                )
            }
        }
        scale.xTicks.forEach { (at, text) ->
            val x = plot.left + plot.width() * at.coerceIn(0f, 1f)
            val wide = paint.measureText(text)
            paint.color = scale.ink
            canvas.drawText(
                text,
                (x - wide / 2f).coerceIn(0f, (width - wide).coerceAtLeast(0f)),
                height - 2f * density,
                paint,
            )
        }
        return plot
    }

    /**
     * The goal, dashed across the plot with its figure at the right end.
     *
     * At the end of its own line rather than in the gutter, because the
     * gutter already holds the scale -- the tab's reasoning, and its shape.
     */
    private fun goalLine(
        canvas: Canvas,
        plot: RectF,
        top: Float,
        scale: Scale,
        density: Float,
    ) {
        val goal = scale.goal?.takeIf { it > 0f } ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scale.line
            strokeWidth = 1f * density
            textSize = TEXT * density
        }
        val y = plot.bottom - plot.height() * (goal / top.coerceAtLeast(1f))
        if (y < plot.top) return
        var x = plot.left
        val dash = 4f * density
        while (x < plot.right) {
            canvas.drawLine(x, y, (x + dash).coerceAtMost(plot.right), y, paint)
            x += dash * 2f
        }
        scale.goalLabel?.let {
            val wide = paint.measureText(it)
            canvas.drawText(
                it,
                (plot.right - wide).coerceAtLeast(plot.left),
                (y - 3f * density).coerceAtLeast(paint.textSize),
                paint,
            )
        }
    }

    /**
     * @param values in the series' own units, oldest first
     * @param ceiling what a full-height bar means, or null for the tallest
     * @param blank true to leave a slot empty rather than draw a stub, for a
     *   series whose slots exist whether or not they hold a reading
     */
    fun bars(
        widthPx: Int,
        heightPx: Int,
        values: List<Float>,
        ceiling: Float?,
        colour: Int,
        dim: Int,
        highlightLast: Boolean,
        blank: Boolean,
        density: Float,
        scale: Scale? = null,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(4, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val reach = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        // The goal belongs under the ceiling, or a week that never reached
        // it is a week with a dashed line off the top of its own chart.
        val top = ceiling ?: maxOf(reach, scale?.goal ?: 0f)
        val plot = frame(canvas, width, height, top, scale, density)
        // The gap shrinks as the columns multiply: a day of twenty-four at
        // three points apart would be more gap than chart.
        val gap = (if (values.size > 12) 1.5f else 3f) * density
        val slot = (plot.width() + gap) / values.size
        val barWidth = (slot - gap).coerceAtLeast(1f)
        val radius = (barWidth / 2f).coerceAtMost(3f * density)
        val newest = values.indexOfLast { it > 0f }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        values.forEachIndexed { index, value ->
            if (blank && value <= 0f) return@forEachIndexed
            val tall = (plot.height() * (value / top)).coerceIn(MIN_BAR * density, plot.height())
            val left = plot.left + index * slot
            paint.color = if (highlightLast && index == newest) colour else dim
            canvas.drawRoundRect(
                RectF(left, plot.bottom - tall, left + barWidth, plot.bottom),
                radius, radius, paint,
            )
        }
        if (scale != null) goalLine(canvas, plot, top, scale, density)
        return bitmap
    }

    /**
     * Proportional segments across the full width: a night's stages, a
     * day's macros.
     */
    fun stripe(
        widthPx: Int,
        heightPx: Int,
        parts: List<Pair<Float, Int>>,
        density: Float,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(2, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val total = parts.sumOf { it.first.toDouble() }.toFloat()
        if (parts.isEmpty() || total <= 0f) return bitmap

        val canvas = Canvas(bitmap)
        val gap = 1.5f * density
        val usable = (width - gap * (parts.size - 1)).coerceAtLeast(1f)
        val radius = (height / 3f).coerceAtMost(3f * density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var left = 0f
        parts.forEach { (share, colour) ->
            val segment = (usable * (share / total)).coerceAtLeast(1f)
            paint.color = colour
            canvas.drawRoundRect(
                RectF(left, 0f, left + segment, height.toFloat()), radius, radius, paint,
            )
            left += segment + gap
        }
        return bitmap
    }

    /**
     * Bars made of parts, each as tall as its own total -- a week of
     * nights, each divided into the stages it was made of.
     *
     * Not normalised, for the reason `StackedBars` gives on the tab: a six
     * hour night and a nine hour one are different nights, and drawing
     * both full height throws away the thing a sleep chart is read for.
     *
     * @param bars oldest first, each a list of part-to-colour, bottom up
     * @param goal a line across the chart, in the same units as the parts
     */
    fun stacked(
        widthPx: Int,
        heightPx: Int,
        bars: List<List<Pair<Float, Int>>>,
        goal: Float?,
        goalColour: Int,
        density: Float,
        scale: Scale? = null,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(4, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (bars.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val totals = bars.map { parts -> parts.sumOf { it.first.toDouble() }.toFloat() }
        // A tenth of headroom, as the tab's plot leaves, so the tallest
        // night does not touch the ceiling and read as clipped.
        val top = (maxOf(totals.max(), goal ?: 0f) * 1.1f).takeIf { it > 0f } ?: 1f
        val plot = frame(canvas, width, height, top, scale, density)
        val gap = 3f * density
        val slot = (plot.width() + gap) / bars.size
        val barWidth = (slot - gap).coerceAtLeast(1f)
        val radius = (barWidth / 2f).coerceAtMost(3f * density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        bars.forEachIndexed { index, parts ->
            if (totals[index] <= 0f) return@forEachIndexed
            val left = plot.left + index * slot
            var bottom = plot.bottom
            parts.forEach { (share, colour) ->
                if (share <= 0f) return@forEach
                val segment = (plot.height() * (share / top)).coerceAtLeast(1f)
                paint.color = colour
                // Every segment rounded, then the ones above the floor
                // squared off again by the one under them: the stack reads
                // as one bar with a rounded cap, which is the tab's shape.
                canvas.drawRoundRect(
                    RectF(left, bottom - segment, left + barWidth, bottom),
                    radius, radius, paint,
                )
                if (bottom < plot.bottom) {
                    canvas.drawRect(
                        RectF(left, bottom - radius, left + barWidth, bottom), paint,
                    )
                }
                bottom -= segment
            }
        }
        if (goal != null && goal > 0f) {
            paint.color = goalColour
            paint.strokeWidth = 1f * density
            val y = plot.bottom - plot.height() * (goal / top)
            var x = plot.left
            val dash = 4f * density
            while (x < plot.right) {
                canvas.drawLine(x, y, (x + dash).coerceAtMost(plot.right), y, paint)
                x += dash * 2f
            }
        }
        return bitmap
    }

    /**
     * Round numbers between zero and [top], at most two of them.
     *
     * The tab's own ladder, with its own steps, so a widget's gutter reads
     * the same as the tile's: 0 and 5k under a 7.5k goal, not 7.5k and 0.
     */
    private fun niceTicks(low: Float, top: Float): List<Float> {
        val span = top - low
        if (span <= 0f) return listOf(low)
        val steps = listOf(
            1f, 2f, 5f, 10f, 20f, 25f, 50f, 100f, 200f, 250f, 500f,
            1000f, 2000f, 2500f, 5000f, 10000f,
        )
        val step = steps.firstOrNull { span / it <= 2f } ?: (span / 2f)
        val first = kotlin.math.ceil(low / step) * step
        return generateSequence(first) { it + step }
            .takeWhile { it <= top + step * 0.001f }
            .take(3)
            .toList()
    }

    /**
     * A day of heartbeats, each slice drawn as the range within it.
     *
     * The tile's own shape. A mean per hour is a different claim from the
     * spread within it -- twenty-four fat bars said "this is what your
     * heart did at nine", where the tile says "between here and here".
     *
     * @param spans position across the plot in 0..1, with the low and high
     *   of that slice in the series' own units
     */
    fun beats(
        widthPx: Int,
        heightPx: Int,
        spans: List<Triple<Float, Float, Float>>,
        low: Float,
        high: Float,
        colour: Int,
        newest: Int,
        density: Float,
        scale: Scale? = null,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(4, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (spans.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val plot = frame(canvas, width, height, high, scale, density)
        val span = (high - low).coerceAtLeast(1f)
        // Two points wide with a round cap: thinner than a bar and denser
        // than one, which is what eighty slices of a day have to be.
        val stroke = (plot.width() / spans.size * 0.55f).coerceIn(1.5f * density, 4f * density)
        val radius = stroke / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        spans.forEachIndexed { index, (at, lo, hi) ->
            val x = plot.left + plot.width() * at.coerceIn(0f, 1f)
            val top = plot.bottom - plot.height() * ((hi - low) / span)
            val bottom = plot.bottom - plot.height() * ((lo - low) / span)
            paint.color = if (index == spans.lastIndex) newest else colour
            canvas.drawRoundRect(
                RectF(x - stroke / 2f, top, x + stroke / 2f, bottom.coerceAtLeast(top + stroke)),
                radius, radius, paint,
            )
        }
        return bitmap
    }

    /**
     * One rail a reading: where it sits across its own range, over the
     * half of the range it usually lands in.
     *
     * `BandRows` on the tab, pixel for pixel -- a track, the usual band at
     * thirty per cent over it, and the latest reading as a dot. A bar says
     * "this much"; this says "here, against where you usually are", which
     * is the question Vitals and the trend rows actually ask.
     *
     * @param rails each (position, band start, band end, colour) in 0..1,
     *   or null for a reading nothing on this phone writes
     */
    /**
     * How thick one rail is drawn, at most.
     *
     * The tab's `BandRows` uses ten; eight here because a widget's rail
     * carries no label beside it and a thinner line reads as a scale
     * rather than as a bar.
     */
    private const val RAIL_THICKNESS = 8f

    /**
     * How far a dot sticks out past its rail, as a share of the rail.
     *
     * The radius is nine tenths of the rail's height, so a dot is 1.8
     * rails across against a rail 1 high: four tenths proud at each end.
     */
    private const val OVERHANG = 0.4f

    fun rails(
        widthPx: Int,
        heightPx: Int,
        rails: List<Quad?>,
        track: Int,
        density: Float,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(2, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (rails.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        // A gap as tall as a rail, which is what `BandRows` is given on the
        // tab -- rowHeight and gap are the same number there. Four points
        // between five-point rails drew a block of stripes rather than
        // five separate readings.
        //
        // Capped, because the division alone is only right for a stack.
        // One rail asked for the whole height and got it: a 44dp pill with
        // a 40dp corner radius, which is a blob rather than a reading.
        //
        // And divided by a little more than the slots, because a dot is
        // wider than the rail it sits on. At nine tenths of the rail for a
        // radius it overhangs by four tenths of one at each end, so a
        // stack that filled the height exactly had the top and bottom
        // dots sliced off against the edge of the bitmap.
        val slots = (rails.size * 2 - 1).coerceAtLeast(1)
        val railHeight = (height.toFloat() / (slots + OVERHANG * 2))
            .coerceIn(1f, RAIL_THICKNESS * density)
        val gap = railHeight
        val radius = (railHeight * 0.9f).coerceAtLeast(2.5f * density)
        val overhang = (radius - railHeight / 2f).coerceAtLeast(0f)
        // Spare height is shared above and below rather than all put
        // under: the layout already drops this whole box to the foot of a
        // tall card, and a rail sitting on the floor of the floor is a
        // rail with its bottom edge off the card.
        val stack = rails.size * railHeight + (rails.size - 1) * gap
        val drop = ((height - stack) / 2f).coerceAtLeast(overhang)
        // The dot overhangs the rail by its radius at either end, so the
        // rail stops short rather than clipping the newest reading.
        val inset = radius
        val usable = (width - inset * 2).coerceAtLeast(1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        rails.forEachIndexed { index, rail ->
            val top = drop + index * (railHeight + gap)
            val middle = top + railHeight / 2f
            paint.color = track
            canvas.drawRoundRect(
                RectF(0f, top, width.toFloat(), top + railHeight),
                railHeight / 2f, railHeight / 2f, paint,
            )
            if (rail == null) return@forEachIndexed
            paint.color = rail.band
            val from = inset + usable * rail.bandFrom
            val to = from + (usable * (rail.bandTo - rail.bandFrom)).coerceAtLeast(railHeight)
            canvas.drawRoundRect(
                RectF(from, top, to, top + railHeight),
                railHeight / 2f, railHeight / 2f, paint,
            )
            paint.color = rail.dot
            canvas.drawCircle(inset + usable * rail.at, middle, radius, paint)
        }
        return bitmap
    }

    /**
     * Two running totals on one axis, with the gap between them shaded.
     *
     * The shading is the point: the balance drawn as an area rather than
     * stated as a number, so the hour it opened up is visible. `RunningTotals`
     * on the tab does the same thing with the same two colours.
     */
    fun pair(
        widthPx: Int,
        heightPx: Int,
        first: List<Float>,
        second: List<Float>,
        firstColour: Int,
        secondColour: Int,
        density: Float,
        scale: Scale? = null,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(4, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val count = maxOf(first.size, second.size)
        if (count < 2) return bitmap

        val canvas = Canvas(bitmap)
        val reach = maxOf(first.maxOrNull() ?: 0f, second.maxOrNull() ?: 0f)
            .takeIf { it > 0f } ?: 1f
        val top = reach * HEADROOM
        val plot = frame(canvas, width, height, top, scale, density, reach, DOT * density)
        val span = (count - 1).toFloat()
        fun x(index: Int) = plot.left + plot.width() * (index / span)
        fun y(value: Float) = plot.bottom - plot.height() * (value / top)

        // The gap first, under both lines, so neither is drawn through it.
        if (first.size == second.size) {
            val area = Path()
            area.moveTo(x(0), y(first[0]))
            first.indices.forEach { area.lineTo(x(it), y(first[it])) }
            for (i in second.indices.reversed()) area.lineTo(x(i), y(second[i]))
            area.close()
            canvas.drawPath(
                area,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    // The line on top owns the gap, as on the tab: shading
                    // it always in the first colour said "burned" on a day
                    // spent eating.
                    color = if (second.last() < first.last()) firstColour else secondColour
                    alpha = GAP_ALPHA
                },
            )
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = LINE * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        listOf(first to firstColour, second to secondColour).forEach { (values, colour) ->
            if (values.size < 2) return@forEach
            paint.color = colour
            val path = Path()
            values.forEachIndexed { index, value ->
                val px = x(index)
                val py = y(value)
                when {
                    index == 0 -> path.moveTo(px, py)
                    // Eating is events, not a flow: the second line steps up
                    // at a meal and stays flat between, which is what
                    // actually happened and what the tab's line draws. A
                    // diagonal would claim a steady trickle of calories
                    // through a morning nobody ate in.
                    values === second -> {
                        path.lineTo(px, y(values[index - 1]))
                        path.lineTo(px, py)
                    }
                    else -> path.lineTo(px, py)
                }
            }
            canvas.drawPath(path, paint)
            dot(canvas, x(values.lastIndex), y(values.last()), colour, density)
        }
        return bitmap
    }

    /**
     * Where a line has got to, so far.
     *
     * The tab ends every running total with one, and it is not decoration:
     * a climb that stops in the middle of the plot needs something saying
     * that it stopped there rather than that the chart was cut off.
     */
    private fun dot(canvas: Canvas, x: Float, y: Float, colour: Int, density: Float) {
        val radius = DOT * density
        canvas.drawCircle(
            x,
            y.coerceAtLeast(radius),
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = colour
                style = Paint.Style.FILL
            },
        )
    }

    /** A bar with nothing in it is still a bar, so it keeps a hairline. */
    private const val MIN_BAR = 1.5f
}
