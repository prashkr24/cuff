package com.srineere.airbridge.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colour the whole app sits on.
 *
 * Four in each mode. They are not interchangeable shades: a ground changes
 * what every neutral derived from it has to be, and one of them — true black
 * — changes how a card can be drawn at all.
 */
enum class Ground(
    val label: String,
    val note: String,
    val dark: Boolean,
    val base: Color,
    /**
     * The colour text is at full strength on this ground, and the one every
     * derived neutral is mixed toward. Warm grounds get warm ink; a neutral
     * grey on Umber reads as a stain on it.
     */
    val ink: Color,
) {
    Ink(
        "Ink",
        "Near-black, cool. Tiles lift off it.",
        dark = true,
        base = Color(0xFF0C0F12),
        ink = Color(0xFFE8EDF0),
    ),

    /**
     * On an OLED screen these pixels are off, which is the whole point. A
     * lifted grey tile would light them again, so tiles on this ground are an
     * outline and nothing else — see [liftsTiles].
     */
    Pitch(
        "Pitch",
        "True black. Tiles lose their fill and keep an outline.",
        dark = true,
        base = Color(0xFF000000),
        ink = Color(0xFFEDEFF1),
    ),

    /** The dim one, and the one that costs the most to support. */
    Dusk(
        "Dusk",
        "Dim grey, easiest at night.",
        dark = true,
        base = Color(0xFF1A1F23),
        ink = Color(0xFFE6EBEE),
    ),
    Umber(
        "Umber",
        "Warm dark.",
        dark = true,
        base = Color(0xFF14110E),
        ink = Color(0xFFF2EDE6),
    ),

    Paper("Paper", "Barely off white.", dark = false, base = Color(0xFFF6F9FA), ink = Color(0xFF141A20)),
    White("White", "Plain white.", dark = false, base = Color(0xFFFFFFFF), ink = Color(0xFF12171C)),

    /** The hardest light ground to pass on, so accents are tuned against it. */
    Mist(
        "Mist",
        "Grey, for rooms where white glares.",
        dark = false,
        base = Color(0xFFE9EDEF),
        ink = Color(0xFF161B20),
    ),
    Cream("Cream", "Warm light.", dark = false, base = Color(0xFFFAF7F2), ink = Color(0xFF1E1914)),
    ;

    /**
     * Whether a card may be drawn as a filled surface.
     *
     * Material conveys depth in dark mode by lifting the surface, and a lift
     * off true black is either invisible or reintroduces exactly the glow the
     * ground was chosen to avoid. Pitch answers no; everything else yes.
     */
    val liftsTiles: Boolean get() = this != Pitch

    companion object {
        val darks: List<Ground> get() = entries.filter { it.dark }
        val lights: List<Ground> get() = entries.filterNot { it.dark }

        /**
         * The ground an accent must clear to be usable in this mode.
         *
         * The palest one in dark, the palest one in light — clear that and
         * every other ground in the mode is easier.
         */
        fun hardest(dark: Boolean): Color =
            (if (dark) darks else lights).maxByOrNull { luminance(it.base) }!!.base
    }
}

/**
 * How hard the edge of a card is drawn.
 *
 * Worth a control of its own because the right answer depends on the ground
 * and on the eyes reading it. On Pitch the border is the only thing holding a
 * card together, since the fill is gone; on Dusk a soft line all but
 * disappears into a ground that is already grey. [weight] is how far the
 * outline is mixed from the ground toward its ink.
 */
enum class Edges(val label: String, val note: String, val weight: Float) {
    None("None", "No lines. Cards separate by their fill.", 0f),
    Defined("Defined", "A clear line round every card.", 0.32f),
    Strong("Strong", "Heavy, for bright light or tired eyes.", 0.62f),
    ;

    /**
     * On a ground with no tile fill the border carries the whole card, so the
     * softest setting is lifted to the middle one rather than leaving cards
     * to float with nothing round them.
     */
    fun on(ground: Ground): Float =
        if (!ground.liftsTiles) maxOf(weight, Defined.weight) else weight
}

/**
 * Whether the theme in force is a dark one.
 *
 * Read from the ground actually in use, never from the system setting.
 * Anything that asks the system gets it wrong the moment someone forces
 * Light on a phone that is in dark mode -- which is how the activity badges
 * came to draw their dark inks on a light ground.
 */
val androidx.compose.material3.MaterialTheme.isDark: Boolean
    @androidx.compose.runtime.Composable get() = luminance(colorScheme.surface) < 0.18f

