package com.srineere.airbridge.widget

import android.app.WallpaperManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.TileKind

/**
 * Type on the wallpaper, and nothing else.
 *
 * No card, no chart, no border: three pieces of text sitting on whatever
 * photograph is behind them. It is the cheapest family here by a wide margin
 * -- no bitmap, no shape drawable, and the only one with nothing to round,
 * which matters because `cornerRadius` wants API 31 and this app goes back
 * to 28.
 *
 * It is also the only one that can become unreadable, and [wantsDarkInk] is
 * the answer to that.
 */
enum class TypeFace(
    val title: String,
    val summary: String,
    val word: String,
    val colour: TileColour,
    val sizes: Set<WidgetSize> = setOf(
        WidgetSize.Strip, WidgetSize.Square, WidgetSize.Row, WidgetSize.Wide,
    ),
) {
    Steps("Steps, as type", "The figure and the word, no card", "Steps", TileColour.Steps),
    Sleep("Sleep, as type", "Last night, in plain type", "Sleep", TileColour.Sleep),
    Battery("Battery, as type", "The Air's last charge, in plain type", "Battery", TileColour.Accent),
    Relay("Relaying, as type", "One word, which is also the figure", "Relay", TileColour.Accent),
}

/**
 * What this face says, borrowed from the family that already says it.
 *
 * A Type widget is a *rendering*, not a new reading: Steps here is the Steps
 * tile with its card and its chart taken away. Computing it twice would be
 * two sets of arithmetic drifting apart.
 */
internal fun typeFaceOf(face: TypeFace, snapshot: WidgetSnapshot, now: Long): Face? = when (face) {
    TypeFace.Steps -> faceFor(TileKind.Steps, snapshot)
    TypeFace.Sleep -> faceFor(TileKind.Sleep, snapshot)
    TypeFace.Battery -> bandFace(BandFace.Battery, snapshot.band, now)
        .takeIf { snapshot.band.battery != null }
    TypeFace.Relay -> bandFace(BandFace.Relay, snapshot.band, now)
}

/**
 * Whether the wallpaper is light enough to want dark text on it.
 *
 * Every other family paints its own card and so knows what its text sits on.
 * This one does not, and a widget cannot see the wallpaper -- but it can ask
 * what the wallpaper asked for. `HINT_SUPPORTS_DARK_TEXT` is the same signal
 * the launcher uses to decide the colour of its own icon labels, so type
 * drawn from it matches the labels beside it rather than fighting them.
 *
 * Null where the system will not say: some launchers and live wallpapers
 * publish no colours at all. The caller then falls back to following the
 * app's own light-or-dark, which is a guess, but the only one left.
 */
internal fun wantsDarkInk(context: android.content.Context): Boolean? = runCatching {
    val colours = WallpaperManager.getInstance(context)
        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
    colours.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
}.getOrNull()

@Composable
internal fun TypeWidget(
    face: TypeFace,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    val context = LocalContext.current
    val darkInk = wantsDarkInk(context)
    val ink = colours.inkOn(darkInk)
    val muted = colours.mutedOn(darkInk)
    val word = colours.tileOn(face.colour, darkInk)

    val reading = typeFaceOf(face, snapshot, now)
    // No card means no Empty(): that composable draws one. A face with
    // nothing behind it says so in the same plain type as everything else.
    val figure = reading?.figure?.takeIf { it.isNotEmpty() } ?: "–"

    val base = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)
    Column(
        modifier = if (onClick != null) base.clickable(onClick) else base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (size) {
            WidgetSize.Row -> Row(verticalAlignment = Alignment.Bottom) {
                Figure(figure, 26, ink)
                Spacer(GlanceModifier.width(9.dp))
                Kicker(face.word, word, size = 11)
            }

            WidgetSize.Strip -> {
                Figure(figure, 22, ink)
                Gap(4.dp)
                Kicker(face.word, word, size = 9)
            }

            else -> {
                Figure(figure, if (size == WidgetSize.Wide) 52 else 34, ink)
                Gap(if (size == WidgetSize.Wide) 12.dp else 9.dp)
                Kicker(face.word, word, size = if (size == WidgetSize.Wide) 11 else 10)
                reading?.sub?.takeIf { it.isNotEmpty() }?.let {
                    Gap(4.dp)
                    Sub(it, muted, size = 10)
                }
            }
        }
    }
}
