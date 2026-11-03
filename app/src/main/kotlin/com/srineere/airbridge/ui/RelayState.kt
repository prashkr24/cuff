package com.srineere.airbridge.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.RelayActivity
import com.srineere.airbridge.RelayAppSelection
import com.srineere.airbridge.RelayHistory
import com.srineere.airbridge.RelayRecentCallers
import com.srineere.airbridge.RelayAppRule
import com.srineere.airbridge.RelayPreset
import com.srineere.airbridge.RelayRingSettings
import com.srineere.airbridge.RelayRuleConfig
import com.srineere.airbridge.RelayRuleStore
import com.srineere.airbridge.SavedCaller

/** One installed app, as the Apps screen needs it. */
internal data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val canCall: Boolean,
    val namesCallers: Boolean,
)

/**
 * What a vibration screen is editing. Encoded into the route so the screen can
 * be reached from several places without threading state through.
 */
internal sealed interface VibrationTarget {
    data object Default : VibrationTarget
    data object DefaultCall : VibrationTarget
    data class App(val packageName: String, val calls: Boolean) : VibrationTarget
    data class Caller(val id: String) : VibrationTarget

    fun encode(): String = when (this) {
        Default -> "default"
        DefaultCall -> "defaultCall"
        is App -> "app|$packageName|${if (calls) "call" else "message"}"
        is Caller -> "caller|$id"
    }

    companion object {
        fun decode(raw: String): VibrationTarget {
            val parts = raw.split('|')
            return when (parts.firstOrNull()) {
                "defaultCall" -> DefaultCall
                "app" -> App(parts.getOrElse(1) { "" }, parts.getOrNull(2) == "call")
                "caller" -> Caller(parts.drop(1).joinToString("|"))
                else -> Default
            }
        }
    }
}

/**
 * The app's view of the relay's stored rules.
 *
 * Everything underneath is SharedPreferences and synchronous, so rather than
 * mirror it into a second source of truth this re-reads on demand and bumps a
 * counter after every write. Small data, no staleness.
 */
internal class RelayState(private val context: Context, private val invalidate: () -> Unit) {
    val config: RelayRuleConfig = RelayRuleStore.read(context)
    val outcome: String? = context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        .getString("outcome", null)

    val apps: List<AppEntry> by lazy {
        RelayAppSelection.available(context).map { raw ->
            AppEntry(
                packageName = raw["packageName"].orEmpty(),
                label = raw["label"].orEmpty(),
                icon = raw["icon"]?.let { encoded ->
                    runCatching {
                        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                    }.getOrNull()
                },
                canCall = raw["calls"] == "true",
                namesCallers = raw["people"] == "true",
            )
        }
    }

    val history: List<RelayActivity> by lazy {
        // Only the moments worth showing: what actually reached the band.
        RelayHistory.read(context)
            .filter { it.stage in SHOWN_STAGES }
            .sortedByDescending { it.atMillis }
            .take(12)
    }

    fun label(packageName: String?): String = packageName?.let { name ->
        apps.firstOrNull { it.packageName == name }?.label ?: name.substringAfterLast('.')
    } ?: "Test vibration"

    // ------------------------------------------------------------- mutations

    private fun changed() = invalidate()

    fun update(change: (RelayRuleConfig) -> RelayRuleConfig) {
        RelayRuleStore.update(context, change)
        CalendarRelayService.active?.settingsChanged()
        changed()
    }

    /**
     * Whatever answers the phone here. Calls are a kind of alert rather than
     * an app, so this package is never shown: the Calls screen owns it.
     */
    val dialer: String = RelayAppSelection.dialer(context)

    val callsOn: Boolean get() = config.apps[dialer]?.enabled == true

    fun setCallsOn(on: Boolean) = setAppEnabled(dialer, on)

    fun setAppEnabled(packageName: String, enabled: Boolean) = update { config ->
        val existing = config.apps[packageName]
        config.copy(apps = config.apps + (packageName to (existing?.copy(enabled = enabled)
            ?: RelayAppRule(enabled))))
    }

    fun preset(target: VibrationTarget): RelayPreset? = when (target) {
        VibrationTarget.Default -> config.defaultPreset
        VibrationTarget.DefaultCall -> config.defaultCallPreset
        is VibrationTarget.App ->
            if (target.calls) config.apps[target.packageName]?.callPreset
            else config.apps[target.packageName]?.preset
        is VibrationTarget.Caller -> config.callers[target.id]?.preset
    }

