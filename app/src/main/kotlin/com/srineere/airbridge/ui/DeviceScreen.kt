package com.srineere.airbridge.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.ui.theme.Accent
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.ui.theme.isAvailable
import dev.airpulse.fitbit_air.airResult

@Composable
internal fun DeviceScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val state = LocalRelayState.current
    val appearance by AppSettings.appearance.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Device")

        BandCard(state)

        SectionHeader("Appearance")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                Segment(
                    label = mode.name,
                    selected = appearance.mode == mode,
                    modifier = Modifier.weight(1f),
                ) { AppSettings.setMode(context, mode) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        ) {
            Text("Accent", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.entries.filter { it.isAvailable }.forEach { accent ->
                    AccentDot(accent, appearance.accent == accent) { AppSettings.setAccent(context, accent) }
                }
            }
        }

        SectionHeader("Band")
        SettingsRow(
            title = "Home screen widgets",
            subtitle = "Band battery and last buzz",
            icon = Icons.Outlined.Widgets,
            onClick = { onOpen("widgets") },
        )

        SectionHeader("About")
        Text(
            "Air Bridge 0.3.0\n\nBuilt on the fitbit_air protocol work by Thore Koritzius. " +
                "Nothing you see here leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Box(Modifier.padding(bottom = 28.dp))
    }
}

/**
 * The band's own state. Every read goes through the running relay, because the
 * relay owns the Bluetooth transport: a second link from the UI would fight it
 * for the same single session.
 */
@Composable
private fun BandCard(state: RelayState) {
    var battery by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf(false) }

    fun refresh() {
        val relay = CalendarRelayService.active
        if (relay == null) {
            message = "Start the relay to reach the band"
            return
        }
        reading = true
        relay.readBattery(airResult(
            onSuccess = { value ->
                reading = false
                battery = (value as? Map<*, *>)?.get("percent") as? Int
                message = if (battery == null) "The band did not report a level" else null
            },
            onError = { code, _ ->
                reading = false
                message = when (code) {
                    "BUSY" -> "Busy delivering a notification; try again shortly"
                    "BLUETOOTH_PERMISSION" -> "Nearby devices permission is needed"
                    "AIR_NOT_FOUND" -> "Pair your Air in Google Health first"
                    else -> "Could not reach the band"
                }
            },
        ))
    }

    LaunchedEffect(state.running) { if (state.running) refresh() }

    Box(Modifier.padding(top = 10.dp)) {
        AccentCard {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Fitbit Air", style = MaterialTheme.typography.titleMedium)
                        Text(
                            message ?: when {
                                reading -> "Reading…"
                                battery != null -> "Connected"
                                else -> "Not read yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    battery?.let { percent ->
                        Text("$percent%", style = MaterialTheme.typography.titleLarge)
                    }
                }

                battery?.let { percent ->
                    Box(
                        Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.extras.positive)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching { CalendarRelayService.active?.testPulse() }
                                .onFailure { message = it.message }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Vibration, contentDescription = null)
                        Text("  Buzz the band")
                    }
                    OutlinedButton(onClick = { refresh() }) { Text("Refresh") }
                }
            }
        }
    }
}

/** One accent swatch. Dynamic shows the wallpaper's own tone. */
@Composable
private fun AccentDot(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    // The wallpaper swatch has to show the wallpaper's own colour, not the
    // active scheme's: unless Dynamic is already picked, that scheme is one of
    // ours and its tertiary means nothing here.
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val fill = when (accent) {
        Accent.Dynamic ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(context).primary
                else dynamicLightColorScheme(context).primary
            } else MaterialTheme.colorScheme.primary
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
