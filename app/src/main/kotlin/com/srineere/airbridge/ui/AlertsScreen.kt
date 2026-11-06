package com.srineere.airbridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import com.srineere.airbridge.RelayPattern
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.RelayPreset
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.health.StepGoalSettings
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.MorningMoment

@Composable
internal fun AlertsScreen(onOpen: (String) -> Unit) {
    val state = LocalRelayState.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Alerts")

        RelayStatusCard(state, onOpen)

        // Under the switch, because both answer the same question at
        // different lengths: off for good, quiet for the night.
        SettingsRow(
            title = "Schedule",
            subtitle = scheduleSummary(state),
            icon = Icons.Outlined.Schedule,
            onClick = { onOpen("schedule") },
        )

        SectionHeader("What gets through")

        SettingsRow(
            title = "Calls",
            subtitle = callsSummary(state),
            icon = Icons.Outlined.Call,
            onClick = { onOpen("calls") },
        )
        SettingsRow(
            title = "Apps",
            subtitle = appsSummary(state),
            icon = Icons.Outlined.Apps,
            onClick = { onOpen("apps") },
        )
        SettingsRow(
            title = "People",
            subtitle = peopleSummary(state),
            icon = Icons.Outlined.People,
            onClick = { onOpen("people") },
        )
        // Under "what gets through", because that is what these are: two
        // more things that reach the wrist, with the app rather than a
        // notification behind them.
        SectionHeader("From your activity")
        // First: it is the one of the three that arrives whether or not you
        // are looking, and the only one that goes to the phone rather than
        // to the wrist. Not in StepBuzzRows, because the Steps screen draws
        // those too and a morning report is not a step setting.
        SettingsRow(
            title = "Morning report",
            subtitle = morningSummary(LocalContext.current),
            icon = Icons.Outlined.WbSunny,
            onClick = { onOpen(MorningMoment.ROUTE) },
        )
        StepBuzzRows(onOpen, goals = false)

        if (state.history.isNotEmpty()) {
            // The newest few answer "did that buzz come from what I think it
            // did"; the rest is for when it did not, and lives behind this.
            SectionHeader(
                text = "Recent",
                action = if (state.history.size > RECENT) "Show all" else null,
                onAction = { onOpen("activity") },
            )
            val now = nowTicking()
            state.history.take(RECENT).forEach { entry -> ActivityRow(entry, state, now) }
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One switch, not three buttons. Start, Pause and Stop asked the wearer to
 * hold a state machine in their head for something that is either relaying or
 * not; pause survives as the notification's own action, where a fifteen minute
 * silence is what is actually wanted.
 */
@Composable
private fun RelayStatusCard(state: RelayState, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val running = relayRunning()
    val blocking = remember(resumeTicks()) { blockingGrants(context) }
    val paused = state.config.silence.reason(System.currentTimeMillis()) != null

    // Only on the way on. Turning it off needs no explaining: the thing it
    // explains is what you have just stopped.
    val (askStart, startDialog) = explained(Explain.Relay) { state.start() }
    startDialog()
    StatusToggle(
        title = when {
            // No subtext, so anything that has to be said is said here or not
            // at all.
            blocking > 0 && !running -> "$blocking thing${if (blocking == 1) "" else "s"} to allow"
            running && paused -> "Paused"
            running -> "Relaying"
            else -> "Not relaying"
        },
        live = running,
        onToggle = { wanted ->
            when {
                // It cannot start, so the switch takes you to the reason
                // instead of pretending to flip.
                wanted && blocking > 0 -> onOpen("permissions")
                wanted -> askStart()
                else -> state.stop()
            }
        },
    )
}

/**
 * What the Calls row says without being opened: how a call rings, and the one
 * exception worth knowing from here. Strength, and a count of people who
 * merely buzz differently, belong on the screen itself -- a row that lists
 * every setting is a worse copy of the screen behind it.
 */
/**
 * The two buzzes the app raises itself.
 *
 * Shared by the Alerts tab and the Steps screen rather than written twice:
 * they are one setting, and two copies of a switch are two chances for the
 * screens to disagree about what the band is going to do.
 *
 * Each row opens its pulse, the way an app or a caller does, and the switch
 * is its own target: tapping the row changes how it feels, tapping the
 * switch changes whether it happens.
 */
@Composable
internal fun StepBuzzRows(onOpen: (String) -> Unit, goals: Boolean = true) {
    val context = LocalContext.current
    val relay = LocalRelayState.current
    var settings by remember { mutableStateOf(HealthSettings.read(context)) }

    fun update(next: StepGoalSettings) {
        settings = next
        HealthSettings.write(context, next)
        // The service keeps its own schedule, so it has to be told rather
        // than left to notice at the next quarter hour.
        CalendarRelayService.active?.settingsChanged()
    }

    SettingsRow(
        title = "Buzz me at ${NumberFormat.getIntegerInstance().format(settings.goal)} steps",
        subtitle = "Once a day, when you cross it · " +
            describe(relay.preset(VibrationTarget.StepGoal) ?: relay.inherited(VibrationTarget.StepGoal)),
        icon = Icons.Outlined.DirectionsWalk,
        onClick = { onOpen("vibration/${VibrationTarget.StepGoal.encode()}") },
        trailing = {
            RowSwitch(
                checked = settings.buzzOnGoal,
                onCheckedChange = { update(settings.copy(buzzOnGoal = it)) },
            )
        },
    )
    SettingsRow(
        title = "Nudge me each hour",
        subtitle = "${settings.hourlyGoal} steps, " +
            "${"%02d".format(settings.activeFrom)}:00–${"%02d".format(settings.activeUntil)}:00 · " +
            describe(relay.preset(VibrationTarget.HourlyNudge) ?: relay.inherited(VibrationTarget.HourlyNudge)),
        icon = Icons.Outlined.Schedule,
        onClick = { onOpen("vibration/${VibrationTarget.HourlyNudge.encode()}") },
        trailing = {
            RowSwitch(
                checked = settings.hourlyNudge,
                onCheckedChange = { update(settings.copy(hourlyNudge = it)) },
            )
        },
    )
    // Only while the nudge is on. A switch for a thing that is not happening
    // is a question about nothing, and the row above is where you say
    // whether it happens at all.
    if (settings.hourlyNudge) {
        SettingsRow(
            indent = Indent.UnderIcon,
            title = "Also show it on this phone",
            subtitle = "A silent notification each time it nudges. Change its sound " +
                "or hide it in Android's own notification settings.",
            trailing = {
                RowSwitch(
                    checked = settings.nudgeOnPhone,
                    onCheckedChange = { update(settings.copy(nudgeOnPhone = it)) },
                )
            },
        )
    }
    // Both of these go through the relay's link to the band, so neither can
    // fire while it is off. Said only when it is off, because a caveat that
    // is always on screen stops being read.
    if (!relayRunning() && (settings.buzzOnGoal || settings.hourlyNudge)) {
        Text(
            "Neither can reach the band while relaying is off — that switch is " +
                "what holds the link.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.hrv,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
    // Only where the figures themselves live. On the Alerts tab the question
    // is what reaches the wrist, and a door into the step goal is a second
    // answer to a question nobody asked there.
    if (goals) {
        SettingsRow(
            title = "Goals",
            subtitle = "The step goal, the hourly figure, and when it may buzz",
            icon = Icons.Outlined.Flag,
            onClick = { onOpen("healthgoals") },
        )
    }
}

/** What the Morning report row says without being opened. */
@Composable
private fun morningSummary(context: android.content.Context): String {
    val settings = remember(resumeTicks()) { HealthSettings.morning(context) }
    return when {
        !settings.on -> "A summary of your night, when you want one"
        settings.afterWaking -> "Each morning, once your band writes the night"
        else -> "Each morning at ${settings.clock}, on this phone"
    }
}

private fun callsSummary(state: RelayState): String {
    if (!state.callsOn) return "Off"
    val ring = shape(state.config.defaultCallPreset)
    val silenced = state.config.callers.count { it.value.preset?.silent == true }
    return if (silenced == 0) ring else "$ring · $silenced silenced"
}

/** A count, not a list: the names were a worse copy of the Apps screen. */
private fun appsSummary(state: RelayState): String = when (val count = (state.config.selectedPackages - state.dialer).size) {
    0 -> "No apps chosen yet"
    1 -> "1 app"
    else -> "$count apps"
}

/** The pattern on its own, where there is no room for how hard it buzzes. */
internal fun shape(preset: RelayPreset): String = when (preset.pattern) {
    RelayPattern.SILENT -> "Silent"
    RelayPattern.ONE -> "One pulse"
    RelayPattern.TWO -> "Two pulses"
    RelayPattern.RING -> "Rings until answered"
}

internal fun describe(preset: RelayPreset): String {
    if (preset.pattern == RelayPattern.SILENT) return "Silent"
    val strength = if (preset.strength == RelayPreset.FULL_STRENGTH) "strong" else "light"
    return "${shape(preset)} · $strength"
}
