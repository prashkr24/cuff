package com.srineere.airbridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayPattern
import com.srineere.airbridge.RelayPreset
import java.util.concurrent.TimeUnit

@Composable
internal fun AlertsScreen(onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val config = state.config

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Alerts")

        RelayStatusCard(state, onOpen)

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
            title = "Everything else",
            subtitle = describe(config.defaultPreset),
            icon = Icons.Outlined.Vibration,
            onClick = { onOpen("vibration/" + VibrationTarget.Default.encode()) },
        )

        if (state.history.isNotEmpty()) {
            SectionHeader("Recent")
            state.history.forEach { entry ->
                SettingsRow(
                    title = state.label(entry.sourcePackage),
                    subtitle = describeActivity(entry.stage, entry.errorCode, entry.preset),
                    trailing = {
                        Text(
                            ago(entry.atMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
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

    val card: @Composable (@Composable () -> Unit) -> Unit =
        // The tint is how this app marks something live, so an off relay does
        // not get to wear it.
        if (running) { body -> AccentCard { body() } }
        else { body ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) { body() }
        }

    Box(Modifier.padding(top = 10.dp)) {
        card {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    when {
                        // No subtext, so anything that has to be said is said
                        // here or not at all.
                        blocking > 0 && !running ->
                            "$blocking thing${if (blocking == 1) "" else "s"} to allow"
                        running && paused -> "Paused"
                        running -> "Relaying"
                        else -> "Not relaying"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = running,
                    onCheckedChange = { wanted ->
                        when {
                            // It cannot start, so the switch takes you to the
                            // reason instead of pretending to flip.
                            wanted && blocking > 0 -> onOpen("permissions")
                            wanted -> state.start()
                            else -> state.stop()
                        }
                    },
                )
            }
        }
    }
}

/** What the Calls row says without being opened. */
private fun callsSummary(state: RelayState): String {
    if (!state.callsOn) return "Off"
    val differ = state.config.callers.count { it.value.preset != null }
    val ring = describe(state.config.defaultCallPreset)
    return if (differ == 0) ring
    else "$ring · $differ ${if (differ == 1) "person" else "people"} differ"
}

/** A count, not a list: the names were a worse copy of the Apps screen. */
private fun appsSummary(state: RelayState): String = when (val count = (state.config.selectedPackages - state.dialer).size) {
    0 -> "No apps chosen yet"
    1 -> "1 app"
    else -> "$count apps"
}

internal fun describe(preset: RelayPreset): String {
    val shape = when (preset.pattern) {
        RelayPattern.SILENT -> return "Silent"
        RelayPattern.ONE -> "One pulse"
        RelayPattern.TWO -> "Two pulses"
        RelayPattern.RING -> "Rings until answered"
    }
    val strength = if (preset.strength == RelayPreset.FULL_STRENGTH) "strong" else "light"
    return "$shape · $strength"
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
