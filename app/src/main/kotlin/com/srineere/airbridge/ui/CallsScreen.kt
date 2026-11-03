package com.srineere.airbridge.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.SavedCaller

/**
 * A call is a kind of alert, not an app. The relay always knew that -- it
 * sorts every notification into a call or a message -- but the UI used to make
 * you find the dialer in a list of apps and tick it, which is the relay's own
 * plumbing showing through. This screen owns the phone's calls, the ring every
 * call inherits, and the people who differ from it.
 */
@Composable
internal fun CallsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val callsOn = state.callsOn

    // Only apps that actually name a caller can carry a per-person rule, so
    // the filter offers exactly the ones we have rules for.
    val sources = state.config.callers.values.map { it.packageName }.distinct().sorted()
    var source by remember(sources) { mutableStateOf<String?>(null) }

    val shown = state.callers(source)
    val (silenced, own) = shown.partition { it.second.preset?.silent == true }
    val usingDefault = own.filter { it.second.preset == null }
    val custom = own.filter { it.second.preset != null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Calls", onBack = onBack)

        Box(Modifier.padding(top = 6.dp)) {
            AccentCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Text(
                        "Ring my wrist for calls",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = callsOn, onCheckedChange = { state.setCallsOn(it) })
                }
            }
        }

        // Everything below only means something once calls are coming through.
        AnimatedVisibility(visible = callsOn) {
            Column {
                SettingsRow(
                    title = "How calls ring",
                    subtitle = describe(state.config.defaultCallPreset),
                    icon = Icons.Outlined.Call,
                    onClick = { onOpen("vibration/" + VibrationTarget.DefaultCall.encode()) },
                )

                SettingsRow(
                    title = "Only these people ring",
                    subtitle = "Everyone else stays silent",
                    trailing = {
                        Switch(
                            checked = state.config.onlySavedCallers,
                            onCheckedChange = { state.setOnlySavedCallers(it) },
                        )
                    },
                )

                if (sources.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        FilterChip(
                            selected = source == null,
                            onClick = { source = null },
                            label = { Text("Everyone") },
                        )
                        sources.forEach { name ->
                            FilterChip(
                                selected = source == name,
                                onClick = { source = name },
                                label = { Text(state.label(name)) },
                            )
                        }
                    }
                }

                if (state.config.callers.isEmpty() && state.recent.isEmpty()) {
                    Text(
                        "People appear here after they call, and you can give them their own " +
                            "buzz — or silence them. Numbers are never stored on their own, " +
                            "only as the rule you set.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                    )
                }

                if (custom.isNotEmpty()) {
                    SectionHeader("Own vibration")
                    custom.forEach { (id, caller) -> CallerRow(id, caller, state, onOpen) }
                }
                if (silenced.isNotEmpty()) {
                    SectionHeader("Silenced")
                    silenced.forEach { (id, caller) -> CallerRow(id, caller, state, onOpen) }
                }
                // Saved-but-unchanged and never-saved both mean "uses the
                // default", so they read as one list.
                val recent = state.recent.filter { source == null || it.second.packageName == source }
                if (usingDefault.isNotEmpty() || recent.isNotEmpty()) {
                    SectionHeader("Recent callers")
                    usingDefault.forEach { (id, caller) -> CallerRow(id, caller, state, onOpen) }
                    recent.forEach { (id, caller) -> CallerRow(id, caller, state, onOpen, saved = false) }
                }
            }
        }

        AnimatedVisibility(visible = !callsOn) {
            Text(
                "With this off, a call rings your phone and nothing else. Apps that call, like " +
                    "WhatsApp, are set under Apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun CallerRow(
    id: String,
    caller: SavedCaller,
    state: RelayState,
    onOpen: (String) -> Unit,
    saved: Boolean = true,
) {
    val preset = caller.preset
    SettingsRow(
        title = caller.label,
        subtitle = listOfNotNull(
            state.label(caller.packageName),
            preset?.let { describe(it) } ?: "Uses the default",
        ).joinToString(" · "),
        leading = { Initial(caller.label, silent = preset?.silent == true) },
        onClick = { onOpen("vibration/" + VibrationTarget.Caller(id).encode()) },
        // Nothing to forget about someone we have only heard ring.
        trailing = if (!saved) null else ({
            TextButton(onClick = { state.forgetCaller(id) }) { Text("Forget") }
        }),
    )
}

@Composable
private fun Initial(label: String, silent: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (silent) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.primaryContainer
            ),
    ) {
        Text(
            label.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = if (silent) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
