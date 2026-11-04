package com.srineere.airbridge.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.srineere.airbridge.health.SleepStage
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.AppSettings
import com.srineere.airbridge.ui.theme.AirExtraColors
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.extrasFor
import com.srineere.airbridge.ui.theme.schemeFor

/**
 * The widget's colours, in both modes at once.
 *
 * A widget cannot ask what the wallpaper looks like and is redrawn by the
 * system, not by a composition, so it cannot watch a theme change either.
 * What it can do is carry both answers: a Glance [ColorProvider] built from
 * a day and a night colour is resolved by the launcher at draw time. So when
 * Appearance is set to System, every colour here holds both; when it is
 * pinned to Light or Dark, both halves are the same and the widget stops
 * following the phone, which is what pinning means.
 *
 * The light halves come from `LightExtras`, never from the dark ones. That
 * is not a nicety: the mint that carries steps on a dark tile is 1.81:1 on
 * white, which is not dim, it is gone.
 */
internal class WidgetColours(
    val background: ColorProvider,
    val onBackground: ColorProvider,
    val muted: ColorProvider,
    val faint: ColorProvider,
    val track: ColorProvider,
    val accent: ColorProvider,
    private val lightExtras: AirExtraColors,
    private val darkExtras: AirExtraColors,
    private val lightAccent: Color,
    private val darkAccent: Color,
    private val lightTrackOf: (Color) -> Color,
    private val darkTrackOf: (Color) -> Color,
    private val mode: ThemeMode,
) {
    /** A health colour, at full strength, in whichever mode is drawn. */
    fun tile(colour: TileColour): ColorProvider =
        provide(mode, extraOf(lightExtras, colour, lightAccent), extraOf(darkExtras, colour, darkAccent))

    /**
     * The same colour, stepped back for the part of a chart that is not
     * today. Mixed toward the card rather than made transparent: a
     * translucent bar over a wallpaper is a bar whose colour depends on the
     * photograph behind it.
     */
    fun tileDim(colour: TileColour): ColorProvider = provide(
        mode,
        lightTrackOf(extraOf(lightExtras, colour, lightAccent)),
        darkTrackOf(extraOf(darkExtras, colour, darkAccent)),
    )

    /** The sleep stages, which keep their own palette whatever is chosen. */
    fun sleepStage(stage: SleepStage): ColorProvider = provide(
        mode,
        when (stage) {
            SleepStage.Deep -> lightExtras.sleepDeep
            SleepStage.Light -> lightExtras.sleepLight
            SleepStage.Rem -> lightExtras.sleepRem
            SleepStage.Awake -> lightExtras.sleepAwake
        },
        when (stage) {
            SleepStage.Deep -> darkExtras.sleepDeep
            SleepStage.Light -> darkExtras.sleepLight
            SleepStage.Rem -> darkExtras.sleepRem
            SleepStage.Awake -> darkExtras.sleepAwake
        },
    )

    /** Green for a surplus of the good kind, and nothing else. */
    val positive: ColorProvider
        get() = provide(mode, lightExtras.positive, darkExtras.positive)
}

private fun extraOf(extras: AirExtraColors, colour: TileColour, accent: Color): Color = when (colour) {
    TileColour.Heart -> extras.heart
    TileColour.Sleep -> extras.sleepRem
    TileColour.Steps -> extras.steps
    TileColour.Hrv -> extras.hrv
    TileColour.Activity -> extras.activity
    TileColour.Energy -> extras.energy
    TileColour.Slate -> extras.disabled
    TileColour.Accent -> accent
}

private fun provide(mode: ThemeMode, light: Color, dark: Color): ColorProvider = when (mode) {
    ThemeMode.Light -> ColorProvider(light)
    ThemeMode.Dark -> ColorProvider(dark)
    ThemeMode.System -> ColorProvider(day = light, night = dark)
}

/**
 * Reads Appearance and builds both halves.
 *
 * [AppSettings.load] is called first because a widget may be drawn in a
 * process that has never opened the app -- the object's defaults would
 * otherwise draw somebody else's theme for one frame and then not correct
 * itself, since nothing recomposes a widget.
 */
internal fun widgetColours(context: Context): WidgetColours {
    AppSettings.load(context)
    val appearance = AppSettings.appearance.value

    val lightGround = appearance.lightGround
    val darkGround = appearance.darkGround
    val lightAccent = appearance.custom?.let { Color(it) } ?: appearance.accent.light
    val darkAccent = appearance.custom?.let { Color(it) } ?: appearance.accent.dark

    val lightScheme = schemeFor(lightGround, lightAccent, appearance.edges)
    val darkScheme = schemeFor(darkGround, darkAccent, appearance.edges)
    val lightExtras = extrasFor(lightGround, lightAccent, appearance.edges)
    val darkExtras = extrasFor(darkGround, darkAccent, appearance.edges)

    // A widget sits on a wallpaper, not on the app's ground, so its card has
    // to be visible against a photograph. Where the ground does not lift its
    // cards -- true black, pure white -- the widget takes the next step up
    // rather than disappearing into whatever is behind it.
    val lightCard =
        if (lightGround.liftsTiles) lightScheme.surfaceContainer else lightScheme.surfaceContainerHigh
    val darkCard =
        if (darkGround.liftsTiles) darkScheme.surfaceContainer else darkScheme.surfaceContainerHigh

    val mode = appearance.mode
    return WidgetColours(
        background = provide(mode, lightCard, darkCard),
        onBackground = provide(mode, lightScheme.onSurface, darkScheme.onSurface),
        muted = provide(mode, lightScheme.onSurfaceVariant, darkScheme.onSurfaceVariant),
        faint = provide(mode, lightExtras.disabled, darkExtras.disabled),
        track = provide(
            mode,
            mixToward(lightScheme.onSurface, lightCard, 0.86f),
            mixToward(darkScheme.onSurface, darkCard, 0.86f),
        ),
        accent = provide(mode, lightScheme.primary, darkScheme.primary),
        lightExtras = lightExtras,
        darkExtras = darkExtras,
        lightAccent = lightScheme.primary,
        darkAccent = darkScheme.primary,
        lightTrackOf = { mixToward(it, lightCard, 0.55f) },
        darkTrackOf = { mixToward(it, darkCard, 0.55f) },
        mode = mode,
    )
}

/**
 * Mixes in sRGB, component by component.
 *
 * Not Compose's `lerp`, which interpolates perceptually: on a true-black
 * card a perceptual mix of 14% lands at 1.05:1, which is a bar you cannot
 * see. The app's own palette made this mistake once and a contrast test
 * caught it; the same arithmetic is repeated here rather than imported,
 * because `lift` takes a ground and an ink and this takes two arbitrary
 * colours.
 */
internal fun mixToward(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = 1f,
)
