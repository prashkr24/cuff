package com.srineere.airbridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.SavedCaller

/** How many of a group is shown before handing over to its own list. */
private const val PREVIEW = 3

/**
 * The people with a rule of their own.
 *
 * This used to live inside Calls, which was never right: a person's vibration
 * outranks the app they ring from, so one rule covers the phone and WhatsApp
 * alike, and a setting that spans both does not belong under one of them.
 */
@Composable
internal fun PeopleScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("People", onBack = onBack)

        Text(
            "A person's own vibration beats the app they ring from, so these rules hold " +
                "whether they call the phone or an app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        // Above the groups, because this is how a rule gets made while they are
        // only what has been made already.
        SettingsRow(
            title = "All contacts",
            subtitle = "Give someone a buzz before they ring",
            icon = Icons.Outlined.People,
            onClick = { onOpen("contacts") },
        )

        CallerGroup.entries.forEach { group -> Group(group, onOpen) }

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
    val people = byPerson(state.group(group))
    if (people.isEmpty()) return

    SectionHeader(
        text = group.title,
        action = if (people.size > PREVIEW) "View all ${people.size}" else null,
        onAction = { onOpen("callers/${group.route}") },
    )
    people.take(PREVIEW).forEach { rules ->
        PersonRow(rules, state, onOpen, forgettable = group != CallerGroup.Recent)
    }
}

/**
 * One entry per person, however many apps they ring from.
 *
 * The identities underneath are genuinely separate -- a phone call is matched
 * by number and a WhatsApp call by name -- but that is the relay's bookkeeping,
 * not something to show someone as two copies of their friend.
 */
internal fun byPerson(
    entries: List<Pair<String, SavedCaller>>,
): List<List<Pair<String, SavedCaller>>> =
    entries.groupBy { it.second.label }.values.toList()

/**
 * A person, as one row. With a single rule it is that rule; with more than
 * one it names the apps and opens the choice, because tapping would otherwise
 * have to pick one of their rules on their behalf.
 */
@Composable
internal fun PersonRow(
    rules: List<Pair<String, SavedCaller>>,
    state: RelayState,
    onOpen: (String) -> Unit,
    forgettable: Boolean = true,
    showSource: Boolean = true,
) {
    val only = rules.singleOrNull()
    if (only != null) {
        CallerRow(only.first, only.second, state, onOpen, forgettable, showSource)
        return
    }
    val caller = rules.first().second
    SettingsRow(
        title = caller.label,
        subtitle = rules.joinToString(" · ") { state.label(it.second.packageName) },
        leading = { Initial(caller.label, silent = rules.all { it.second.preset?.silent == true }) },
        onClick = { onOpen("person/" + rules.first().first) },
    )
}

/** Every rule held for one person, once their row has more than one. */
@Composable
internal fun PersonScreen(id: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val label = state.caller(id)?.label
    val rules = state.callers(null).filter { it.second.label == label }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(label ?: "Caller", onBack = onBack)

        Text(
            "The app a call comes through decides which of these applies, because each " +
                "names this person differently.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        rules.forEach { (ruleId, caller) ->
            CallerRow(ruleId, caller, state, onOpen, showNumber = true)
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * What the People row says without being opened: who is different, and who
 * never gets through. Recent callers are left out because having rung is not
 * a decision anyone made.
 */
internal fun peopleSummary(state: RelayState): String {
    val own = byPerson(state.group(CallerGroup.Own)).size
    val silenced = byPerson(state.group(CallerGroup.Silenced)).size
    return when {
        own == 0 && silenced == 0 -> "Nobody has their own rule yet"
        own == 0 -> "$silenced silenced"
        silenced == 0 -> "$own with their own rule"
        else -> "$own with their own rule · $silenced silenced"
    }
}
