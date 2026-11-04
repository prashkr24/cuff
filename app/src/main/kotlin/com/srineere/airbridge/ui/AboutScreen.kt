package com.srineere.airbridge.ui

import android.content.Intent
import android.net.Uri
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
            "Two halves. It sends the notifications you choose to your wrist, with the buzz " +
                "you choose, for the people you choose — which the Air cannot do on its own, " +
                "because its motor is built for alarms and nothing else. And it reads the " +
                "readings your band has already taken, and draws them."
        )
        Note(
            "The Health tab is the half you open it to see. The relay runs whether or not " +
                "anyone is looking."
        )
        Link(context, "Source on GitHub", "https://github.com/srineere/air-bridge")

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

        SectionHeader("Credit and licences")
        Note(
            "None of the above was documented. It was worked out by Thore Koritzius, whose " +
                "fitbit_air project this is built on and without which none of this would exist."
        )
        Link(context, "fitbit_air on GitHub", "https://github.com/ThoreKoritzius/fitbit-air")
        Note(
            "Air Bridge is MIT licensed. The bundled Golden Gate transport is Fitbit's own, " +
                "under Apache 2.0."
        )

        Box(Modifier.padding(bottom = 28.dp))
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
