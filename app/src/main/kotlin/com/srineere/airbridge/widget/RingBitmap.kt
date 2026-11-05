package com.srineere.airbridge.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Concentric arcs, drawn to a bitmap.
 *
 * Glance has no determinate ring. `CircularProgressIndicator` takes a colour
 * and a modifier and nothing else -- there is no progress parameter, so it
 * can only spin -- and a curve cannot be made out of the rectangles a Row of
 * Boxes gives you. So every arc in this app is an Android Canvas rendered
 * once per composition and shown as an `Image`.
 *
 * [MAX_PX] is not a guess. A RemoteViews travels to the launcher through a
 * Binder transaction with a hard size limit, and an ARGB bitmap costs four
 * bytes a pixel: a 4x4 widget at three times density would be about 900px
 * square, which is 3MB, which is a widget that silently fails to appear.
 * 192px is more than an arc needs and leaves the transaction room for the
 * rest of the layout; the Image scales it up.
 */
internal object RingBitmap {
    const val MAX_PX = 192

    /**
     * @param arcs outermost first, each a fraction of a full circle
     * @param stroke how thick each arc is, as a share of the bitmap's width
     * @param gap the space between two arcs, as the same share
     */
    fun draw(
        sizePx: Int,
        arcs: List<Pair<Float, Int>>,
        track: Int,
        stroke: Float = 0.105f,
        gap: Float = 0.035f,
    ): Bitmap {
        val side = sizePx.coerceIn(24, MAX_PX)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        if (arcs.isEmpty()) return bitmap

        val canvas = Canvas(bitmap)
        val strokePx = side * stroke
        val gapPx = side * gap
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }

        // Half a stroke in from the edge, or the round cap is clipped by the
        // bitmap on the arc's outer shoulder.
        var inset = strokePx / 2f + 1f
        arcs.forEach { (fraction, colour) ->
            val box = RectF(inset, inset, side - inset, side - inset)
            if (box.width() <= strokePx) return@forEach

            paint.color = track
            canvas.drawArc(box, START_DEGREES, 360f, false, paint)

            val swept = fraction.coerceIn(0f, 1f) * 360f
            if (swept > 0f) {
                paint.color = colour
                // A full circle drawn as a 360 degree arc with a round cap
                // leaves a notch where the cap meets its own tail; a hair
                // under closes it.
                canvas.drawArc(box, START_DEGREES, swept.coerceAtMost(359.9f), false, paint)
            }
            inset += strokePx + gapPx
        }
        return bitmap
    }

    /** Twelve o'clock, because that is where everyone reads a dial from. */
    private const val START_DEGREES = -90f
}
