package com.srineere.airbridge.ui.theme

import androidx.compose.ui.graphics.Color

// Every colour the app draws is one of these roles. Nothing below this file
// names a hex value, which is what makes the theme switch and the accent
// picker a single swap rather than a hunt through composables.

// The neutrals that used to live here -- twenty surfaces, outlines and
// greys -- are gone. They were right for the one ground that existed when
// they were written and wrong for every other, so they are mixed from
// whichever ground is in use instead. See Scheme.kt.

/**
 * Colours Material 3 has no role for. [positive] is "this is working" — the
 * relay dot, a battery bar, a resting rate trending the right way.
 */
data class AirExtraColors(
    val positive: Color,
    val disabled: Color,
    val navSurface: Color,
    /** Border of a live, accent-tinted card. Filled in per accent by the theme. */
    val accentOutline: Color = Color(0xFF1E4C59),
    // Sleep stages, darkest to lightest in the order they stack: deep at the
    // bottom of the bar, awake at the top. They differ in lightness as well
    // as hue, so the stack is still readable without colour vision.
    val sleepDeep: Color,
    val sleepLight: Color,
    val sleepRem: Color,
    val sleepAwake: Color,
    /**
     * One colour per health domain, fixed rather than following the accent.
     * The Activity screen already colours alert kinds this way and for the
     * same reason: these screens put unlike things side by side, and hue is
     * the fastest way to tell which is which. A tile's own colour can be
     * changed; these cannot, because the charts use them to mean something.
     */
    val heart: Color,
    val steps: Color,
    val activity: Color,
    val hrv: Color,
    val energy: Color,
    /** Heart rate zones, lightest effort to hardest. */
    val zoneLight: Color,
    val zoneModerate: Color,
    val zoneVigorous: Color,
    val zonePeak: Color,
)

internal val DarkExtras = AirExtraColors(
    positive = Color(0xFF5BD6A8),
    disabled = Color(0xFF6E7E87),
    navSurface = Color(0xFF11161A),
    sleepDeep = Color(0xFF3C3C6B),
    sleepLight = Color(0xFF5D5D92),
    sleepRem = Color(0xFF8E90C9),
    sleepAwake = Color(0xFF2E2E42),
    heart = Color(0xFFFF8FA3),
    steps = Color(0xFF5BD6A8),
    activity = Color(0xFF5FC2D6),
    hrv = Color(0xFFE8C48F),
    energy = Color(0xFFF2994A),
    zoneLight = Color(0xFF7FC9E8),
    zoneModerate = Color(0xFFE8C48F),
    zoneVigorous = Color(0xFFF2994A),
    zonePeak = Color(0xFFFF6B7F),
)

internal val LightExtras = AirExtraColors(
    positive = Color(0xFF11875F),
    disabled = Color(0xFF9AAAB3),
    navSurface = Color(0xFFFFFFFF),
    // Darkened to hold contrast on white, keeping the same order and the
    // same relative lightness so a stack reads the same way in both themes.
    sleepDeep = Color(0xFF3B3B78),
    sleepLight = Color(0xFF6A6AA8),
    sleepRem = Color(0xFF9A9CD4),
    sleepAwake = Color(0xFFC9CBDE),
    heart = Color(0xFFC2415C),
    steps = Color(0xFF11875F),
    activity = Color(0xFF1E7C92),
    hrv = Color(0xFF9A6B1E),
    energy = Color(0xFFBE6A16),
    zoneLight = Color(0xFF2A7FA6),
    zoneModerate = Color(0xFF9A6B1E),
    zoneVigorous = Color(0xFFBE6A16),
    zonePeak = Color(0xFFC2415C),
)
