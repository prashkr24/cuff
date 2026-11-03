package com.srineere.airbridge.ui

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import java.util.Calendar
import java.util.Date

private const val MINUTE = 60_000L

/**
 * When the band is allowed to buzz.
 *
 * Three lengths of the same answer: the relay switch is for good, a pause is
 * for the next hour, quiet hours are for every night. Pause and the quiet
 * window were both already in the stored rules and honoured by the relay --
 * pause only as a fifteen minute action on the notification, and the window
 * with no way to see or set it at all.
 */
@Composable
internal fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = LocalRelayState.current
    val silence = state.config.silence
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    val now = System.currentTimeMillis()
    val paused = silence.pauseUntil == -1L || silence.pauseUntil > now
    var clash by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Schedule", onBack = onBack)

        Text(
            "Quiet means quiet on the wrist only. Alerts still arrive on the phone exactly " +
                "as they do now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        SectionHeader("Pause")

        if (paused) {
            SettingsRow(
                title = if (silence.pauseUntil == -1L) "Paused"
                    else "Paused until " + format.format(Date(silence.pauseUntil)),
                subtitle = "Nothing reaches the band until this ends",
                trailing = { TextButton(onClick = { state.pauseUntil(0) }) { Text("Resume") } },
            )
        } else {
            Text(
                "Stops the band buzzing without stopping the relay, so it starts again by " +
                    "itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                OutlinedButton(onClick = { state.pauseUntil(now + 15 * MINUTE) }) { Text("15 min") }
                OutlinedButton(onClick = { state.pauseUntil(now + 60 * MINUTE) }) { Text("1 hour") }
                OutlinedButton(
                    onClick = { state.pauseUntil(-1) },
                    modifier = Modifier.weight(1f),
                ) { Text("Until I resume") }
            }
        }

        SectionHeader("Quiet hours")

        SettingsRow(
            title = "Every night",
            subtitle = "The band stays still between these times",
            trailing = {
                Switch(
                    checked = silence.quietEnabled,
                    onCheckedChange = { state.setQuiet(it) },
                )
            },
        )

        SettingsRow(
            title = "From",
            onClick = {
                pickTime(context, silence.startMinute) { picked ->
                    // The window would never end, and the rules refuse to
                    // hold it, so the clash is caught before it is written.
                    clash = picked == silence.endMinute
                    if (!clash) state.setQuietWindow(picked, silence.endMinute)
                }
            },
            trailing = { Text(label(format, silence.startMinute), style = MaterialTheme.typography.bodyLarge) },
        )
        SettingsRow(
            title = "To",
            onClick = {
                pickTime(context, silence.endMinute) { picked ->
                    clash = picked == silence.startMinute
                    if (!clash) state.setQuietWindow(silence.startMinute, picked)
                }
            },
            trailing = { Text(label(format, silence.endMinute), style = MaterialTheme.typography.bodyLarge) },
        )

        if (clash) {
            Text(
                "Pick two different times. A window that ends when it starts would never end.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        Text(
            "A call from someone with their own vibration is silenced by quiet hours too. " +
                "Nothing here lets a rule through the quiet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** The phone's own picker, in the phone's own 12 or 24 hour habit. */
private fun pickTime(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        minuteOfDay / 60,
        minuteOfDay % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

/** A minute of the day, written the way this phone writes a time. */
private fun label(format: java.text.DateFormat, minuteOfDay: Int): String {
    val when_ = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return format.format(when_.time)
}

/** What the Schedule row says without being opened. */
@Composable
internal fun scheduleSummary(state: RelayState): String {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    val silence = state.config.silence
    return when {
        silence.pauseUntil == -1L -> "Paused"
        silence.pauseUntil > System.currentTimeMillis() ->
            "Paused until " + format.format(Date(silence.pauseUntil))
        silence.quietEnabled ->
            "Quiet " + label(format, silence.startMinute) + " to " + label(format, silence.endMinute)
        else -> "Always on, no quiet hours"
    }
}
