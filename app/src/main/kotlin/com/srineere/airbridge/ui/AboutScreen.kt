package com.srineere.airbridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.Band
import com.srineere.airbridge.BuildConfig
import dev.airpulse.fitbit_air.airResult

/**
 * Read in the order that matters to someone holding the phone: the band in
 * their hand first, then what this app is, then what becomes of their
 * readings, then what might stop it working. The transport and the credit sit
 * last because they explain everything above them and are the least urgent.
 *
 * Both halves are named. This screen described the relay alone for a while,
 * which made the Health tab -- the half the app opens on -- look like
 * something that had wandered in.
 */
@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<Map<*, *>?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    // Read from the band rather than assumed, so it is whatever the Air is
    // running today. A reading costs a session with the band, and the relay
    // may be holding one for a notification, so a refusal here is often just
    // bad timing rather than a band out of range.
    LaunchedEffect(attempt) {
        failure = null
        Band.readDeviceInfo(context, airResult(
            onSuccess = { info = it as? Map<*, *> },
            onError = { code, _ ->
                failure = if (code == "BUSY") "The relay is delivering a notification."
                else "The band did not answer. These come from the Air itself, so it needs to be in range."
            },
        ))
    }

    fun field(key: String): String? = (info?.get(key) as? String)?.takeIf { it.isNotBlank() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("About", onBack = onBack)

        SectionHeader("Your band")
        // The band's own name first: its model characteristic reads "67",
        // which is a number the wearer has no use for.
        Fact("Model", field("name") ?: field("model") ?: pending(info, failure))
        Fact("Firmware", field("firmware") ?: pending(info, failure))
        Fact("Made by", field("manufacturer") ?: pending(info, failure))
        val problem = failure
        if (problem == null) {
            Note("Read from the band itself, so it is whatever your Air is running today — not what it shipped with.")
        } else {
            Note(problem)
            TextButton(onClick = { attempt++ }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Try again")
            }
        }

        SectionHeader("This app")
        Fact("Air Bridge", BuildConfig.VERSION_NAME)
        Note(
            "The Air is screenless and ships with no notification support: its motor fires " +
                "for alarms and Smart Wake and nothing else. Air Bridge drives that motor, so " +
                "your phone's calls and notifications reach your wrist — and it reads back the " +
                "readings the band has already taken, and draws them."
        )
        Note(
            "Two halves, and they are independent. The relay runs whether or not anyone is " +
                "looking; the Health tab is the half you open it to see, which is why the app " +
                "opens on it."
        )
        Link(context, "srineere on GitHub", "https://github.com/srineere")

        SectionHeader("Alerts")
        Note(
            "Every app you enable gets its own vibration, and a separate one for calls if it " +
                "can ring. A caller can have their own vibration too, or be blocked — " +
                "separately for phone calls and for WhatsApp calls, because a person is a " +
                "different person in each."
        )
        Note(
            "A call rings until it stops ringing: answered, declined, missed or timed out. " +
                "Patterns and strength are chosen apart from each other — one pulse, two, a " +
                "ring, or silence, at either of the Air's two strengths."
        )
        Note(
            "Quiet hours, a pause, and a Stop button in the relay's own notification. Quiet " +
                "hours beat everything, including a person with a rule of their own."
        )

        SectionHeader("The Health tab")
        Note(
            "A grid of tiles you arrange — five sizes, reordered, resized, recoloured or " +
                "taken off — each carrying a trend measured in its own units. Behind every " +
                "tile is a screen: heart rate, resting heart rate, sleep, steps, energy in " +
                "against out, workouts, HRV, the vitals, and nutrition."
        )
        Note(
            "Sleep is scored against your own nights rather than a recommended figure, so 70 " +
                "means \"like your usual\" and not \"adequate\" — and the score is never shown " +
                "without the four parts it stands on. Nothing else here is scored, because a " +
                "number invented on top of a measurement is a claim this app will not make."
        )
        Note(
            "A live heart rate you ask for is the one figure no reader can produce, because " +
                "it is a measurement rather than a number already written. And the band can " +
                "buzz you from your own activity: once when you cross the step goal, and " +
                "hourly when an active hour falls short."
        )

        SectionHeader("Your readings")
        Note(
            "The band's readings go to Google Health, which files them in Health Connect, " +
                "and Air Bridge reads them back out from there. Thirteen kinds of reading, " +
                "and it writes exactly one thing: the food you log here, which is saved as a " +
                "nutrition entry like any other app's."
        )
        Note(
            "Because it reads Health Connect rather than the band, it is not really Air-only. " +
                "Anything whose app files readings there — a Pixel Watch, a Garmin, a Samsung, " +
                "a Polar — fills most of these screens too."
        )
        Note(
            "None of it leaves this phone. No account, no server, nothing uploaded. The band " +
                "is reached over Bluetooth directly, and your contacts are read only to put a " +
                "name on a number."
        )

        SectionHeader("What can break it")
        Note(
            "This is unofficial. Fitbit publishes no way in, so the way to the band was worked " +
                "out by reading the traffic — which means a firmware update to the Air, or an " +
                "update to Google Health, can stop it working without warning. Nothing here is " +
                "approved by Fitbit or by Google."
        )
        Note(
            "Two things the hardware simply cannot do, both tested rather than assumed: the " +
                "status light cannot be driven, and a wrist tap cannot be felt by this app. " +
                "So there is no colour-coding alerts, and no tapping to silence one."
        )

        SectionHeader("Reporting a problem")
        Note(
            "Unofficial and unsupported — but a good report is still worth making, and most " +
                "faults here come down to a version. Say what the band did or failed to do, " +
                "and include the four lines below: the app, this phone, its Android version, " +
                "and the firmware the Air is running."
        )
        ReportDetails(info)

        SectionHeader("How it reaches the band")
        Note(
            "The Air has no public way in. Air Bridge speaks the transport Fitbit's own app " +
                "speaks — Golden Gate, then DTLS, then CoAP — and there is no vibrate command " +
                "in it: a buzz is a side effect of writing to the band's settings. That is why " +
                "a ring holds one secure session open rather than opening a new one per pulse, " +
                "and why the first buzz of a call takes a few seconds to arrive."
        )
        Note(
            "It also means only one thing can hold the band at a time, which is why measuring " +
                "your heart rate pauses the relay, and why a long ring delays a sync."
        )

        SectionHeader("Credit")
        Note(
            "None of that transport was documented. It was worked out by Thore Koritzius, " +
                "whose fitbit_air project this is built on."
        )
        Link(context, "fitbit_air on GitHub", "https://github.com/ThoreKoritzius/fitbit-air")
        Note("MIT, with the bundled Golden Gate transport under Apache 2.0.")

        Box(Modifier.padding(bottom = 28.dp))
    }
}

