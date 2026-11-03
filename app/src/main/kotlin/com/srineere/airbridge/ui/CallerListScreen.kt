package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.SavedCaller
import com.srineere.airbridge.ui.theme.extras

/** Which of the three groups of people a list is showing. */
internal enum class CallerGroup(val route: String, val title: String) {
    Own("own", "Own vibration"),
    Silenced("silenced", "Silenced"),
    Recent("recent", "Recent callers");

    companion object {
        fun of(raw: String?): CallerGroup = entries.firstOrNull { it.route == raw } ?: Own
    }
}

/**
 * One screen for all three groups. They differ only in which people they
 * hold, so giving each its own screen would be three copies of one list.
 */
@Composable
internal fun CallerListScreen(group: CallerGroup, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val people = state.group(group)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(group.title, onBack = onBack)

        Text(
            when (group) {
                CallerGroup.Own -> "These people ring differently from everyone else. " +
                    "Tap one to change it, or put them back on the default."
                CallerGroup.Silenced -> "Calls from these people never reach the band."
                CallerGroup.Recent -> "People who have rung since the relay started. " +
                    "Give one their own buzz and they move up to Own vibration."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        if (people.isEmpty()) {
            Text(
                "Nobody here yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        people.forEach { (id, caller) ->
            CallerRow(
                id = id,
                caller = caller,
                state = state,
                onOpen = onOpen,
                forgettable = group != CallerGroup.Recent,
            )
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** One person, wherever they are listed. */
@Composable
internal fun CallerRow(
    id: String,
    caller: SavedCaller,
    state: RelayState,
    onOpen: (String) -> Unit,
    forgettable: Boolean = true,
    showSource: Boolean = true,
    showNumber: Boolean = false,
) {
    val preset = caller.preset
    SettingsRow(
        title = caller.label,
        subtitle = listOfNotNull(
            // Only worth saying where a list mixes sources; a screen already
            // filtered to one would just repeat its own chip on every row.
            state.label(caller.packageName).takeIf { showSource },
            // A contact with two numbers is two entries with one name, and
            // only one of them will ever ring. Kept to the address book and
            // to a person's own screen rather than scattered over the tab.
            caller.detail?.takeIf { showNumber && it.isNotBlank() },
            preset?.let { describe(it) } ?: "Uses the default",
        ).joinToString(" · "),
        leading = { Initial(caller.label, silent = preset?.silent == true) },
        onClick = { onOpen("vibration/" + VibrationTarget.Caller(id).encode()) },
        // Nothing to forget about someone we have only heard ring.
        trailing = if (!forgettable || preset == null) null else ({
            TextButton(onClick = { state.forgetCaller(id) }) { Text("Forget") }
        }),
    )
}

@Composable
internal fun Initial(label: String, silent: Boolean) {
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
