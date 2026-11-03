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

/**
 * Colours Material 3 has no role for. [positive] is "this is working" — the
 * relay dot, a battery bar, a resting rate trending the right way.
 */
internal data class AirExtraColors(
    val positive: Color,
    val disabled: Color,
    val navSurface: Color,
    val sleepAwake: Color,
    val sleepLight: Color,
    val sleepDeep: Color,
)

internal val DarkExtras = AirExtraColors(
    positive = Color(0xFF5BD6A8),
    disabled = DarkDisabled,
    navSurface = DarkSurfaceContainerLowest,
    sleepAwake = Color(0xFF3A3A4E),
    sleepLight = Color(0xFF6E6EA8),
    sleepDeep = Color(0xFFB9BCE8),
)

internal val LightExtras = AirExtraColors(
    positive = Color(0xFF11875F),
    disabled = LightDisabled,
    navSurface = LightSurfaceContainerLowest,
    sleepAwake = Color(0xFFC9CBDE),
    sleepLight = Color(0xFF8A8AC4),
    sleepDeep = Color(0xFF5B5B9E),
)
