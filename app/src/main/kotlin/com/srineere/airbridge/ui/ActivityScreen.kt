package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.text.format.DateFormat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.RelayActivity
import com.srineere.airbridge.RelayPreset
import com.srineere.airbridge.ui.theme.extras
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

        val now = nowTicking()
        state.history.forEach { entry -> ActivityRow(entry, state, now, absolute = true) }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One thing the relay did.
 *
 * The tab says how long ago, because there the question is whether that buzz
 * was the thing just now. This screen says when, because by the time you are
 * reading it the question is which of several alerts it was -- and "3 hr" is
 * no use against a call log that reads in clock time.
 */
@Composable
internal fun ActivityRow(
    entry: RelayActivity,
    state: RelayState,
    now: Long,
    absolute: Boolean = false,
) {
    val context = LocalContext.current
    // The phone's own 12 or 24 hour habit, not this file's opinion of it.
    val clock = remember(context) { DateFormat.getTimeFormat(context) }
    val app = state.apps.firstOrNull { it.packageName == entry.sourcePackage }
    val badge = badgeFor(entry.sourcePackage, state.dialer, entry.commandId)
    // The history holds only the identity hash, so the name is looked up
    // here, from the same places a person's screen finds them. Someone who
    // rang once before a restart is no longer nameable, and the app's own
    // name stands in rather than a blank.
    val who = entry.callerId?.let { state.callerLabel(it) }
    SettingsRow(
        title = who ?: state.label(entry),
        subtitle = listOfNotNull(
            state.label(entry).takeIf { who != null },
            describeActivity(entry.stage, entry.errorCode, entry.preset),
        ).joinToString(" · "),
        // A drawn badge for the things every phone has, so a list of alerts
        // reads as one list rather than a row of launcher icons shouting in
        // four different palettes. Its own colour, not the accent: these name
        // what kind of thing happened, and a column of identical green tiles
        // would say nothing that the words beside them do not.
        leading = when {
            badge != null -> ({ Badge(badge) })
            app != null -> ({ AppIcon(app, size = 34) })
            else -> null
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (absolute) clock.format(Date(entry.atMillis)) else ago(entry.atMillis, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (absolute) {
                    Text(
                        day(entry.atMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extras.disabled,
                    )
                }
            }
        },
    )
}

/**
 * A drawn stand-in for an app, with a colour of its own.
 *
 * Deliberately outside the theme palette. Everything else in this app is one
 * accent because it is all one setting; this list is a record of different
 * kinds of thing, and the colour is the fastest way to read which.
 */
private class AppBadge(
    val icon: ImageVector,
    private val darkTile: Long, private val darkInk: Long,
    private val lightTile: Long, private val lightInk: Long,
) {
    fun tile(dark: Boolean) = Color(if (dark) darkTile else lightTile)
    fun ink(dark: Boolean) = Color(if (dark) darkInk else lightInk)
}

private val CALL = AppBadge(Icons.Outlined.Call, 0xFF1F3B2F, 0xFF7FE0B2, 0xFFD7F0E2, 0xFF1D5B3C)
private val CHAT = AppBadge(Icons.Outlined.ChatBubbleOutline, 0xFF26303A, 0xFF9FD6E8, 0xFFD9ECF5, 0xFF1A4E63)
private val DATE = AppBadge(Icons.Outlined.CalendarMonth, 0xFF332B22, 0xFFE8C48F, 0xFFF6E7CE, 0xFF6B4A17)
private val WAKE = AppBadge(Icons.Outlined.Alarm, 0xFF2B2433, 0xFFCFA8E8, 0xFFEADDF5, 0xFF4C2E63)
private val POST = AppBadge(Icons.Outlined.MailOutline, 0xFF3A2326, 0xFFE89FA6, 0xFFF8DFE2, 0xFF6B2730)
/** A buzz this app asked for itself, which belongs to no app at all. */
private val SELF = AppBadge(Icons.Outlined.Vibration, 0xFF1E262B, 0xFF9FD6E8, 0xFFE2EAEF, 0xFF2A4C5B)
// The app raises three of its own, and they are worth telling apart: a
// nudge you have had eight of today reads differently from a goal you
// crossed once.
private val GOAL = AppBadge(Icons.Outlined.DirectionsWalk, 0xFF14332A, 0xFF7FE0B2, 0xFFD7F0E2, 0xFF1D5B3C)
private val NUDGE = AppBadge(Icons.Outlined.Schedule, 0xFF2B2433, 0xFFCFA8E8, 0xFFEADDF5, 0xFF4C2E63)

/**
 * The stock apps, by what they are rather than by who makes them: the package
 * name is the only clue available here, and every maker's dialer still has
 * "dialer" in it.
 */
private fun badgeFor(packageName: String?, dialer: String, commandId: String = ""): AppBadge? {
    val name = packageName ?: return when {
        commandId.startsWith("goal-") -> GOAL
        commandId.startsWith("nudge-") -> NUDGE
        else -> SELF
    }
    return when {
        name == dialer || "dialer" in name || "telecom" in name -> CALL
        "calendar" in name -> DATE
        "messaging" in name || ".mms" in name || ".sms" in name -> CHAT
        "deskclock" in name || "alarm" in name -> WAKE
        name.substringAfterLast('.') == "gm" || "mail" in name -> POST
        else -> null
    }
}

@Composable
private fun Badge(badge: AppBadge) {
    val dark = isSystemInDarkTheme()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(badge.tile(dark)),
    ) {
        Icon(
            badge.icon,
            contentDescription = null,
            tint = badge.ink(dark),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The day it happened, named where a name is clearer than a date. */
private fun day(atMillis: Long): String {
    val midnight = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = TimeUnit.MILLISECONDS.toDays(midnight - atMillis)
    return when {
        atMillis >= midnight -> "Today"
        days < 1 -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(atMillis))
    }
}

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

private fun ago(atMillis: Long, now: Long): String {
    val delta = (now - atMillis).coerceAtLeast(0)
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
