package com.srineere.airbridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayPattern
import com.srineere.airbridge.RelayPreset
import java.util.concurrent.TimeUnit

@Composable
internal fun AlertsScreen(onOpen: (String) -> Unit) {
    val state = LocalRelayState.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Alerts")

        RelayStatusCard(state, onOpen)

        // Under the switch, because both answer the same question at
        // different lengths: off for good, quiet for the night.
        SettingsRow(
            title = "Schedule",
            subtitle = scheduleSummary(state),
            icon = Icons.Outlined.Schedule,
            onClick = { onOpen("schedule") },
        )

        SectionHeader("What gets through")

        SettingsRow(
            title = "Calls",
            subtitle = callsSummary(state),
            icon = Icons.Outlined.Call,
            onClick = { onOpen("calls") },
        )
        SettingsRow(
            title = "Apps",
            subtitle = appsSummary(state),
            icon = Icons.Outlined.Apps,
            onClick = { onOpen("apps") },
        )
        SettingsRow(
            title = "People",
            subtitle = peopleSummary(state),
            icon = Icons.Outlined.People,
            onClick = { onOpen("people") },
        )
        if (state.history.isNotEmpty()) {
            // The newest few answer "did that buzz come from what I think it
            // did"; the rest is for when it did not, and lives behind this.
            SectionHeader(
                text = "Recent",
                action = if (state.history.size > RECENT) "Show all" else null,
                onAction = { onOpen("activity") },
            )
            state.history.take(RECENT).forEach { entry -> ActivityRow(entry, state) }
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One switch, not three buttons. Start, Pause and Stop asked the wearer to
 * hold a state machine in their head for something that is either relaying or
 * not; pause survives as the notification's own action, where a fifteen minute
 * silence is what is actually wanted.
 */
@Composable
private fun RelayStatusCard(state: RelayState, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val running = relayRunning()
    val blocking = remember(resumeTicks()) { blockingGrants(context) }
    val paused = state.config.silence.reason(System.currentTimeMillis()) != null

    StatusToggle(
        title = when {
            // No subtext, so anything that has to be said is said here or not
            // at all.
            blocking > 0 && !running -> "$blocking thing${if (blocking == 1) "" else "s"} to allow"
            running && paused -> "Paused"
            running -> "Relaying"
            else -> "Not relaying"
        },
        live = running,
        onToggle = { wanted ->
            when {
                // It cannot start, so the switch takes you to the reason
                // instead of pretending to flip.
                wanted && blocking > 0 -> onOpen("permissions")
                wanted -> state.start()
                else -> state.stop()
            }
        },
    )
}

/**
 * What the Calls row says without being opened: how a call rings, and the one
 * exception worth knowing from here. Strength, and a count of people who
 * merely buzz differently, belong on the screen itself -- a row that lists
 * every setting is a worse copy of the screen behind it.
 */
private fun callsSummary(state: RelayState): String {
    if (!state.callsOn) return "Off"
    val ring = shape(state.config.defaultCallPreset)
    val silenced = state.config.callers.count { it.value.preset?.silent == true }
    return if (silenced == 0) ring else "$ring · $silenced silenced"
}

/** A count, not a list: the names were a worse copy of the Apps screen. */
private fun appsSummary(state: RelayState): String = when (val count = (state.config.selectedPackages - state.dialer).size) {
    0 -> "No apps chosen yet"
    1 -> "1 app"
    else -> "$count apps"
}

/** The pattern on its own, where there is no room for how hard it buzzes. */
internal fun shape(preset: RelayPreset): String = when (preset.pattern) {
    RelayPattern.SILENT -> "Silent"
    RelayPattern.ONE -> "One pulse"
    RelayPattern.TWO -> "Two pulses"
    RelayPattern.RING -> "Rings until answered"
}

internal fun describe(preset: RelayPreset): String {
    if (preset.pattern == RelayPattern.SILENT) return "Silent"
    val strength = if (preset.strength == RelayPreset.FULL_STRENGTH) "strong" else "light"
    return "${shape(preset)} · $strength"
}

private fun describeActivity(stage: String, errorCode: String?, preset: String?): String {
    val shape = preset?.let { runCatching { describe(RelayPreset.parse(it)) }.getOrNull() }
    return when (stage) {
        "verified" -> shape ?: "Buzzed"
        "ring_started" -> "Started ringing"
        "ring_stopped" -> "Ring ended"
        else -> errorCode?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Did not reach the band"
    }
}

private fun ago(atMillis: Long): String {
    val delta = (System.currentTimeMillis() - atMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours hr"
        else -> "$days d"
    }
}
