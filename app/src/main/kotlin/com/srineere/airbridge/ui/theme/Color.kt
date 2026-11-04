package com.srineere.airbridge.ui.theme

import androidx.compose.ui.graphics.Color

// Every colour the app draws is one of these roles. Nothing below this file
// names a hex value, which is what makes the theme switch and the accent
// picker a single swap rather than a hunt through composables.

// Neutrals — dark
internal val DarkSurface = Color(0xFF0F1316)          // the screen itself
internal val DarkSurfaceContainer = Color(0xFF161C21) // cards
internal val DarkSurfaceContainerHigh = Color(0xFF1E262B)
internal val DarkSurfaceContainerLowest = Color(0xFF11161A) // nav bar
internal val DarkOnSurface = Color(0xFFE4E9EC)
internal val DarkOnSurfaceVariant = Color(0xFF8FA3AD)  // secondary text
internal val DarkOutline = Color(0xFF222B31)           // card borders
internal val DarkOutlineVariant = Color(0xFF1C2429)    // dividers
internal val DarkDisabled = Color(0xFF6E7E87)

// Neutrals — light
internal val LightSurface = Color(0xFFF6F9FA)
internal val LightSurfaceContainer = Color(0xFFFFFFFF)
internal val LightSurfaceContainerHigh = Color(0xFFE7F1F5)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF161D21)
internal val LightOnSurfaceVariant = Color(0xFF5B6B74)
internal val LightOutline = Color(0xFFE2E8EB)
internal val LightOutlineVariant = Color(0xFFE4EAEC)
internal val LightDisabled = Color(0xFF9AAAB3)

/**
 * One accent, in both themes. [container] is the tinted panel behind a live
 * state — a glow in dark, a flat tint in light — and [onContainer] is the text
 * that sits on it.
 */
internal data class AccentColors(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val containerOutline: Color,
)

internal val CyanDark = AccentColors(
    primary = Color(0xFF6FD3E8),
    onPrimary = Color(0xFF07242B),
    container = Color(0xFF15313A),
    onContainer = Color(0xFFBDE6F2),
    containerOutline = Color(0xFF1E4C59),
)
internal val CyanLight = AccentColors(
    primary = Color(0xFF0E7490),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFDFF1F7),
    onContainer = Color(0xFF0B3B48),
    containerOutline = Color(0xFFB4DCE9),
)

internal val GreenDark = AccentColors(
    primary = Color(0xFF5BD6A8),
    onPrimary = Color(0xFF05271C),
    container = Color(0xFF14332A),
    onContainer = Color(0xFFB4EBD4),
    containerOutline = Color(0xFF1E5041),
)
internal val GreenLight = AccentColors(
    primary = Color(0xFF11875F),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFDCF2E6),
    onContainer = Color(0xFF0A3F2C),
    containerOutline = Color(0xFFAFDCC6),
)

internal val VioletDark = AccentColors(
    primary = Color(0xFFB9A8E8),
    onPrimary = Color(0xFF1E1630),
    container = Color(0xFF261F38),
    onContainer = Color(0xFFDCD2F4),
    containerOutline = Color(0xFF3E3458),
)
internal val VioletLight = AccentColors(
    primary = Color(0xFF6246AF),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFEBE5F8),
    onContainer = Color(0xFF2E2252),
    containerOutline = Color(0xFFCFC3EC),
)

internal val AmberDark = AccentColors(
    primary = Color(0xFFE8C48F),
    onPrimary = Color(0xFF2B1F0C),
    container = Color(0xFF332A1C),
    onContainer = Color(0xFFF4E0C4),
    containerOutline = Color(0xFF57452C),
)
internal val AmberLight = AccentColors(
    primary = Color(0xFF8A5A14),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFF6EBDA),
    onContainer = Color(0xFF442C05),
    containerOutline = Color(0xFFE3CFAC),
)

internal val RoseDark = AccentColors(
    primary = Color(0xFFE8A6B4),
    onPrimary = Color(0xFF2E141C),
    container = Color(0xFF351F26),
    onContainer = Color(0xFFF6D3DB),
    containerOutline = Color(0xFF59333F),
)
internal val RoseLight = AccentColors(
    primary = Color(0xFFA33A58),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFF9E4E9),
    onContainer = Color(0xFF4B1424),
    containerOutline = Color(0xFFEDC5CF),
)

internal val SlateDark = AccentColors(
    primary = Color(0xFFA8B6C8),
    onPrimary = Color(0xFF161D26),
    container = Color(0xFF212A33),
    onContainer = Color(0xFFD6DEE8),
    containerOutline = Color(0xFF3A4654),
)
internal val SlateLight = AccentColors(
    primary = Color(0xFF445468),
    onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFE7ECF2),
    onContainer = Color(0xFF1E2831),
    containerOutline = Color(0xFFCAD4DF),
)

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
    disabled = DarkDisabled,
    navSurface = DarkSurfaceContainerLowest,
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
    disabled = LightDisabled,
    navSurface = LightSurfaceContainerLowest,
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
