package com.srineere.airbridge.ui

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

@Composable
internal fun PeopleScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current

    // Only apps that actually name a caller can carry a per-person rule, so
    // the filter offers exactly the ones we have rules for.
    val sources = state.config.callers.values.map { it.packageName }.distinct().sorted()
    var source by remember(sources) { mutableStateOf<String?>(null) }

    val shown = state.callers(source)
    val (silenced, own) = shown.partition { it.second.preset?.silent == true }
    val usingDefault = own.filter { it.second.preset == null }
    val custom = own.filter { it.second.preset != null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("People", onBack = onBack)

        if (sources.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                FilterChip(
                    selected = source == null,
                    onClick = { source = null },
                    label = { Text("All") },
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

        if (state.config.callers.isEmpty()) {
            Text(
                "Nobody saved yet. After someone calls, they appear here and you can " +
                    "give them their own buzz — or silence them.",
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
        if (usingDefault.isNotEmpty()) {
            SectionHeader("Recent callers")
            usingDefault.forEach { (id, caller) -> CallerRow(id, caller, state, onOpen) }
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
) {
    val preset = caller.preset
    SettingsRow(
        title = caller.label,
        subtitle = listOfNotNull(
            caller.detail,
            preset?.let { describe(it) } ?: "Uses the default",
        ).joinToString(" · "),
        leading = { Initial(caller.label, silent = preset?.silent == true) },
        onClick = { onOpen("vibration/" + VibrationTarget.Caller(id).encode()) },
        trailing = {
            TextButton(onClick = { state.forgetCaller(id) }) { Text("Forget") }
        },
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
