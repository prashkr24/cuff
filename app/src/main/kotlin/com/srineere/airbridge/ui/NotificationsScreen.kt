package com.srineere.airbridge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayPattern
import com.srineere.airbridge.RelayPreset
import com.srineere.airbridge.ui.theme.extras
import java.util.concurrent.TimeUnit

@Composable
internal fun NotificationsScreen(onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val config = state.config

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Notifications")

        RelayStatusCard(state)

        SectionHeader("Rules")

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

@Composable
private fun RelayStatusCard(state: RelayState) {
    val paused = state.config.silence.reason(System.currentTimeMillis()) != null
    val dot by animateColorAsState(
        when {
            !state.running -> MaterialTheme.colorScheme.onSurfaceVariant
            paused -> MaterialTheme.extras.disabled
            else -> MaterialTheme.extras.positive
        },
        label = "relayDot",
    )

    Box(Modifier.padding(top = 10.dp)) {
        AccentCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            !state.running -> "Not running"
                            paused -> "Paused"
                            else -> "Relaying"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (state.running) appsSummary(state) else (state.outcome ?: "Start to relay to your Air"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        when {
                            !state.running -> state.start()
                            paused -> state.pause(false)
                            else -> state.pause(true)
                        }
                    },
                ) {
                    Text(
                        when {
                            !state.running -> "Start"
                            paused -> "Resume"
                            else -> "Pause"
                        }
                    )
                }
            }
        }
    }
}

private fun appsSummary(state: RelayState): String {
    val on = state.config.selectedPackages
    if (on.isEmpty()) return "No apps chosen yet"
    val names = on.take(3).map { state.label(it) }
    val rest = on.size - names.size
    return names.joinToString(", ") + if (rest > 0) " and $rest more" else ""
}

private fun peopleSummary(state: RelayState): String {
    val callers = state.config.callers
    if (callers.isEmpty()) return "Everyone buzzes the same"
    val silent = callers.values.count { it.preset?.silent == true }
    val own = callers.size - silent
    return buildList {
        if (own > 0) add("$own with their own buzz")
        if (silent > 0) add("$silent silenced")
    }.joinToString(" · ")
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
