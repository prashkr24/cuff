package com.srineere.airbridge.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
internal object ChartBitmap {
    const val MAX_WIDTH = 640
    const val MAX_HEIGHT = 320

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
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(4, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val top = ceiling ?: values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        // The gap shrinks as the columns multiply: a day of twenty-four at
        // three points apart would be more gap than chart.
        val gap = (if (values.size > 12) 1.5f else 3f) * density
        val slot = (width + gap) / values.size
        val barWidth = (slot - gap).coerceAtLeast(1f)
        val radius = (barWidth / 2f).coerceAtMost(3f * density)
        val newest = values.indexOfLast { it > 0f }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        values.forEachIndexed { index, value ->
            if (blank && value <= 0f) return@forEachIndexed
            val tall = (height * (value / top)).coerceIn(MIN_BAR * density, height.toFloat())
            val left = index * slot
            paint.color = if (highlightLast && index == newest) colour else dim
            canvas.drawRoundRect(
                RectF(left, height - tall, left + barWidth, height.toFloat()),
                radius, radius, paint,
            )
        }
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

    /** A bar with nothing in it is still a bar, so it keeps a hairline. */
    private const val MIN_BAR = 1.5f
}
