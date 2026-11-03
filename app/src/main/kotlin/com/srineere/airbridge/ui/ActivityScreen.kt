package com.srineere.airbridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayActivity
import com.srineere.airbridge.RelayPreset
import com.srineere.airbridge.ui.theme.extras
import java.util.concurrent.TimeUnit

/** How much of the activity the Alerts tab shows before handing over. */
internal const val RECENT = 4

/**
 * Everything the relay has done lately.
 *
 * The tab shows only the last few: the question there is "did that buzz come
 * from what I think it did", which the newest entries answer. The rest is for
 * when something looks wrong and the answer is further back.
 */
@Composable
internal fun ActivityScreen(onBack: () -> Unit) {
    val state = LocalRelayState.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Activity", onBack = onBack)

        if (state.history.isEmpty()) {
            Text(
                "Nothing yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        state.history.forEach { entry -> ActivityRow(entry, state) }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** One thing the relay did. */
@Composable
internal fun ActivityRow(entry: RelayActivity, state: RelayState) = SettingsRow(
    title = state.label(entry.sourcePackage),
    subtitle = describeActivity(entry.stage, entry.errorCode, entry.preset),
    trailing = {
        Text(
            ago(entry.atMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
)

private fun describeActivity(stage: String, errorCode: String?, preset: String?): String {
    val shape = preset?.let { runCatching { describe(RelayPreset.parse(it)) }.getOrNull() }
    return when (stage) {
        "verified" -> shape ?: "Buzzed"
        "ring_started" -> "Started ringing"
        "ring_stopped" -> "Ring ended"
        else -> errorCode?.lowercase()?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Did not reach the band"
    }
}

private fun ago(atMillis: Long): String {
    val delta = (System.currentTimeMillis() - atMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours hr"
        else -> "$days d"
    }
}