    /** What this target falls back to when it has no rule of its own. */
    fun inherited(target: VibrationTarget): RelayPreset = when (target) {
        VibrationTarget.Default -> config.defaultPreset
        VibrationTarget.DefaultCall -> config.defaultCallPreset
        is VibrationTarget.App ->
            if (target.calls) config.defaultCallPreset else config.defaultPreset
        is VibrationTarget.Caller -> {
            val caller = config.callers[target.id]
            config.apps[caller?.packageName]?.callPreset ?: config.defaultCallPreset
        }
    }

    fun setPreset(target: VibrationTarget, preset: RelayPreset) = update { config ->
        when (target) {
            VibrationTarget.Default -> config.copy(defaultPreset = preset)
            VibrationTarget.DefaultCall -> config.copy(defaultCallPreset = preset)
            is VibrationTarget.App -> {
                val rule = config.apps[target.packageName]
                    ?: RelayAppRule(enabled = true)
                config.copy(
                    apps = config.apps + (target.packageName to
                        if (target.calls) rule.copy(callPreset = preset) else rule.copy(preset = preset))
                )
            }
            is VibrationTarget.Caller -> {
                val caller = config.callers[target.id] ?: remember(target.id)
                    ?: return@update config
                config.copy(callers = config.callers + (target.id to caller.copy(preset = preset)))
            }
        }
    }

    fun ring(target: VibrationTarget): RelayRingSettings = when (target) {
        is VibrationTarget.App -> config.apps[target.packageName]?.ring ?: config.ring
        is VibrationTarget.Caller -> {
            val caller = config.callers[target.id]
            caller?.ring ?: config.apps[caller?.packageName]?.ring ?: config.ring
        }
        else -> config.ring
    }

    fun setRing(target: VibrationTarget, settings: RelayRingSettings) = update { config ->
        when (target) {
            is VibrationTarget.App -> {
                val rule = config.apps[target.packageName]
                    ?: RelayAppRule(enabled = true)
                config.copy(apps = config.apps + (target.packageName to rule.copy(ring = settings)))
            }
            is VibrationTarget.Caller -> {
                val caller = config.callers[target.id] ?: remember(target.id)
                    ?: return@update config
                config.copy(callers = config.callers + (target.id to caller.copy(ring = settings)))
            }
            else -> config.copy(ring = settings)
        }
    }

    /**
     * Everyone who has rung but has no rule yet. They live in memory rather
     * than in the config, because remembering a stranger who rang once is not
     * the same as deciding something about them.
     */
    val recent: List<Pair<String, SavedCaller>>
        get() = RelayRecentCallers.all(config.callers.keys)
            .map { it.id to SavedCaller(it.packageName, it.label, detail = it.detail) }

    /** A recent caller, as the rule we would save for them. */
    private fun remember(id: String): SavedCaller? =
        recent.firstOrNull { it.first == id }?.second

    fun callers(packageName: String?): List<Pair<String, SavedCaller>> =
        config.callers.entries
            .filter { packageName == null || it.value.packageName == packageName }
            .sortedBy { it.value.label.lowercase() }
            .map { it.key to it.value }

    fun forgetCaller(id: String) = update { it.copy(callers = it.callers - id) }

    fun setOnlySavedCallers(only: Boolean) = update { it.copy(onlySavedCallers = only) }

    // ----------------------------------------------------------------- relay

    fun start() {
        runCatching { CalendarRelayService.start(context) }
        changed()
    }

    fun stop() {
        CalendarRelayService.stop()
        changed()
    }

    fun pause(paused: Boolean) = update { config ->
        config.copy(
            silence = config.silence.copy(
                pauseUntil = if (paused) System.currentTimeMillis() + 900_000 else 0,
            )
        )
    }

    companion object {
        private val SHOWN_STAGES = setOf("verified", "failed", "ring_started", "ring_stopped")
    }
}

/** The relay's live up or down, as the service publishes it. */
@Composable
internal fun relayRunning(): Boolean = CalendarRelayService.running.collectAsState().value

internal val LocalRelayState = staticCompositionLocalOf<RelayState> {
    error("No RelayState; wrap the screen in ProvideRelayState.")
}

/** Re-reads the stored rules whenever something writes to them. */
@Composable
internal fun rememberRelayState(context: Context): RelayState {
    var revision by remember { mutableIntStateOf(0) }
    return remember(revision) { RelayState(context) { revision++ } }
}
