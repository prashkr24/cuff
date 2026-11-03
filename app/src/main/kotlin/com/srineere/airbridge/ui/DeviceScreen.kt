package com.srineere.airbridge.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.Band
import com.srineere.airbridge.ui.theme.Accent
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.ui.theme.isAvailable
import dev.airpulse.fitbit_air.airResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

@Composable
internal fun DeviceScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val appearance by AppSettings.appearance.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Device")

        BandCard()

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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Accent.entries.filter { it.isAvailable }.forEach { accent ->
                AccentDot(accent, appearance.accent == accent) { AppSettings.setAccent(context, accent) }
            }
            Spacer(Modifier.weight(1f))
        }

        SectionHeader("Band")
        SettingsRow(
            title = "Permissions",
            subtitle = permissionsSummary(),
            icon = Icons.Outlined.Lock,
            onClick = { onOpen("permissions") },
        )
        SettingsRow(
            title = "Home screen widgets",
            subtitle = "Band battery and last buzz",
            icon = Icons.Outlined.Widgets,
            onClick = { onOpen("widgets") },
        )

        SettingsRow(
            title = "About",
            subtitle = "Your band, this app, and how they talk",
            icon = Icons.Outlined.Info,
            onClick = { onOpen("about") },
        )

        Box(Modifier.padding(bottom = 28.dp))
    }
}

/** Where Buzz the band is in its one short round trip. */
private enum class Buzz { Idle, Sending, Sent }

/**
 * The band's own state, which does not wait on the relay: [Band] borrows the
 * relay's link when there is one and holds its own when there is not. Nothing
 * here is live either way -- every reading costs a session with the band -- so
 * each one carries the moment it was taken.
 */
@Composable
private fun BandCard() {
    val context = LocalContext.current
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var batteryAt by remember { mutableLongStateOf(0L) }
    var rssi by remember { mutableStateOf<Int?>(null) }
    var rssiAt by remember { mutableLongStateOf(0L) }
    var syncing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var buzz by remember { mutableStateOf(Buzz.Idle) }

    fun sync() {
        if (syncing) return
        syncing = true
        message = null
        Band.isConnected(context, airResult(
            onSuccess = { connected = it as? Boolean },
            onError = { _, _ -> connected = null },
        ))
        // One at a time: the band holds a single session, so the signal read
        // waits for the battery read rather than racing it.
        Band.readBattery(context, airResult(
            onSuccess = { value ->
                // The band answered, so it is reachable whatever the adapter
                // said, and whatever an earlier attempt complained about.
                connected = true
                message = null
                battery = (value as? Map<*, *>)?.get("percent") as? Int
                batteryAt = System.currentTimeMillis()
                Band.readSignal(context, airResult(
                    onSuccess = { reading ->
                        syncing = false
                        rssi = (reading as? Map<*, *>)?.get("rssi") as? Int
                        rssiAt = System.currentTimeMillis()
                    },
                    onError = { code, _ -> syncing = false; message = explain(code) },
                ))
            },
            onError = { code, _ -> syncing = false; message = explain(code) },
        ))
    }

    fun buzzBand() {
        if (buzz != Buzz.Idle) return
        message = null
        buzz = Buzz.Sending
        Band.buzz(context, airResult(
            onSuccess = { buzz = Buzz.Sent },
            onError = { code, detail ->
                buzz = Buzz.Idle
                message = if (code == "SILENT" || code == "CALLS_ONLY") detail ?: explain(code)
                else explain(code)
            },
        ))
    }

    // "Sent" is a confirmation, not a state: it says its piece and goes.
    LaunchedEffect(buzz) {
        if (buzz == Buzz.Sent) {
            delay(2_000)
            buzz = Buzz.Idle
        }
    }
    LaunchedEffect(Unit) { sync() }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(
                    Icons.Outlined.Watch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Fitbit Air", style = MaterialTheme.typography.headlineSmall)
                Row(
                    // A failure takes this line and can run to two, so the
                    // glyph sits on the first line rather than floating in
                    // the middle of the block.
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    // A failure takes this line, so the readings never have to
                    // carry an error beside a number that is no longer true.
                    Icon(
                        if (connected == true) Icons.Outlined.Bluetooth
                        else Icons.Outlined.BluetoothDisabled,
                        contentDescription = null,
                        tint = if (connected == true) MaterialTheme.extras.positive
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp).size(15.dp),
                    )
                    Text(
                        message ?: when {
                            connected == true -> "Connected"
                            connected == false -> "Disconnected"
                            syncing -> "Checking…"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { sync() }, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Sync,
                        contentDescription = "Check the band again",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(top = 22.dp),
        ) {
            Metric(
                icon = Icons.Outlined.BatteryStd,
                label = "Battery",
                value = battery?.let { "$it%" } ?: "\u2014",
                detail = if (batteryAt == 0L) "Not checked yet" else "Checked ${since(batteryAt)}",
                modifier = Modifier.weight(1f),
            )
            Metric(
                icon = Icons.Outlined.SignalCellularAlt,
                label = "Signal",
                value = rssi?.let { strength(it) } ?: "\u2014",
                detail = rssi?.let { "$it dBm \u00b7 ${since(rssiAt)}" } ?: "Not checked yet",
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedButton(
            onClick = { buzzBand() },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        ) {
            when (buzz) {
                Buzz.Sending -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Buzz.Sent -> Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Buzz.Idle -> Icon(Icons.Outlined.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                when (buzz) {
                    Buzz.Sending -> "Sending\u2026"
                    Buzz.Sent -> "Sent"
                    Buzz.Idle -> "Buzz the band"
                }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
private fun Metric(
    icon: ImageVector,
    label: String,
    value: String?,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A missing reading stays blank rather than showing a dash: at this
        // size a dash draws a rule across the card, and the line underneath
        // already says it has not been read. The height is held either way so
        // the two tiles keep one baseline.
        Box(Modifier.padding(top = 5.dp).height(34.dp), contentAlignment = Alignment.CenterStart) {
            if (value != null) Text(value, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** A proximity hint, not a measurement, so it is worded as one. */
private fun strength(rssi: Int): String = when {
    rssi >= -60 -> "Strong"
    rssi >= -80 -> "Good"
    else -> "Weak"
}

private fun since(atMillis: Long): String {
    val delta = (System.currentTimeMillis() - atMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(delta)} d ago"
    }
}

private fun explain(code: String?): String = when (code) {
    "BUSY" -> "Busy delivering a notification; try again shortly"
    "BLUETOOTH_PERMISSION" -> "Nearby devices permission is needed"
    "AIR_NOT_FOUND" -> "Pair your Air in Google Health first"
    "NOT_RUNNING" -> "Start the relay to reach the band"
    else -> "Could not reach the band"
}

/** What the Permissions row says without having to be opened. */
@Composable
private fun permissionsSummary(): String {
    val context = LocalContext.current
    val blocking = remember(resumeTicks()) { blockingGrants(context) }
    return when (blocking) {
        0 -> "Everything the relay needs is allowed"
        1 -> "One thing still to allow"
        else -> "$blocking things still to allow"
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
        Accent.Rose -> Color(0xFFE8A6B4)
        Accent.Slate -> Color(0xFFA8B6C8)
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
