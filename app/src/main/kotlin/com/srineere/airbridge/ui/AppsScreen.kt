package com.srineere.airbridge.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayAppRule
import com.srineere.airbridge.RelayConversationPolicy
import com.srineere.airbridge.ui.theme.extras

@Composable
internal fun AppsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    var query by remember { mutableStateOf("") }
    var openOn by remember { mutableStateOf(true) }
    var openOff by remember { mutableStateOf(false) }

    val apps = state.apps
    val config = state.config
    val dialer = state.dialer
    // Read once. This is a getter that builds a map and a set every time, and
    // it used to be read inside the partition below -- so once per app, on
    // every frame.
    val selected = config.selectedPackages

    // The dialer is not one of these. Calls own it, and a second switch for
    // the same setting is a pair of controls that will disagree.
    val matching = remember(apps, query, dialer) {
        apps.filter { it.packageName != dialer && it.label.contains(query, ignoreCase = true) }
    }
    val (on, off) = remember(matching, selected) {
        matching.partition { it.packageName in selected }
    }
    // One pass over the saved callers, rather than a filter and a sort of all
    // of them inside every row that is open.
    val callers = remember(config.callers) {
        config.callers.values.groupingBy { it.packageName }.eachCount()
    }

    // A search whose matches sit inside a shut section looks broken, so
    // searching opens both. Off opens by itself when nothing is on yet, or
    // the screen would greet a new phone with nothing at all.
    val searching = query.isNotEmpty()
    val showOn = openOn || searching
    val showOff = openOff || searching || on.isEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Apps", onBack = onBack)

        Text(
            "Your phone's own calls are set under Calls. These are the apps whose " +
                "notifications buzz you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        // What an app does before anyone says otherwise. These are the answer
        // to every "Default" below, so they belong on the same screen as them,
        // and there are two because an app's call is not an app's message.
        SettingsRow(
            title = "How apps buzz",
            subtitle = describe(state.config.defaultPreset),
            icon = Icons.Outlined.Vibration,
            onClick = { onOpen("vibration/" + VibrationTarget.Default.encode()) },
        )
        SettingsRow(
            title = "How app calls ring",
            subtitle = describe(state.config.defaultAppCallPreset),
            icon = Icons.Outlined.Call,
            onClick = { onOpen("vibration/" + VibrationTarget.DefaultAppCall.encode()) },
        )

        SearchField(query, { query = it }, "Search apps")

        LazyColumn(Modifier.fillMaxSize()) {
            if (on.isNotEmpty()) {
                item("on") { SectionToggle("On", on.size, showOn) { openOn = !openOn } }
                if (showOn) items(on, key = { "on:" + it.packageName }) { app ->
                    AppRow(
                        app = app,
                        enabled = true,
                        rule = config.apps[app.packageName],
                        callers = callers[app.packageName] ?: 0,
                        onToggle = { state.setAppEnabled(app.packageName, it) },
                        onOpen = onOpen,
                    )
                }
            }
            if (off.isNotEmpty()) {
                item("off") { SectionToggle("Off", off.size, showOff) { openOff = !openOff } }
                if (showOff) items(off, key = { "off:" + it.packageName }) { app ->
                    AppRow(
                        app = app,
                        enabled = false,
                        rule = config.apps[app.packageName],
                        callers = callers[app.packageName] ?: 0,
                        onToggle = { state.setAppEnabled(app.packageName, it) },
                        onOpen = onOpen,
                    )
                }
            }
            // Only once there is a list to have matched against: before it
            // lands, nothing matching is not the same as nothing found.
            if (matching.isEmpty() && state.appsLoaded) {
                item("none") {
                    Text(
                        "No app by that name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extras.disabled,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            item("tail") { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

/**
 * A section heading that opens and shuts, carrying its count so a shut one
 * still says how much is inside.
 *
 * Open or shut is a view, not a setting: it lasts while the screen is open and
 * is forgotten when it closes, because a section that stays shut across
 * launches is a section whose contents quietly stop existing.
 */
@Composable
private fun SectionToggle(text: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 90f else 0f, Motion.spatial(), label = "caret")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 14.dp, top = 18.dp, bottom = 2.dp),
    ) {
        Text(
            "${text.uppercase()} \u00b7 $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = if (open) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(turn),
        )
    }
}

/**
 * The switch decides whether this app is in play at all; what each kind of
 * alert feels like lives in its own row under it, so neither is hidden behind
 * tapping the other.
 */
@Composable
private fun AppRow(
    app: AppEntry,
    enabled: Boolean,
    rule: RelayAppRule?,
    callers: Int,
    onToggle: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) }
            // The switch sets the height on its own, so the row adds almost
            // nothing: a long list of apps is mostly scrolling.
            .padding(horizontal = 20.dp, vertical = 1.dp),
    ) {
        AppIcon(app, size = 34)
        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RowSwitch(checked = enabled, onCheckedChange = onToggle)
    }
    if (enabled) {
        AlertKinds(app, rule, callers, onOpen)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The kinds of alert this app can produce, and what each one feels like.
 *
 * Takes the app's rule and its number of saved callers rather than the whole
 * [RelayState]: that is a plain class, so a row holding one can never be
 * skipped, and a screen of them all recomposed whenever anything was written.
 */
@Composable
private fun AlertKinds(
    app: AppEntry,
    rule: RelayAppRule?,
    callers: Int,
    onOpen: (String) -> Unit,
) {
    // Only apps that name a caller get a list of people; elsewhere a
    // per-person rule would be a promise we could not keep.
    val names = app.namesCallers || app.packageName in RelayConversationPolicy.packages
    // A call rule already set stays reachable even on an app we do not think
    // can ring, so that it can be undone.
    val calls = app.canCall || rule?.callPreset != null

    Box(Modifier.fillMaxWidth()) {
        // A hairline tying these rows to the app they belong to.
        Box(Modifier.matchParentSize().padding(start = 37.dp, bottom = 2.dp)) {
            Box(
                Modifier.width(1.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Column {
            AlertRow(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = "Notifications",
                // Silent here is what makes "calls only" possible: the app
                // stays on, so its calls still ring.
                //
                // Inheriting says so and stops: repeating the default's shape
                // on every app made a screen of apps look like a screen of
                // decisions, when most of them are the same one.
                value = rule?.preset?.let { describe(it) } ?: "Default",
                onClick = {
                    onOpen("vibration/" + VibrationTarget.App(app.packageName, calls = false).encode())
                },
            )
            if (names) {
                AlertRow(
                    icon = Icons.Outlined.People,
                    title = "People",
                    value = if (callers == 0) "Nobody yet"
                        else "$callers with their own rule",
                    onClick = { onOpen("appcallers/" + app.packageName) },
                )
            }
            if (calls) {
                AlertRow(
                    icon = Icons.Outlined.Call,
                    title = "Calls",
                    value = rule?.callPreset?.let { describe(it) } ?: "Default",
                    onClick = {
                        onOpen("vibration/" + VibrationTarget.App(app.packageName, calls = true).encode())
                    },
                )
            }
        }
    }
}

/** One kind of alert, indented under its app. */
@Composable
private fun AlertRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .heightIn(min = 40.dp)
        .padding(start = 68.dp, end = 20.dp),
) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(17.dp),
    )
    Text(title, style = MaterialTheme.typography.bodyMedium)
    // The value takes whatever the title leaves rather than a fixed column,
    // which is what wrapped "Rings until answered" onto a second line.
    Text(
        value,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
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

// ------------------------------------------------------ the people in one app

/**
 * Who rings through one app. Only the people with a rule of their own are
 * listed: everyone else follows the app's call vibration, and the address book
 * is where a new rule is made.
 */
@Composable
internal fun AppCallersScreen(packageName: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state = LocalRelayState.current
    val label = state.apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
    val saved = state.callers(packageName)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("$label callers", onBack = onBack)

        SettingsRow(
            title = "All contacts",
            subtitle = "Give someone a buzz before they ring",
            icon = Icons.Outlined.People,
            onClick = { onOpen("contacts/$packageName") },
        )

        if (saved.isEmpty()) {
            Text(
                "Nobody here has their own rule yet, so every $label call feels the same.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        } else {
            SectionHeader("Their own rule")
            saved.forEach { (id, caller) ->
                CallerRow(id, caller, state, onOpen, showSource = false, showNumber = true)
            }
        }

        if (packageName in RelayConversationPolicy.packages) {
            Text(
                "$label tells us a caller's name but not their number, so these rules match " +
                    "on the name. Renaming a contact breaks their rule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}
