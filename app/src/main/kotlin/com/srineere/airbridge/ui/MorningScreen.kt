package com.srineere.airbridge.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.MorningMoment
import com.srineere.airbridge.health.MorningReport
import com.srineere.airbridge.health.morningReport
import com.srineere.airbridge.ui.theme.extras
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Where the morning notification lands.
 *
 * Nothing here is new. Every figure is already on the Health tab, and that
 * is the point: a report that computed something of its own would be a
 * fourth opinion to keep in step with three others.
 *
 * Last night, then the morning's own readings, then yesterday closed off.
 * Three sections, not a scroll — a report nobody finishes before the kettle
 * boils is a report nobody reads.
 */
@Composable
internal fun MorningScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    LaunchedEffect(Unit) { state.loadGoals(context) }
    val extras = MaterialTheme.extras
    val sleep = extras.sleepRem

    var settings by remember { mutableStateOf(HealthSettings.morning(context)) }
    val report = remember(data.snapshot, state.goals) {
        morningReport(data.snapshot, state.goals)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Good morning",
            subtitle = DateTimeFormatter.ofPattern("EEEE, d MMMM").format(LocalDate.now()) +
                " · nothing here is new",
            onBack = onBack,
        )

        // Last night first: it is the only thing that happened since you
        // last looked at any of this.
        SectionHeader("Last night", action = "Sleep", onAction = { onOpen("healthsleep") })
        DetailCard {
            val night = report.night
            if (night == null) {
                Text(
                    "No night was recorded. Google Health writes one once your band " +
                        "syncs in the morning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        night.asleep.asHoursMinutes(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = sleep,
                    )
                    report.score?.total?.let { score ->
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = sleep,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                        Text(
                            " score",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.extras.disabled,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                StageBars(
                    nights = listOf(night.fourStages()),
                    deep = extras.sleepDeep,
                    light = extras.sleepLight,
                    rem = extras.sleepRem,
                    awake = extras.sleepAwake,
                    height = 26.dp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                report.score?.let { score ->
                    Text(
                        // The score's own working, which is the only thing
                        // that makes a composite worth printing.
                        score.parts.sortedBy { it.second }
                            .joinToString(" · ") { (name, value) ->
                                name.lowercase() + " " + when {
                                    value < 60 -> "short"
                                    value < 80 -> "fair"
                                    else -> "usual"
                                }
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.extras.disabled,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // Then the morning's own readings, each against its own band. Three
        // lines rather than three cards.
        SectionHeader("This morning")
        DetailCard {
            MorningLine(
                "Standing",
                report.standing.replaceFirstChar { it.uppercase() },
                null,
                extras.hrv,
            ) { onOpen("healthvitals") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MorningLine(
                "Resting heart rate",
                report.resting?.latest?.roundToInt()?.toString() ?: "—",
                MorningReport.against(report.resting),
                extras.heart,
            ) { onOpen("healthtrend") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MorningLine(
                "HRV",
                report.hrv?.latest?.roundToInt()?.toString() ?: "—",
                MorningReport.against(report.hrv),
                extras.hrv,
            ) { onOpen("healthhrv") }
        }

        // Yesterday, closed off. A day you can still change is on the tab.
        SectionHeader("Yesterday")
        DetailCard {
            if (report.week.isEmpty()) {
                Text(
                    "Nothing was written for yesterday.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Steps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when (report.metStepGoal) {
                            true -> "goal met"
                            false -> "goal not met"
                            null -> "no goal set"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (report.metStepGoal == true) extras.positive
                        else MaterialTheme.extras.disabled,
                    )
                }
                GoalBars(
                    values = report.week.map { it.value },
                    target = report.stepGoal.takeIf { it > 0 }?.toDouble(),
                    colour = extras.steps,
                    dim = extras.steps.copy(alpha = 0.35f),
                    height = 54.dp,
                    modifier = Modifier.padding(top = 8.dp),
                    // The last bar is yesterday, which is the day this card
                    // is about, so it keeps its highlight.
                    highlightLast = true,
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        report.yesterdaySteps?.let { count(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    report.weekAverage?.let {
                        Text(
                            "your last seven average ${count(it.roundToInt().toLong())}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.extras.disabled,
                        )
                    }
                }
            }
        }

        SectionHeader("Sending")
        SettingsRow(
            title = "Send this each morning",
            subtitle = when {
                !settings.on -> "Off — nothing is sent"
                settings.afterWaking -> "Once your band writes the night, on the lock screen"
                else -> "At ${settings.clock}, on the lock screen"
            },
            trailing = {
                RowSwitch(
                    checked = settings.on,
                    onCheckedChange = { wanted ->
                        settings = settings.copy(on = wanted)
                        HealthSettings.writeMorning(context, settings)
                        MorningMoment.schedule(context)
                    },
                )
            },
        )
        if (settings.on) {
            SettingsRow(
                indent = Indent.UnderTitle,
                title = "Wait for the night",
                // The honest name for "after you wake up". Nothing here can
                // see you open your eyes; it can see the night arrive.
                subtitle = "Sent when your band syncs it, rather than at an hour",
                trailing = {
                    RowSwitch(
                        checked = settings.afterWaking,
                        onCheckedChange = { wanted ->
                            settings = settings.copy(afterWaking = wanted)
                            HealthSettings.writeMorning(context, settings)
                            MorningMoment.schedule(context)
                        },
                    )
                },
            )
        }
        if (settings.on && !settings.afterWaking) {
            SettingsRow(
                indent = Indent.UnderTitle,
                title = "At",
                subtitle = "The time it arrives",
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            settings = settings.copy(minute = hour * 60 + minute)
                            HealthSettings.writeMorning(context, settings)
                            MorningMoment.schedule(context)
                        },
                        settings.hour,
                        settings.past,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
                trailing = {
                    Text(settings.clock, style = MaterialTheme.typography.bodyLarge)
                },
            )
        }

        Text(
            if (settings.afterWaking) {
                "Sent once, when your band writes the night — it looks from 06:00 and " +
                    "gives up at 11:00, sending what there is. Never during quiet hours. " +
                    "It computes nothing new, which is why it is safe to miss."
            } else {
                "Sent once, at the time you set, and never during quiet hours. It " +
                    "computes nothing new — every figure here is already on the Health " +
                    "tab, which is why it is cheap to send and safe to miss."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One reading, its figure, and where that figure sits.
 *
 * Three columns at fixed widths so the figures line up down the card: a
 * column of numbers somebody has to hunt for is a column nobody reads.
 */
@Composable
private fun MorningLine(
    name: String,
    value: String,
    band: String?,
    tint: androidx.compose.ui.graphics.Color,
    onOpen: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
        .clickable(onClick = onOpen),
) {
    Text(
        name,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
    )
    Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (band == null) MaterialTheme.colorScheme.onSurface else tint,
        maxLines = 1,
    )
    if (band != null) {
        Text(
            band,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            textAlign = TextAlign.End,
            modifier = Modifier.width(84.dp).padding(start = 8.dp),
        )
    }
}
