package com.srineere.airbridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** How many of a group the Calls screen shows before handing over to its list. */
private const val PREVIEW = 3

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Calls", onBack = onBack)

        StatusToggle(
            title = "Ring my wrist for calls",
            live = callsOn,
            onToggle = { state.setCallsOn(it) },
        )

        // The card is its own thing, not a heading for the rows under it.
        // On Alerts a section header holds them apart; here there is none, so
        // the distance has to be said outright.
        Spacer(Modifier.height(12.dp))

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

                // Above the groups, because this is how a rule gets made while
                // they are only what has been made already.
                SettingsRow(
                    title = "All contacts",
                    subtitle = "Give someone a buzz before they ring",
                    icon = Icons.Outlined.People,
                    onClick = { onOpen("contacts") },
                )

                CallerGroup.entries.forEach { group ->
                    Group(group, onOpen)
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

/**
 * The first few of a group, with a way to the rest. The point of this screen
 * is to see at a glance who is different, not to scroll a directory.
 */
@Composable
private fun Group(group: CallerGroup, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val people = state.group(group)
    if (people.isEmpty()) return

    SectionHeader(
        text = group.title,
        action = if (people.size > PREVIEW) "View all ${people.size}" else null,
        onAction = { onOpen("callers/${group.route}") },
    )
    people.take(PREVIEW).forEach { (id, caller) ->
        CallerRow(id, caller, state, onOpen, forgettable = group != CallerGroup.Recent)
    }
}
