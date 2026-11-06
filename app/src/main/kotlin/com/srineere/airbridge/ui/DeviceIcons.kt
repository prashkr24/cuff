package com.srineere.airbridge.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * A screenless tracker, drawn here because no Material set has one.
 *
 * The Air was wearing [Icons.Outlined.Watch], which is a face on a strap --
 * the one thing this band does not have. It is the first thing on the
 * Device tab and the app's only picture of the hardware, so it was telling
 * everybody who opened it that they own a different product.
 *
 * Material Icons and Material Symbols both stop at `watch`: a tracker pod
 * is not in either set, and a slashed or generic-device glyph says less
 * than a wrong one. So it is two rounded rectangles -- a continuous strap,
 * and a sensor pod slightly wider than it, with nothing drawn inside the
 * pod because there is nothing to draw.
 */
val BandIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Band",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        // The band, and nothing inside it.
        //
        // Third drawing. A watch was wrong because it has a face. A pod
        // was wrong because a raised rectangle mid-strap reads as the
        // screen this product is sold on not having. A capsule was wrong
        // because it was too wide and too round to be a wrist band at
        // all -- 12 units across a 24 grid, with a corner radius of 4, is
        // a pill with a line through it.
        //
        // Measured off the product instead of imagined. The Air is a
        // woven loop about a quarter as wide as it is long, with its
        // tracker hidden in a pocket behind, and the one piece of
        // hardware you can see from the front is the clasp near the top.
        // So: a narrow rectangle with soft corners, and a clasp that is
        // wider than the band it holds, which is what stops the outline
        // reading as a capsule.
        .addStroked(
            "M9.5,2.5 h5 a2,2 0 0 1 2,2 v14.5 a2,2 0 0 1 -2,2 " +
                "h-5 a2,2 0 0 1 -2,-2 v-14.5 a2,2 0 0 1 2,-2 z"
        )
        // Filled, not outlined. An outlined clasp on an outlined band is
        // two hollow shapes stacked, and at 24dp the eye reads the pair as
        // one outline with a kink in it. Solid, it becomes a separate
        // object sitting on the band -- which is what it is, and the only
        // part of this device that is not fabric.
        .addFilled("M7.4,5.4 h9.2 a1.6,1.6 0 0 1 0,3.2 h-9.2 a1.6,1.6 0 0 1 0,-3.2 z")
        // The weave, drawn lighter than everything above it.
        //
        // At the outline's own weight these three lines were three more
        // hard edges inside a shape that has two, and they read as
        // structure -- slots cut in the band. Thinner and dimmed, they
        // read as texture on it, which is what fabric is. It is also what
        // keeps them from collapsing into a smudge at 24dp, where they
        // sit three units apart.
        .addStroked(
            "M10,11.8 h4 M10,14.8 h4 M10,17.8 h4",
            width = WEAVE_WIDTH,
            alpha = WEAVE_ALPHA,
        )
        .build()
}

private fun ImageVector.Builder.addFilled(pathData: String): ImageVector.Builder = addPath(
    pathData = addPathNodes(pathData),
    fill = SolidColor(Color.Black),
)

private fun ImageVector.Builder.addStroked(
    pathData: String,
    width: Float = OUTLINE_WIDTH,
    alpha: Float = 1f,
): ImageVector.Builder = addPath(
    pathData = addPathNodes(pathData),
    // Black, because Icon tints what it is given and a colour chosen here
    // would be a colour this glyph wore everywhere. The alpha survives
    // that tinting, so a dimmed path stays dimmed in any colour.
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeAlpha = alpha,
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
)

/** What the band and its edges are drawn at. */
private const val OUTLINE_WIDTH = 1.5f

/** And the weave, which is texture rather than an edge. */
private const val WEAVE_WIDTH = 0.9f

private const val WEAVE_ALPHA = 0.45f

/**
 * The glyph for a piece of hardware, from whatever it calls itself.
 *
 * Read rather than assumed: [BandCard] knew it was drawing the Air and drew
 * a watch anyway, and the Health tab draws readings from whatever wrote
 * them -- a Pixel Watch, a Garmin, a phone counting its own steps. A band
 * icon on a Galaxy Watch's workout would be the same mistake pointing the
 * other way.
 *
 * Matched on the model name because that is what both sources give: the
 * band reports its own over GATT, and Health Connect files a device model
 * against every record. Unknown hardware gets the band, since this app's
 * own reason to exist is one.
 */
fun deviceGlyph(model: String?): ImageVector {
    val name = model?.lowercase().orEmpty()
    return when {
        name.isBlank() -> BandIcon
        // Phones first: a phone that counts steps is the one source here
        // that is definitely not worn.
        PHONES.any { it in name } -> Icons.Outlined.PhoneAndroid
        WATCHES.any { it in name } -> Icons.Outlined.Watch
        else -> BandIcon
    }
}

/** Model names that mean a watch rather than a band. */
private val WATCHES = listOf(
    "watch", "versa", "sense", "ionic", "galaxy watch", "forerunner",
    "fenix", "venu", "vivoactive", "instinct", "epix",
)

/** And the ones that mean a phone wrote it. */
private val PHONES = listOf("phone", "pixel 1", "pixel 2", "pixel 3", "sm-", "oneplus", "cph")
