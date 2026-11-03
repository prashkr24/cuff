package com.srineere.airbridge.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayContacts
import com.srineere.airbridge.ui.theme.extras

/**
 * The address book, so a rule can be made before someone's first call --
 * which is the only moment a rule is any use.
 *
 * Asked per app, because identity differs by who is calling: the dialer names
 * a caller by number, WhatsApp only by display name. The same contact is a
 * different person to each, so the list is too.
 */
@Composable
internal fun ContactsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val state = LocalRelayState.current

    var granted by remember { mutableStateOf(RelayContacts.granted(context)) }
    var asked by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = RelayContacts.granted(context)
        asked++
    }

    val sources = remember(state.dialer) {
        listOf(state.dialer to "Phone") + RelayContacts.NAMED_CALLERS
            .filter { state.apps.any { app -> app.packageName == it } }
            .map { it to state.label(it) }
    }
    var source by remember(sources) { mutableStateOf(sources.first().first) }
    var query by remember { mutableStateOf("") }

    // Reading the address book is the slow part, so it happens once per source
    // rather than on every keystroke.
    val all = remember(source, granted, asked, state.config) {
        if (granted) state.contacts(source) else emptyList()
    }
    val shown = all.filter { it.second.label.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Contacts", onBack = onBack)

        if (!granted) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Show my contacts",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Names are read on this phone, so you can give someone a buzz before they " +
                        "ring. Nothing is uploaded, and a number is only ever written down as " +
                        "part of a rule you set for it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { ask.launch(Manifest.permission.READ_CONTACTS) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("Allow") }
            }
            return@Column
        }

        if (sources.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                sources.forEach { (packageName, label) ->
                    FilterChip(
                        selected = source == packageName,
                        onClick = { source = packageName },
                        label = { Text(label) },
                    )
                }
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search contacts") },
            trailingIcon = {
                // Only once there is something to clear: a cross in an empty
                // field invites a tap that does nothing.
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        )

        // An address book runs to hundreds, so this is the one list here that
        // has to be lazy.
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.first }) { (id, caller) ->
                CallerRow(
                    id, caller, state, onOpen,
                    forgettable = false, showSource = false, showNumber = true,
                )
            }
            item {
                Text(
                    if (source == state.dialer)
                        "A phone call is matched by number, so a rule here keeps working if " +
                            "they change their name."
                    else "${state.label(source)} sends only a name, so a rule here follows the " +
                        "name — rename the contact and it stops matching.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extras.disabled,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            item { Box(Modifier.padding(bottom = 16.dp)) }
        }
    }
}
