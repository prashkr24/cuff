package com.srineere.airbridge.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.srineere.airbridge.ui.theme.Accent
import com.srineere.airbridge.ui.theme.Edges
import com.srineere.airbridge.ui.theme.Ground
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.ThemePreset
import com.srineere.airbridge.ui.theme.isAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Appearance holds.
 *
 * Both grounds are stored, not one. Someone on System crosses midday into the
 * other mode, and a half-chosen theme would leave them somewhere nobody
 * picked.
 */
data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val accent: Accent = Accent.Azure,
    val darkGround: Ground = Ground.Ink,
    val lightGround: Ground = Ground.Paper,
    val edges: Edges = Edges.Defined,
    /** An accent from the picker, which wins over [accent] while it is set. */
    val custom: Int? = null,
) {
    fun ground(dark: Boolean): Ground = if (dark) darkGround else lightGround

    /** The accent actually drawn, custom first. */
    fun colour(dark: Boolean): Color =
        custom?.let { Color(it) } ?: accent.colour(dark)

    /** The preset these add up to, or null — which the screen calls Custom. */
    val preset: ThemePreset?
        get() = if (custom != null) null
        else ThemePreset.matching(darkGround, lightGround, accent)
}

/**
 * The look of the app, kept where both the Activity and any future widget can
 * read it. Small enough to stay synchronous: a theme that arrives one frame
 * late is a theme that flashes.
 */
object AppSettings {
    private const val PREFS = "air_appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_ACCENT = "accent"
    private const val KEY_DARK_GROUND = "dark_ground"
    private const val KEY_LIGHT_GROUND = "light_ground"
    private const val KEY_EDGES = "edges"
    private const val KEY_CUSTOM = "custom_accent"

    private val state = MutableStateFlow(Appearance())
    val appearance: StateFlow<Appearance> = state.asStateFlow()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun <T : Enum<T>> read(key: String, all: List<T>, fallback: T): T =
            prefs.getString(key, null)?.let { name -> all.firstOrNull { it.name == name } } ?: fallback

        // The accent enum was replaced outright when three of its values
        // turned out to be the same hex as a health colour. An old name no
        // longer matches anything, so it falls back to the default rather
        // than to a colour nobody can name.
        val accent = read(KEY_ACCENT, Accent.entries, Accent.Azure).takeIf { it.isAvailable } ?: Accent.Azure
        val dark = read(KEY_DARK_GROUND, Ground.darks, Ground.Ink)
        val light = read(KEY_LIGHT_GROUND, Ground.lights, Ground.Paper)
        state.value = Appearance(
            mode = read(KEY_MODE, ThemeMode.entries, ThemeMode.System),
            accent = accent,
            darkGround = if (dark.dark) dark else Ground.Ink,
            lightGround = if (!light.dark) light else Ground.Paper,
            edges = read(KEY_EDGES, Edges.entries, Edges.Defined),
            custom = prefs.getInt(KEY_CUSTOM, 0).takeIf { it != 0 },
        )
    }

    fun setMode(context: Context, mode: ThemeMode) = update(context) { it.copy(mode = mode) }

    /** Choosing a named accent is also how a custom one is given up. */
    fun setAccent(context: Context, accent: Accent) {
        if (!accent.isAvailable) return
        update(context) { it.copy(accent = accent, custom = null) }
    }

    fun setCustom(context: Context, colour: Color) =
        update(context) { it.copy(custom = colour.toArgb()) }

    fun setGround(context: Context, ground: Ground) = update(context) {
        if (ground.dark) it.copy(darkGround = ground) else it.copy(lightGround = ground)
    }

    fun setEdges(context: Context, edges: Edges) = update(context) { it.copy(edges = edges) }

    /** A preset writes every field it names, including giving up a custom accent. */
    fun setPreset(context: Context, preset: ThemePreset) = update(context) {
        it.copy(
            accent = preset.accent,
            darkGround = preset.darkGround,
            lightGround = preset.lightGround,
            custom = null,
        )
    }

    private fun update(context: Context, change: (Appearance) -> Appearance) {
        val next = change(state.value)
        state.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, next.mode.name)
            .putString(KEY_ACCENT, next.accent.name)
            .putString(KEY_DARK_GROUND, next.darkGround.name)
            .putString(KEY_LIGHT_GROUND, next.lightGround.name)
            .putString(KEY_EDGES, next.edges.name)
            .putInt(KEY_CUSTOM, next.custom ?: 0)
            .apply()
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255 + 0.5f).toInt(),
    (red * 255 + 0.5f).toInt(),
    (green * 255 + 0.5f).toInt(),
    (blue * 255 + 0.5f).toInt(),
)
