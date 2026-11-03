package com.srineere.airbridge.ui

import android.content.Context
import com.srineere.airbridge.ui.theme.Accent
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.isAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Device › Appearance holds. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val accent: Accent = Accent.Cyan,
)

/**
 * The look of the app, kept where both the Activity and any future widget can
 * read it. Small enough to stay synchronous: a theme that arrives one frame
 * late is a theme that flashes.
 */
object AppSettings {
    private const val PREFS = "air_appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_ACCENT = "accent"

    private val state = MutableStateFlow(Appearance())
    val appearance: StateFlow<Appearance> = state.asStateFlow()

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getString(KEY_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.System
        val accent = prefs.getString(KEY_ACCENT, null)
            ?.let { name -> Accent.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.isAvailable }
            ?: Accent.Cyan
        state.value = Appearance(mode, accent)
    }

    fun setMode(context: Context, mode: ThemeMode) = update(context) { it.copy(mode = mode) }

    fun setAccent(context: Context, accent: Accent) {
        if (!accent.isAvailable) return
        update(context) { it.copy(accent = accent) }
    }

    private fun update(context: Context, change: (Appearance) -> Appearance) {
        val next = change(state.value)
        state.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, next.mode.name)
            .putString(KEY_ACCENT, next.accent.name)
            .apply()
    }
}
