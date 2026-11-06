package com.srineere.airbridge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** What the user picked under Device › Appearance. */
enum class ThemeMode { Light, Dark, System }

internal val LocalAirExtras = staticCompositionLocalOf { DarkExtras }

/** Colours Material 3 has no role for. See [AirExtraColors]. */
val MaterialTheme.extras: AirExtraColors
    @Composable get() = LocalAirExtras.current

@Composable
fun AirBridgeTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Accent = Accent.Rose,
    ground: Ground? = null,
    edges: Edges = Edges.Defined,
    custom: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val on = ground ?: if (dark) Ground.Ink else Ground.Paper

    // The wallpaper gives a hue, not a scheme.
    //
    // dynamicDarkColorScheme returns the platform's surfaces as well as its
    // accent, so taking it wholesale would silently overwrite whichever
    // ground had just been chosen -- "true black" plus "match my wallpaper"
    // would quietly stop being true black. Reading one colour out of it and
    // building the rest ourselves keeps both features.
    val wanted = remember(accent, custom, dark, context) {
        custom ?: if (accent == Accent.Dynamic && Accent.Dynamic.isAvailable) {
            if (dark) dynamicDarkColorScheme(context).primary
            else dynamicLightColorScheme(context).primary
        } else {
            accent.colour(dark)
        }
    }

    // Remembered because LocalColorScheme is static: a fresh instance on every
    // recomposition invalidates every composable that reads a colour, which is
    // all of them. Nothing here changes unless one of these does.
    val scheme = remember(on, wanted, edges) { schemeFor(on, wanted, edges) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // The bars are drawn behind; only their icon contrast changes.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !dark
        }
    }

    val extras = remember(on, wanted, edges) { extrasFor(on, wanted, edges) }

    CompositionLocalProvider(LocalAirExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AirTypography,
            content = content,
        )
    }
}
