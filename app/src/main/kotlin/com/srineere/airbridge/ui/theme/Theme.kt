package com.srineere.airbridge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** What the user picked under Device › Appearance. */
enum class ThemeMode { Light, Dark, System }

/**
 * The accent swatches on Device. [Dynamic] follows the wallpaper and is only
 * offered on Android 12 and up, where the platform can supply it.
 */
enum class Accent { Dynamic, Cyan, Green, Violet, Amber }

val Accent.isAvailable: Boolean
    get() = this != Accent.Dynamic || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

internal val LocalAirExtras = staticCompositionLocalOf { DarkExtras }

/** Colours Material 3 has no role for. See [AirExtraColors]. */
val MaterialTheme.extras: AirExtraColors
    @Composable get() = LocalAirExtras.current

private fun accentColors(accent: Accent, dark: Boolean): AccentColors = when (accent) {
    Accent.Green -> if (dark) GreenDark else GreenLight
    Accent.Violet -> if (dark) VioletDark else VioletLight
    Accent.Amber -> if (dark) AmberDark else AmberLight
    // Dynamic never reaches here: it is resolved from the platform below, and
    // falls back to Cyan on the versions that cannot supply it.
    Accent.Cyan, Accent.Dynamic -> if (dark) CyanDark else CyanLight
}

private fun darkScheme(accent: AccentColors): ColorScheme = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    secondary = accent.primary,
    onSecondary = accent.onPrimary,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private fun lightScheme(accent: AccentColors): ColorScheme = lightColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    secondary = accent.primary,
    onSecondary = accent.onPrimary,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

@Composable
fun AirBridgeTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Cyan,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val wallpaper = accent == Accent.Dynamic && Accent.Dynamic.isAvailable

    val scheme = when {
        wallpaper && dark -> dynamicDarkColorScheme(context)
        wallpaper -> dynamicLightColorScheme(context)
        dark -> darkScheme(accentColors(accent, dark = true))
        else -> lightScheme(accentColors(accent, dark = false))
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The bars are drawn behind; only their icon contrast changes.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !dark
        }
    }

    val extras = (if (dark) DarkExtras else LightExtras).copy(
        accentOutline = if (wallpaper) scheme.outline else accentColors(accent, dark).containerOutline,
    )

    CompositionLocalProvider(LocalAirExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AirTypography,
            content = content,
        )
    }
}
