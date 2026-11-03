package com.srineere.airbridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayConversationPolicy

@Composable
internal fun AppsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    var query by remember { mutableStateOf("") }

    val matching = state.apps.filter { it.label.contains(query, ignoreCase = true) }
    val (on, off) = matching.partition { it.packageName in state.config.selectedPackages }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Apps", onBack = onBack)

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search apps") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )

        LazyColumn(Modifier.fillMaxSize()) {
            if (on.isNotEmpty()) {
                item("on") { SectionHeader("On") }
                items(on, key = { "on:" + it.packageName }) { app ->
                    AppRow(app, enabled = true, state = state, onOpen = onOpen)
                }
            }
            if (off.isNotEmpty()) {
                item("off") { SectionHeader("Off") }
                items(off, key = { "off:" + it.packageName }) { app ->
                    AppRow(app, enabled = false, state = state, onOpen = onOpen)
                }
            }
            item("tail") { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, enabled: Boolean, state: RelayState, onOpen: (String) -> Unit) {
    val rule = state.config.apps[app.packageName]
    SettingsRow(
        title = app.label,
        subtitle = if (!enabled) null else buildString {
            append(describe(rule?.preset ?: state.config.defaultPreset))
            if (app.canCall) {
                append(" · calls ")
                append(describe(rule?.callPreset ?: state.config.defaultCallPreset).lowercase())
            }
        },
        onClick = if (enabled) ({ onOpen("app/" + app.packageName) }) else null,
        leading = { AppIcon(app) },
        trailing = {
            Switch(
                checked = enabled,
                onCheckedChange = { state.setAppEnabled(app.packageName, it) },
            )
        },
    )
}

@Composable
internal fun AppIcon(app: AppEntry, size: Int = 38) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3.4).dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size((size - 8).dp))
        } else {
            Text(
                app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ------------------------------------------------------------- one app

@Composable
internal fun AppDetailScreen(packageName: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val app = state.apps.firstOrNull { it.packageName == packageName }
    val rule = state.config.apps[packageName]
    val enabled = packageName in state.config.selectedPackages

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(app?.label ?: packageName, onBack = onBack)

        SettingsRow(
            title = "Let this app buzz me",
            trailing = {
                Switch(checked = enabled, onCheckedChange = { state.setAppEnabled(packageName, it) })
            },
        )

        if (enabled) {
            SectionHeader("What it feels like")
            SettingsRow(
                title = "Messages",
                subtitle = describe(rule?.preset ?: state.config.defaultPreset),
                icon = Icons.Outlined.ChatBubbleOutline,
                onClick = {
                    onOpen("vibration/" + VibrationTarget.App(packageName, calls = false).encode())
                },
            )
            if (app?.canCall == true) {
                SettingsRow(
                    title = "Calls",
                    subtitle = describe(rule?.callPreset ?: state.config.defaultCallPreset),
                    icon = Icons.Outlined.Call,
                    onClick = {
                        onOpen("vibration/" + VibrationTarget.App(packageName, calls = true).encode())
                    },
                )
            }

            // Only apps that actually name a caller get a People entry: the
            // dialer, and WhatsApp, which sends a display name. Instagram and
            // Slack ring without saying who, so a per-person rule there would
            // be a promise we cannot keep.
            if (app?.namesCallers == true || packageName in RelayConversationPolicy.packages) {
                SectionHeader("Who")
                val saved = state.callers(packageName)
                SettingsRow(
                    title = "People who call here",
                    subtitle = if (saved.isEmpty()) "Nobody saved yet"
                        else "${saved.size} with their own rule",
                    icon = Icons.Outlined.People,
                    onClick = { onOpen("people") },
                )
                if (packageName in RelayConversationPolicy.packages) {
                    Text(
                        "WhatsApp tells us a caller's name but not their number, so these " +
                            "rules match on the name. Renaming a contact breaks their rule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
