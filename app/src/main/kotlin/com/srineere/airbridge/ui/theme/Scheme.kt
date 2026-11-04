package com.srineere.airbridge.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Everything the app draws, derived from one ground and one accent.
 *
 * The neutrals used to be twenty constants, which was fine while there was
 * one ground in each mode. The moment the ground became a choice they turned
 * into twenty wrong answers: `sleepAwake` was picked to sit just off the old
 * near-black and is 1.25:1 on Dusk, and `disabled` drops from 4.57:1 to
 * 3.95:1 on the same ground. Both are now mixed from whatever ground is
 * actually in use, which is the only way they can keep a promise about
 * contrast.
 *
 * The six health colours do NOT move. A steps green that drifts with the
 * ground is a steps green nobody can learn.
 */
internal fun schemeFor(ground: Ground, accent: Color, edges: Edges): ColorScheme {
    val base = ground.base
    val ink = ground.ink

    // A card lifts off the ground, except where it cannot: on true black a
    // lift is either invisible or relights the pixels the ground turned off,
    // so there the card is its border and nothing else.
    val lifts = ground.liftsTiles
    val container = if (lifts) lift(base, ink, 0.055f) else base
    val containerHigh = lift(base, ink, if (lifts) 0.105f else 0.06f)
    val containerLowest = if (lifts) lift(base, ink, 0.028f) else base

    val outline = lift(base, ink, edges.on(ground))
    val outlineVariant = lift(base, ink, edges.on(ground) * 0.62f)

    // The accent is clamped against the ground it will actually sit on, not
    // against the mode's hardest — a preset already clears that, and a custom
    // colour should not be darkened further than this ground requires.
    val primary = clampToGround(accent, base)
    val onPrimary = if (luminance(primary) > 0.45f) Color(0xFF101418) else Color(0xFFF7FAFC)
    val primaryContainer = lift(base, primary, 0.14f)
    val onPrimaryContainer = clampToGround(primary, primaryContainer, target = 4.5f)

    val variant = mixToContrast(base, ink, 6.5f)
    return if (ground.dark) {
        darkScheme(
            primary, onPrimary, primaryContainer, onPrimaryContainer,
            base, ink, container, containerHigh, containerLowest,
            variant, outline, outlineVariant,
        )
    } else {
        lightScheme(
            primary, onPrimary, primaryContainer, onPrimaryContainer,
            base, ink, container, containerHigh, containerLowest,
            variant, outline, outlineVariant,
        )
    }
}

/**
 * The same argument list for both builders.
 *
 * Material's two factories take the same names in different orders, so the
 * roles are named here once rather than written out twice and allowed to
 * drift apart.
 */
private fun darkScheme(
    primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    surface: Color, onSurface: Color, container: Color, containerHigh: Color, containerLowest: Color,
    onSurfaceVariant: Color, outline: Color, outlineVariant: Color,
): ColorScheme = androidx.compose.material3.darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = primary,
    onSecondary = onPrimary,
    surface = surface,
    onSurface = onSurface,
    surfaceContainerLowest = containerLowest,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = lift(surface, onSurface, 0.15f),
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
)

private fun lightScheme(
    primary: Color, onPrimary: Color, primaryContainer: Color, onPrimaryContainer: Color,
    surface: Color, onSurface: Color, container: Color, containerHigh: Color, containerLowest: Color,
    onSurfaceVariant: Color, outline: Color, outlineVariant: Color,
): ColorScheme = androidx.compose.material3.lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = primary,
    onSecondary = onPrimary,
    surface = surface,
    onSurface = onSurface,
    surfaceContainerLowest = containerLowest,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = lift(surface, onSurface, 0.15f),
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
)

/**
 * The colours Material has no role for, for one ground.
 *
 * Three of these are mixed from the ground and the rest are fixed. The
 * mixed ones are the ones whose job is to be *just* visible against it —
 * get those from a constant and they vanish the moment the ground moves.
 */
internal fun extrasFor(ground: Ground, accent: Color, edges: Edges): AirExtraColors {
    val base = ground.base
    val ink = ground.ink
    val container = if (ground.liftsTiles) lift(base, ink, 0.055f) else base
    val fixed = if (ground.dark) DarkExtras else LightExtras
    return fixed.copy(
        // 4.5:1 rather than a constant: the old value was a hair under AA on
        // its own ground and well under it on a dimmer one.
        disabled = mixToContrast(base, ink, 4.5f),
        navSurface = if (ground.liftsTiles) lift(base, ink, 0.028f) else base,
        accentOutline = lift(base, clampToGround(accent, base), 0.32f + edges.on(ground) * 0.3f),
        // The band behind a night's sleep. Fixed, it was 1.45:1 on Ink and
        // 1.25:1 on Dusk -- a stage of the chart you could not see.
        // To a ratio, not a fixed mix: on true black there is no tile fill
        // to lift from, so a flat 14% lands at 1.05:1 and the band vanishes.
        sleepAwake = mixToContrast(container, ink, 1.35f),
    )
}
