package com.srineere.airbridge.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * The tab's `MiniScatter`, drawn to a bitmap.
 *
 * Five tiles -- resting heart rate, HRV, breathing, skin temperature and
 * weight -- say the same thing the same way: one dot a reading,
 * a shaded band behind them showing where half your readings land, and the
 * newest dot bigger and at full strength. The widgets drew bars instead,
 * which is a different claim: a bar says "this much", a dot says "here,
 * against where you usually are".
 *
 * Glance cannot draw a dot, so this is an Android Canvas. The arithmetic is
 * copied from `MiniScatter` deliberately -- the padding of a quarter span
 * above and below, the twelve per cent band, the 2.2dp radius, the last dot
 * at one and a half times and full alpha -- because a widget that plots its
 * points differently from the tile is a widget showing a different chart.
 */
internal object ScatterBitmap {
    /** Big enough for any cell, small enough for a Binder transaction. */
    const val MAX_WIDTH = 480
    const val MAX_HEIGHT = 200

    fun draw(
        widthPx: Int,
        heightPx: Int,
        values: List<Double>,
        bandLow: Double?,
        bandHigh: Double?,
        colour: Int,
        density: Float,
    ): Bitmap {
        val width = widthPx.coerceIn(8, MAX_WIDTH)
        val height = heightPx.coerceIn(8, MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val lowest = values.min()
        val highest = values.max()
        val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
        val low = lowest - span * 0.25
        val high = highest + span * 0.25

        fun y(value: Double): Float =
            (height - (value - low) / (high - low) * height).toFloat()

        if (bandLow != null && bandHigh != null) {
            val top = y(bandHigh)
            val bottom = y(bandLow)
            canvas.drawRect(
                0f, top, width.toFloat(), maxOf(bottom, top + 1f),
                Paint().apply { setColor(withAlpha(colour, 0.12f)) },
            )
        }

        val radius = 2.2f * density
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        values.forEachIndexed { index, value ->
            val last = index == values.lastIndex
            dot.color = if (last) colour else withAlpha(colour, 0.8f)
            canvas.drawCircle(
                xOf(index, values.size, radius, width.toFloat()),
                y(value),
                if (last) radius * 1.5f else radius,
                dot,
            )
        }
        return bitmap
    }

    /** The nth of [count] evenly spaced points, inset so dots are not clipped. */
    private fun xOf(index: Int, count: Int, inset: Float, width: Float): Float {
        if (count <= 1) return width / 2f
        val usable = (width - inset * 2).coerceAtLeast(1f)
        return inset + usable * index / (count - 1).toFloat()
    }

    private fun withAlpha(colour: Int, alpha: Float): Int =
        (colour and 0x00FFFFFF) or (((colour ushr 24) * alpha).toInt().coerceIn(0, 255) shl 24)
}
