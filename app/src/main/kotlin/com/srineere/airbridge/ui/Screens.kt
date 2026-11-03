package com.srineere.airbridge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.ui.theme.Accent
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.isAvailable

// ------------------------------------------------------------ Notifications

@Composable
fun NotificationsScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Notifications")

        SectionHeader("Rules")
        SettingsRow("Apps", "Which apps may buzz you", Icons.Outlined.Apps) { onOpen("apps") }
        SettingsRow("People", "Who gets their own buzz", Icons.Outlined.People) { onOpen("people") }
        SettingsRow("Everything else", "The fallback vibration", Icons.Outlined.Vibration) {}

        NotYet("The status card and the recent feed land with the relay rewiring.")
    }
}

// -------------------------------------------------------------------- Device

@Composable
fun DeviceScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val appearance by AppSettings.appearance.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Device")

        SectionHeader("Appearance")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                SegmentButton(
                    label = when (mode) {
                        ThemeMode.Light -> "Light"
                        ThemeMode.Dark -> "Dark"
                        ThemeMode.System -> "System"
                    },
                    selected = appearance.mode == mode,
                    modifier = Modifier.weight(1f),
                ) { AppSettings.setMode(context, mode) }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        ) {
            Text("Accent", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.entries.filter { it.isAvailable }.forEach { accent ->
                    AccentDot(accent, appearance.accent == accent) { AppSettings.setAccent(context, accent) }
                }
            }
        }

        SectionHeader("Band")
        SettingsRow("Home screen widgets", "2 ready now", Icons.Outlined.Widgets) { onOpen("widgets") }

        NotYet("The band card, battery and firmware arrive with the relay rewiring.")
    }
}

/** One accent swatch. Dynamic shows the wallpaper gradient as a single tone. */
@Composable
private fun AccentDot(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill = when (accent) {
        Accent.Dynamic -> colors.tertiary
        Accent.Cyan -> Color(0xFF6FD3E8)
        Accent.Green -> Color(0xFF5BD6A8)
        Accent.Violet -> Color(0xFFB9A8E8)
        Accent.Amber -> Color(0xFFE8C48F)
    }
    val ring by animateColorAsState(
        if (selected) fill else MaterialTheme.colorScheme.outline,
        label = "accentRing",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.88f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "accentScale",
    )
    Box(
        Modifier
            .size(32.dp)
            .scale(scale)
            .border(2.dp, ring, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) scheme.primaryContainer else Color.Transparent,
        label = "segmentFill",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                1.dp,
                if (selected) MaterialTheme.extras.accentOutline else scheme.outline,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

// --------------------------------------------------------- pushed screens

@Composable
fun AppsScreen(onBack: () -> Unit) = Placeholder("Apps", onBack)

@Composable
fun PeopleScreen(onBack: () -> Unit) = Placeholder("People", onBack)

@Composable
fun WidgetsScreen(onBack: () -> Unit) = Placeholder("Widgets", onBack)

@Composable
private fun Placeholder(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(title, onBack = onBack)
        NotYet("Not built yet.")
    }
}

/** Says plainly that a screen is unfinished rather than showing invented data. */
@Composable
private fun NotYet(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
)
