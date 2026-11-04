package com.srineere.airbridge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import com.srineere.airbridge.CalendarRelayService
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.StepGoalSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.roundToInt

/**
 * Steps in full: today against the goal, then the month against itself.
 *
 * The goal line runs through both charts, because it is the only reason a
 * step count has a shape at all -- 8,420 means nothing until you know what
 * you were aiming at.
 */
@Composable
internal fun HealthStepsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val steps = LocalTileColour.current ?: MaterialTheme.extras.steps
    val series = data.snapshot.steps
    // One copy of the settings for the whole screen: the ring, the bars and
    // the switches all mean the same goal, and reading it twice is how they
    // would come to disagree after a change.
    var settings by remember { mutableStateOf(HealthSettings.read(context)) }
    val goal = settings.goal
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    val axes = rememberAxes()
    val window = rememberChartWindow(30)
    var picked by remember { mutableStateOf<Int?>(null) }
    val shown = window.slice(series) { it.day }

    fun update(next: StepGoalSettings) {
        settings = next
        HealthSettings.write(context, next)
        // The service holds its own schedule, so it has to be told rather
        // than left to notice at the next quarter hour.
        CalendarRelayService.active?.settingsChanged()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Steps", onBack = onBack)

        if (series.isEmpty()) {
            Text(
                "No step data yet. Health Connect fills this in as your band and " +
                    "phone report.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            return@Column
        }

        // One card, not two. The ring and the bars are the same subject at
        // two scales -- where today is, and where the days before it were --
        // and splitting them put the goal in one card and the progress
        // against it in another.
        DetailCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(
                    progress = data.stepProgress(goal),
                    colour = steps,
                    track = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(86.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(data.stepProgress(goal) * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "of goal",
                            style = MaterialTheme.typography.labelSmall,
                            color = steps,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        count(data.stepsToday),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (data.stepsToGo(goal) > 0) {
                            "${count(data.stepsToGo(goal))} to go · goal ${count(goal.toLong())}"
                        } else {
                            "goal met today"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            WindowBar(
                window = window,
                options = listOf(7, 30, 90),
                onChange = {
                    picked = null
                    state.widen(context, window.daysNeeded)
                },
                modifier = Modifier.padding(top = 14.dp),
            )

            if (shown.isEmpty()) {
                Text(
                    if (state.loading) "Reading these days…" else "Nothing recorded in these days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                GoalBars(
                    values = shown.map { it.value },
                    target = goal.toDouble(),
                    colour = steps,
                    dim = steps.copy(alpha = 0.35f),
                    height = 150.dp,
                    modifier = Modifier.padding(top = 12.dp)
                        .scrubbable(shown.size, CHART_GUTTER) { picked = it },
                    axes = axes,
                    xTicks = dateTicks(
                        shown.map { it.day },
                        if (window.isNow) "Today" else dayLabel(shown.last().day),
                    ),
                    goalLabel = shortNumber(goal.toDouble()),
                    selected = picked,
                )
                val at = picked?.let { shown.getOrNull(it) }
                ChartReadout(
                    day = at?.day,
                    value = at?.let { count(it.value.roundToInt().toLong()) + " steps" },
                    detail = at?.let { if (it.value >= goal) "goal met" else "under goal" },
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (shown.isNotEmpty()) {
                val met = shown.count { it.value >= goal }
                Row {
                    Stat(
                        "Daily average",
                        count((shown.map { it.value }.average()).roundToInt().toLong()),
                        Modifier.weight(1f),
                    )
                    Stat(
                        "Best day",
                        count(shown.maxOf { it.value }.roundToInt().toLong()),
                        Modifier.weight(1f),
                    )
                    Stat("Goal met", "$met of ${shown.size}", Modifier.weight(1f))
                }
            }
        }

        ByWeek(series, steps, axes)
        WhenYouWalk(data.snapshot.stepsHourly, steps, axes)

        data.stepsTrend?.let { trend ->
            SectionHeader("Against your usual")
            Text(
                "Half your days land between ${count(trend.usual.low.roundToInt().toLong())} " +
                    "and ${count(trend.usual.high.roundToInt().toLong())} steps. " +
                    "Your last seven average ${count(trend.recent.roundToInt().toLong())}, " +
                    describeDrift(trend.versusAverage, trend.smallestWorthwhileChange) + ".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // The same rows as the Alerts tab, from the same place: one setting
        // shown twice must not be two switches that can disagree.
        SectionHeader("Buzz me")
        StepBuzzRows(onOpen)

        // This said Health Connect kept one count when the band and the phone
        // both wrote. It does not -- an unfiltered read returns every writer
        // and adds them, which is what this app did until 4 November 2026 and
        // why the goal used to buzz early. The filtering is ours, and saying
        // so is the difference between a caveat and a claim.
        Text(
            "Your phone counts steps of its own and writes them here too. These " +
                "figures come from the sources set under Goals — by default the " +
                "band and not the phone, so one walk is counted once.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}


/**
 * This week against the weeks behind it.
 *
 * The daily bars say what each day was; they do not say whether the week as
 * a whole moved, and that is the only part of a step count worth acting on.
 * Rolling sevens again, counted back from today.
 */
@Composable
private fun ByWeek(series: List<Reading>, colour: Color, axes: Axes) {
    var picked by remember { mutableStateOf<Int?>(null) }
    if (series.size < 14) return
    val weeks = series.reversed().chunked(7).take(4).reversed().map { week ->
        week.map { it.value }.average()
    }
    val change = weeks.last() - weeks.dropLast(1).average()
    SectionHeader("By week")
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    count(weeks.last().roundToInt().toLong()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "a day, over the last seven",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val percent = if (weeks.dropLast(1).average() > 0) {
                change / weeks.dropLast(1).average() * 100
            } else {
                0.0
            }
            ChangeChip(percent, "%", colour)
        }
        GoalBars(
            values = weeks,
            target = null,
            colour = colour,
            dim = colour.copy(alpha = 0.4f),
            height = 86.dp,
            modifier = Modifier.padding(top = 12.dp)
                .scrubbable(weeks.size, CHART_GUTTER) { picked = it },
            axes = axes,
            xTicks = listOf(0f to "4 wk ago", 1f to "last 7 d"),
            selected = picked,
        )
        picked?.let { at ->
            weeks.getOrNull(at)?.let { value ->
                val back = (weeks.size - at - 1) * 7
                Text(
                    (if (back == 0) "last 7 days" else "${back + 1}–${back + 7} days ago") +
                        "  ${count(value.roundToInt().toLong())} steps a day",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Text(
            "Each bar is a week's daily average, oldest first.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * When, not just how many.
 *
 * The hourly nudge fires against a clock, so the question it raises is which
 * hours are already empty. This is that chart, and it is today only --
 * averaging a month of hours would smooth away the thing being looked for.
 */
@Composable
private fun WhenYouWalk(hourly: List<Double>, colour: Color, axes: Axes) {
    var picked by remember { mutableStateOf<Int?>(null) }
    if (hourly.size != 24 || hourly.sum() <= 0.0) return
    val busiest = hourly.indices.maxByOrNull { hourly[it] } ?: return
    SectionHeader("When you walk")
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Today",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "busiest ${"%02d".format(busiest)}:00",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalBars(
            values = hourly,
            target = null,
            colour = colour,
            dim = colour.copy(alpha = 0.4f),
            height = 96.dp,
            modifier = Modifier.padding(top = 10.dp)
                .scrubbable(hourly.size, CHART_GUTTER) { picked = it },
            axes = axes,
            xTicks = listOf(0f to "00", 0.25f to "06", 0.5f to "12", 0.75f to "18", 1f to "24"),
            highlightLast = false,
            selected = picked,
        )
        picked?.let { at ->
            hourly.getOrNull(at)?.let { value ->
                Text(
                    "%02d:00".format(at) +
                        "  ${count(value.roundToInt().toLong())} steps",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Says whether a move is worth noticing. Anything inside half a standard
 * deviation of your own scatter is noise, and calling it a trend would be
 * the app inventing significance it cannot support.
 */
internal fun describeDrift(versusAverage: Double, smallestWorthwhile: Double): String = when {
    kotlin.math.abs(versusAverage) <= smallestWorthwhile -> "which is ordinary variation"
    versusAverage > 0 -> "which is genuinely above your usual"
    else -> "which is genuinely below your usual"
}



