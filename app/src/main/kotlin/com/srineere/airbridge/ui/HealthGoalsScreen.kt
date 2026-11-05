package com.srineere.airbridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthConnect
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.HealthSource
import com.srineere.airbridge.health.HealthSources
import com.srineere.airbridge.health.SourceChoice
import com.srineere.airbridge.health.StepGoalSettings
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The targets every goal line on this tab is drawn from.
 *
 * They are kept here because Health Connect has none to read. It carries
 * measurements and nothing else — the step and calorie goals you set inside
 * Google Health stay inside Google Health, and no API hands them over. So
 * this screen exists, each default names where it came from, and none of
 * them is presented as a fact about you.
 */
@Composable
internal fun HealthGoalsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.loadGoals(context) }
    val goals = state.goals

    // The nudge's own settings live with the relay's, because the service
    // reads them on its own schedule and has to be told when they move.
    fun setBuzz(next: StepGoalSettings) {
        HealthSettings.write(context, next)
        CalendarRelayService.active?.settingsChanged()
        state.refresh(context, force = true)
    }

    fun save(next: Goals) {
        state.saveGoals(context, next)
        // The step goal is also what the band buzzes at, so the relay's own
        // copy has to move with it or the wrist waits for the old number.
        HealthSettings.write(context, HealthSettings.read(context).copy(goal = next.steps))
        CalendarRelayService.active?.settingsChanged()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Goals", onBack = onBack)
        Text(
            "Health Connect carries measurements, not targets: whatever you set " +
                "inside Google Health stays there, and no app can read it. These " +
                "are Air Bridge's own, and every goal line on the tab is drawn " +
                "from them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        SectionHeader("Steps")
        GoalRow(
            value = count(goals.steps.toLong()),
            unit = "a day",
            note = "Your last seven average " +
                (data.stepsTrend?.let { count(it.recent.roundToInt().toLong()) } ?: "—") +
                ". Ten thousand is a 1960s pedometer slogan, not a finding — " +
                "set it where it is worth crossing.",
            onLess = { save(goals.copy(steps = (goals.steps - 500).coerceAtLeast(500))) },
            onMore = { save(goals.copy(steps = goals.steps + 500)) },
        )
        StepSourcePicker()

        SectionHeader("Sleep")
        GoalRow(
            value = "${goals.sleepMinutes / 60}h ${"%02d".format(goals.sleepMinutes % 60)}m",
            unit = "a night",
            note = "The adult range in the sleep literature is seven to nine " +
                "hours. This sets the dashed line on the sleep charts and " +
                "nothing else — the score measures you against your own nights.",
            onLess = { save(goals.copy(sleepMinutes = (goals.sleepMinutes - 15).coerceAtLeast(240))) },
            onMore = { save(goals.copy(sleepMinutes = (goals.sleepMinutes + 15).coerceAtMost(720))) },
        )

        SectionHeader("Energy")
        GoalRow(
            value = when {
                goals.energyKcal == 0 -> "Maintain"
                goals.energyKcal < 0 -> "−${count(abs(goals.energyKcal).toLong())}"
                else -> "+${count(goals.energyKcal.toLong())}"
            },
            unit = if (goals.energyKcal == 0) "" else "kcal a day",
            note = buildString {
                append("A deficit is negative, a surplus positive. ")
                if (goals.energyKcal != 0) {
                    append(
                        "At this rate a fortnight is about " +
                            "%.1f kg".format(abs(goals.fortnightKg)) +
                            (if (goals.energyKcal < 0) " off" else " on") +
                            ", by the 7,700 kcal-per-kilogram rule of thumb — " +
                            "which overstates real loss, so treat it as a check " +
                            "on the food log rather than a forecast.",
                    )
                } else {
                    append("No line is drawn on the balance chart while this is level.")
                }
            },
            onLess = { save(goals.copy(energyKcal = (goals.energyKcal - 50).coerceAtLeast(-1500))) },
            onMore = { save(goals.copy(energyKcal = (goals.energyKcal + 50).coerceAtMost(1500))) },
        )

        SectionHeader("Protein")
        val suggested = data.weightTrend?.let { Goals.proteinFor(it.latest) }
        GoalRow(
            value = goals.proteinGrams?.toString() ?: suggested?.toString() ?: "—",
            unit = "g a day",
            note = when {
                goals.proteinGrams != null -> "Your own figure."
                suggested != null ->
                    "Suggested from your weight at 1.6 g per kilogram, the point " +
                        "above which more stops helping. Change it to make it yours."
                else ->
                    "Nothing has written a weight, so there is nothing to derive " +
                        "this from. Set it by hand or leave it empty."
            },
            onLess = {
                val from = goals.proteinGrams ?: suggested ?: 100
                save(goals.copy(proteinGrams = (from - 5).coerceAtLeast(20)))
            },
            onMore = {
                val from = goals.proteinGrams ?: suggested ?: 100
                save(goals.copy(proteinGrams = (from + 5).coerceAtMost(400)))
            },
        )

        SectionHeader("Hourly nudge")
        val buzz = HealthSettings.read(context)
        GoalRow(
            value = "${buzz.hourlyGoal}",
            unit = "steps an hour",
            note = "The band buzzes ten minutes before the hour when you have not " +
                "crossed this. Two hundred and fifty is the figure most hourly " +
                "prompts use; it is about a two-minute walk.",
            onLess = { setBuzz(buzz.copy(hourlyGoal = (buzz.hourlyGoal - 50).coerceAtLeast(50))) },
            onMore = { setBuzz(buzz.copy(hourlyGoal = (buzz.hourlyGoal + 50).coerceAtMost(2000))) },
        )
        GoalRow(
            // Each row edits the figure it shows. One row showing the whole
            // range while its buttons moved only one end of it was a control
            // that lied about what it did.
            value = "%02d:00".format(buzz.activeFrom),
            unit = "from",
            note = "Before this the wrist is left alone, whatever the step count. " +
                "Quiet hours and pause still apply inside the window.",
            onLess = {
                setBuzz(buzz.copy(activeFrom = (buzz.activeFrom - 1).coerceAtLeast(0)))
            },
            onMore = {
                setBuzz(buzz.copy(activeFrom = (buzz.activeFrom + 1).coerceAtMost(buzz.activeUntil - 1)))
            },
        )
        GoalRow(
            value = "%02d:00".format(buzz.activeUntil),
            unit = "until",
            note = "The last hour that can buzz you. After it, nothing until " +
                "tomorrow morning.",
            onLess = {
                setBuzz(buzz.copy(activeUntil = (buzz.activeUntil - 1).coerceAtLeast(buzz.activeFrom + 1)))
            },
            onMore = {
                setBuzz(buzz.copy(activeUntil = (buzz.activeUntil + 1).coerceAtMost(23)))
            },
        )

        SectionHeader("Activity")
        GoalRow(
            value = "${goals.activeMinutes}",
            unit = "minutes a week",
            note = "The World Health Organization's lower bound for adults is 150 " +
                "minutes of moderate activity a week.",
            onLess = { save(goals.copy(activeMinutes = (goals.activeMinutes - 15).coerceAtLeast(0))) },
            onMore = { save(goals.copy(activeMinutes = (goals.activeMinutes + 15).coerceAtMost(1500))) },
        )

        Text(
            "The step goal and the hourly nudge reach the band. The rest draw " +
                "lines on charts and nothing more.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One target, with a step either way.
 *
 * Buttons rather than a text field: every one of these is a round number
 * nudged occasionally, and a keyboard for "8,500" is three taps more than
 * the thing deserves.
 */
/**
 * Which writers the step figures are counted from.
 *
 * It sits under the step goal because that is the number it changes, and
 * because the goal is what goes wrong when it is set badly: the band buzzes
 * at a figure reached by a walk counted twice.
 *
 * The list is whatever has actually written steps in the last fortnight, so
 * on a phone with one band it is two rows and needs no thought. The
 * enumeration costs a Health Connect read and is done off the main thread,
 * once, for the reason `InstalledApps` exists.
 */
@Composable
private fun StepSourcePicker() {
    val context = LocalContext.current
    val state = LocalHealthState.current
    var sources by remember { mutableStateOf<List<HealthSource>>(emptyList()) }
    var choice by remember { mutableStateOf(HealthSettings.stepSource(context)) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sources = withContext(Dispatchers.IO) {
            HealthConnect.clientOrNull(context)
                ?.let { HealthSources.stepWriters(context, it, HealthConnect.granted(it)) }
                .orEmpty()
        }
        loaded = true
    }

    fun pick(next: SourceChoice) {
        choice = next
        HealthSettings.writeStepSource(context, next)
        state.refresh(context, force = true)
    }

    val phone = sources.firstOrNull { it.isPhone }

    DetailCard {
        Text(
            "Steps counted from",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
        )
        Text(
            when {
                !loaded -> "Looking at what has written steps lately…"
                sources.isEmpty() ->
                    "Nothing has written a step in the last fortnight, so there " +
                        "is nothing to choose between yet."
                phone == null ->
                    "Only your band is writing steps, so nothing is being left " +
                        "out whichever of these you pick."
                else ->
                    "Your phone counts its own steps and writes them here too. " +
                        "Counted alongside the band they make one walk into two, " +
                        "and the goal buzzes early — so by default they are left " +
                        "out."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )

        SourceOption(
            title = "Devices only",
            subtitle = phone?.let { "Everything but ${it.label}" } ?: "Every source writing now",
            selected = choice == SourceChoice.Wearables,
        ) { pick(SourceChoice.Wearables) }

        SourceOption(
            title = "Everything",
            subtitle = "Your phone's own count included",
            selected = choice == SourceChoice.Everything,
        ) { pick(SourceChoice.Everything) }

        // Naming each writer is what makes the two above checkable rather
        // than taken on trust.
        sources.forEach { source ->
            SourceOption(
                title = source.label,
                subtitle = if (source.isPhone) "This phone, on its own" else source.packageName,
                selected = (choice as? SourceChoice.Only)?.packageName == source.packageName,
            ) { pick(SourceChoice.Only(source.packageName)) }
        }
    }
}

@Composable
private fun SourceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) = SettingsRow(
    title = title,
    subtitle = subtitle,
    onClick = onClick,
    trailing = {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "Chosen",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    },
)

@Composable
private fun GoalRow(
    value: String,
    unit: String,
    note: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) = DetailCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onLess) {
            Icon(Icons.Outlined.Remove, contentDescription = "Less")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.Add, contentDescription = "More")
        }
    }
    Text(
        note,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.extras.disabled,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
