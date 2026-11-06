package com.srineere.airbridge.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color

/**
 * The accent swatches.
 *
 * The old set had a problem that was not about taste: three of its six were
 * the same hex as a health colour. `#5BD6A8` was both the green accent and
 * `extras.steps`; `#E8C48F` was both the amber accent and `extras.hrv`; the
 * rose accent sat a shade off `extras.heart`. On the one tab where colour
 * carries meaning, the chrome and the data were the same colour — which
 * contradicts the note in [AirExtraColors] saying those colours mean
 * something.
 *
 * These five sit in hues the charts do not use. Warmth is deliberately absent:
 * the warm band belongs to energy and HRV, so an app that wants to look warm
 * changes its ground to Umber instead.
 *
 * Each [dark] value clears 4.5:1 on the palest dark ground and each [light]
 * value on the palest light one, which means both clear every other ground in
 * their mode. That floor is not decoration — the accent draws 1px chart
 * strokes and 11px axis labels.
 */
enum class Accent(
    val label: String,
    val dark: Color,
    val light: Color,
) {
    Azure("Azure", Color(0xFF84BDF7), Color(0xFF0E60A0)),
    Iris("Iris", Color(0xFFBFA6F2), Color(0xFF622DBE)),
    Orchid("Orchid", Color(0xFFEFA4DE), Color(0xFFA3298B)),
    Moss("Moss", Color(0xFFADE392), Color(0xFF39741B)),

    /** The icon's own rose, so the app can match what launched it. */
    Rose("Rose", Color(0xFFE7A6A6), Color(0xFFA8454F)),

    /** No colour outside the charts. Selection is carried by a filled shape. */
    Graphite("Graphite", Color(0xFFDDE1E4), Color(0xFF586874)),

    /**
     * The wallpaper's hue, and only its hue.
     *
     * Material You supplies a whole colour scheme, surfaces included, so
     * taking it wholesale would silently overwrite whatever ground had just
     * been chosen — the two features were mutually exclusive as the theme was
     * first wired. Reading the hue and building our own colour from it keeps
     * both.
     */
    Dynamic("Match my wallpaper", Color(0xFF84BDF7), Color(0xFF0E60A0)),
    ;

    fun colour(dark: Boolean): Color = if (dark) this.dark else this.light
}

val Accent.isAvailable: Boolean
    get() = this != Accent.Dynamic || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The presets, in the order the screen offers them. Dynamic is not one. */
val accentSwatches: List<Accent> = Accent.entries.filter { it != Accent.Dynamic }

/**
 * A ground and an accent that were chosen together.
 *
 * A theme names both modes, not one: picking a dark ground alone leaves the
 * light side undefined, and someone on System would cross midday into a
 * half-chosen look. Picking a preset writes all three fields; changing any of
 * them afterwards simply means no preset matches, and the screen says Custom.
 */
enum class ThemePreset(
    val label: String,
    val note: String,
    val darkGround: Ground,
    val lightGround: Ground,
    val accent: Accent,
) {
    /** The pair the icon is drawn in, and what a fresh install opens on. */
    InkTheme("Cuff", "Cool near-black and the app's own rose.", Ground.Ink, Ground.Paper, Accent.Rose),
    PitchTheme(
        "Pitch",
        "True black, for OLED.",
        Ground.Pitch, Ground.White, Accent.Azure,
    ),
    DuskTheme("Dusk", "Dim both ways.", Ground.Dusk, Ground.Mist, Accent.Iris),
    UmberTheme("Umber", "Warm, both ways.", Ground.Umber, Ground.Cream, Accent.Moss),
    MonoTheme(
        "Mono",
        "No colour outside the charts.",
        Ground.Ink, Ground.Paper, Accent.Graphite,
    ),
    ;

    companion object {
        /** The preset these three add up to, or null when they add up to none. */
        fun matching(darkGround: Ground, lightGround: Ground, accent: Accent): ThemePreset? =
            entries.firstOrNull {
                it.darkGround == darkGround && it.lightGround == lightGround && it.accent == accent
            }
    }
}
