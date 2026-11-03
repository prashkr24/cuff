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
import com.srineere.airbridge.RelayContacts
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
    data object DefaultAppCall : VibrationTarget
    data class App(val packageName: String, val calls: Boolean) : VibrationTarget
    data class Caller(val id: String) : VibrationTarget

    fun encode(): String = when (this) {
        Default -> "default"
        DefaultCall -> "defaultCall"
        DefaultAppCall -> "defaultAppCall"
        is App -> "app|$packageName|${if (calls) "call" else "message"}"
        is Caller -> "caller|$id"
    }

    companion object {
        fun decode(raw: String): VibrationTarget {
            val parts = raw.split('|')
            return when (parts.firstOrNull()) {
                "defaultCall" -> DefaultCall
                "defaultAppCall" -> DefaultAppCall
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

    /**
     * Whether the warning behind Only these people ring has been put away for
     * good. Chrome rather than a relay rule, so it sits beside the outcome and
     * not in the config the service reads.
     */
    val explainedOnlySaved: Boolean = context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
        .getBoolean("explained_only_saved", false)

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
            .take(60)
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
        VibrationTarget.DefaultAppCall -> config.defaultAppCallPreset
        is VibrationTarget.App ->
            if (target.calls) config.apps[target.packageName]?.callPreset
            else config.apps[target.packageName]?.preset
        is VibrationTarget.Caller -> config.callers[target.id]?.preset
    }

    /** What this target falls back to when it has no rule of its own. */
    fun inherited(target: VibrationTarget): RelayPreset = when (target) {
        VibrationTarget.Default -> config.defaultPreset
        VibrationTarget.DefaultCall -> config.defaultCallPreset
        VibrationTarget.DefaultAppCall -> config.defaultAppCallPreset
        is VibrationTarget.App ->
            if (target.calls) config.defaultAppCallPreset else config.defaultPreset
        is VibrationTarget.Caller -> {
            val caller = config.callers[target.id]
            config.apps[caller?.packageName]?.callPreset ?: config.defaultCallPreset
        }
    }

    fun setPreset(target: VibrationTarget, preset: RelayPreset) = update { config ->
        when (target) {
            VibrationTarget.Default -> config.copy(defaultPreset = preset)
            VibrationTarget.DefaultCall -> config.copy(defaultCallPreset = preset)
            VibrationTarget.DefaultAppCall -> config.copy(defaultAppCallPreset = preset)
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

    /**
     * The phone's own address book, so a rule can be made before someone's
     * first call. Asked per app because identity differs: the dialer names a
     * caller by number, WhatsApp only by display name.
     */
    fun contacts(packageName: String): List<Pair<String, SavedCaller>> =
        RelayContacts.list(context, packageName).map { row ->
            val id = row["id"].orEmpty()
            val saved = config.callers[id] ?: SavedCaller(
                packageName = packageName,
                label = row["label"].orEmpty(),
                detail = row["detail"]?.takeIf { it.isNotBlank() },
            )
            known[id] = saved.copy(preset = null, ring = null)
            id to saved
        }

    /**
     * Someone we can name: saved, heard ringing, or read from the address
     * book. Anything that puts a person on screen has to be able to find
     * them again, or their own screen opens calling them "Caller".
     */
    fun caller(id: String): SavedCaller? = config.callers[id]
        ?: recent.firstOrNull { it.first == id }?.second
        ?: known[id]

    private fun remember(id: String): SavedCaller? = caller(id)

    /**
     * A name for an identity, falling back to the address book.
     *
     * Without that fallback a name only lasts as long as the relay does:
     * someone who rang but was never given a rule is held in memory alone, so
     * yesterday's activity would forget who it was about. The book is read
     * once per process and only when something is still unnamed.
     */
    fun callerLabel(id: String): String? = caller(id)?.label ?: fromBook(id)

    private fun fromBook(id: String): String? {
        val book = addressBook ?: buildAddressBook().also { addressBook = it }
        return book[id]
    }

    private fun buildAddressBook(): Map<String, String> {
        if (!RelayContacts.granted(context)) return emptyMap()
        val sources = listOf(dialer) + RelayContacts.NAMED_CALLERS
        return sources.flatMap { packageName ->
            RelayContacts.list(context, packageName).mapNotNull { row ->
                val id = row["id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = row["label"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to label
            }
        }.toMap()
    }

    /**
     * Puts someone back on the default rather than freezing today's default
     * onto them, which is what picking its current shape by hand would do.
     */
    fun clearPreset(target: VibrationTarget) = update { config ->
        when (target) {
            is VibrationTarget.Caller -> config.copy(callers = config.callers - target.id)
            is VibrationTarget.App -> {
                val rule = config.apps[target.packageName] ?: return@update config
                config.copy(
                    apps = config.apps + (target.packageName to
                        if (target.calls) rule.copy(callPreset = null) else rule.copy(preset = null))
                )
            }
            else -> config
        }
    }

    fun callers(packageName: String?): List<Pair<String, SavedCaller>> =
        config.callers.entries
            .filter { packageName == null || it.value.packageName == packageName }
            .sortedBy { it.value.label.lowercase() }
            .map { it.key to it.value }

    /**
     * The people in one of the three groups. Scoped to one app where the
     * screen asking is about that app -- Calls is the phone, so a rule for
     * someone's WhatsApp has no business being listed under it.
     */
    fun group(group: CallerGroup, packageName: String? = null): List<Pair<String, SavedCaller>> {
        val saved = config.callers.entries
            .filter { packageName == null || it.value.packageName == packageName }
            .sortedBy { it.value.label.lowercase() }
            .map { it.key to it.value }
        return when (group) {
            CallerGroup.Own -> saved.filter { it.second.preset?.silent == false }
            CallerGroup.Silenced -> saved.filter { it.second.preset?.silent == true }
            // Saved-but-unchanged and never-saved both mean "uses the
            // default", so they read as one list.
            CallerGroup.Recent -> saved.filter { it.second.preset == null } +
                recent.filter { packageName == null || it.second.packageName == packageName }
        }
    }

    fun forgetCaller(id: String) = update { it.copy(callers = it.callers - id) }

    fun setOnlySavedCallers(only: Boolean) = update { it.copy(onlySavedCallers = only) }

    /** Taken only when the switch is actually turned on, not when it is cancelled. */
    fun rememberOnlySavedExplained() {
        context.getSharedPreferences("calendar_relay", Context.MODE_PRIVATE)
            .edit().putBoolean("explained_only_saved", true).apply()
        changed()
    }

    // ----------------------------------------------------------------- relay

    fun start() {
        runCatching { CalendarRelayService.start(context) }
        changed()
    }

    fun stop() {
        CalendarRelayService.stop()
        changed()
    }

    /**
     * Quiet on the wrist until this moment. -1 means until it is resumed by
     * hand, 0 means not paused at all.
     */
    fun pauseUntil(atMillis: Long) = update { config ->
        config.copy(silence = config.silence.copy(pauseUntil = atMillis))
    }

    fun setQuiet(enabled: Boolean) = update { config ->
        config.copy(silence = config.silence.copy(quietEnabled = enabled))
    }

    /**
     * Both ends at once, because the rules refuse a window that ends when it
     * starts and setting one end at a time would walk through that state.
     */
    fun setQuietWindow(startMinute: Int, endMinute: Int) = update { config ->
        config.copy(
            silence = config.silence.copy(startMinute = startMinute, endMinute = endMinute)
        )
    }

    companion object {
        /**
         * People we have put on screen but not written down. Held here rather
         * than in the config so that naming someone is not the same as
         * deciding something about them.
         */
        private val known = mutableMapOf<String, SavedCaller>()

        /** The address book, as identities. Read once; it rarely changes. */
        private var addressBook: Map<String, String>? = null

        /**
         * What reached the band, and what did not. A ring also opens and
         * closes, which the relay records because it has to know; but one
         * phone call is one thing that happened, and the wearer did not feel
         * it start, repeat and end as three separate events.
         */
        private val SHOWN_STAGES = setOf("verified", "failed")
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