/**
 * The four lines a report needs, gathered and copied in one tap.
 *
 * Asking someone to find their Android version and their band's firmware is
 * asking for a report without them in it. The firmware is already on this
 * screen, so the only new work is putting it somewhere it can be pasted.
 */
@Composable
private fun ReportDetails(info: Map<*, *>?) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    fun field(key: String): String? = (info?.get(key) as? String)?.takeIf { it.isNotBlank() }
    val firmware = field("firmware")

    val lines = listOf(
        "Air Bridge ${BuildConfig.VERSION_NAME}",
        "${Build.MANUFACTURER} ${Build.MODEL}",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        // Said plainly rather than left out: a report with no firmware is
        // different from one where the band was out of range, and only the
        // person sending it knows which.
        "Band firmware " + (firmware ?: "not read"),
    )

    lines.forEach { line ->
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
    }

    TextButton(
        onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            runCatching {
                clipboard?.setPrimaryClip(ClipData.newPlainText("Air Bridge", lines.joinToString("\n")))
                copied = true
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text(if (copied) "Copied" else "Copy these details")
    }
}

/** What a band field says before the band has answered. */
private fun pending(info: Map<*, *>?, failure: String?): String = when {
    failure != null -> "Unavailable"
    info == null -> "Reading…"
    else -> "Not reported"
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
)

@Composable
private fun Link(context: android.content.Context, label: String, url: String) {
    TextButton(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text(label)
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(15.dp),
        )
    }
}