/** WCAG relative luminance. */
internal fun luminance(colour: Color): Float {
    val srgb = colour.convert(ColorSpaces.Srgb)
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(srgb.red) +
        0.7152f * channel(srgb.green) +
        0.0722f * channel(srgb.blue)
}

/** WCAG contrast, 1.0 for identical colours and 21.0 for black on white. */
internal fun contrast(a: Color, b: Color): Float {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * [ink] mixed into [ground] until it is just readable against it.
 *
 * This is how every derived neutral is built — the dimmest grey text, a
 * divider, the awake band in the sleep chart. Declaring them as constants is
 * what broke when the ground became a choice: `#2E2E42` was picked to sit
 * just off the old near-black and is 1.25:1 on Dusk, which is invisible.
 *
 * Binary search rather than a formula because luminance is not linear in the
 * mix, and twelve steps lands inside a hundredth of a ratio. It runs once per
 * theme, not per frame.
 */
internal fun mixToContrast(ground: Color, ink: Color, target: Float): Color {
    if (contrast(ground, ink) <= target) return ink
    var low = 0f
    var high = 1f
    repeat(12) {
        val mid = (low + high) / 2
        if (contrast(ground, lift(ground, ink, mid)) < target) low = mid else high = mid
    }
    return lift(ground, ink, high)
}

/**
 * A flat mix, for the lifts that are about depth rather than legibility.
 *
 * Component-wise in sRGB, deliberately, rather than Compose's [lerp]. That
 * one interpolates perceptually, and perceptual halfway off true black is
 * much darker than it sounds: a 0.14 lift came out at 1.05:1, which is a
 * sleep band you cannot see. These amounts were chosen to match the hand
 * -picked surfaces they replaced, and those were mixed in sRGB.
 */
internal fun lift(ground: Color, ink: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = ground.red + (ink.red - ground.red) * t,
        green = ground.green + (ink.green - ground.green) * t,
        blue = ground.blue + (ink.blue - ground.blue) * t,
    )
}

/**
 * An accent made safe for a ground, keeping its hue.
 *
 * The accent draws 1px chart strokes and 11px axis labels, so one that cannot
 * clear 4.5:1 breaks a promise the rest of the app keeps. Rejecting a colour
 * outright is rude and offering a "use anyway" escape is dishonest, so a
 * failing pick is lifted — or darkened, in light mode — until it passes, and
 * the screen shows what it landed on.
 *
 * Hue and saturation survive; only lightness moves, and only as far as it has
 * to. A colour that already passes is returned untouched.
 */
internal fun clampToGround(colour: Color, ground: Color, target: Float = 4.5f): Color {
    if (contrast(colour, ground) >= target) return colour
    // Toward white on a dark ground, toward black on a light one: the
    // direction that can actually reach the target.
    val toward = if (luminance(ground) < 0.18f) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(12) {
        val mid = (low + high) / 2
        if (contrast(lift(colour, toward, mid), ground) < target) low = mid else high = mid
    }
    val moved = lift(colour, toward, high)
    // If even the extreme cannot pass — a mid grey on a mid grey ground —
    // give the ground's own ink rather than something that merely looks
    // closer. Nothing in the app should be told it is legible when it is not.
    return if (contrast(moved, ground) >= target - 0.01f) moved else toward
}

/** True when a colour can be used as the accent on this ground untouched. */
internal fun passes(colour: Color, ground: Color, target: Float = 4.5f): Boolean =
    contrast(colour, ground) >= target

/**
 * How far two colours sit apart in hue, in degrees, 0 to 180.
 *
 * Used to keep an accent off the health colours. The charts put unlike things
 * side by side and hue is how you tell which is which, so an accent that
 * lands on one of them makes the chrome look like a reading.
 */
internal fun hueGap(a: Color, b: Color): Float {
    val gap = abs(hueOf(a) - hueOf(b))
    return if (gap > 180f) 360f - gap else gap
}

internal fun hueOf(colour: Color): Float {
    val c = colour.convert(ColorSpaces.Srgb)
    val top = max(c.red, max(c.green, c.blue))
    val bottom = min(c.red, min(c.green, c.blue))
    val span = top - bottom
    if (span < 0.0001f) return 0f
    val hue = when (top) {
        c.red -> 60f * (((c.green - c.blue) / span) % 6f)
        c.green -> 60f * (((c.blue - c.red) / span) + 2f)
        else -> 60f * (((c.red - c.green) / span) + 4f)
    }
    return (hue + 360f) % 360f
}
