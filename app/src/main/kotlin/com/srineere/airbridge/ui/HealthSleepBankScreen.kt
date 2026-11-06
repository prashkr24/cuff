package com.srineere.airbridge.ui

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
import com.srineere.airbridge.ui.theme.extras
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a run of nights adds up to, against your own goal.
 *
 * A balance, carried. The figure is signed and the word beside it is
 * "down", never "debt": a debt is something owed to somebody, and nobody
 * is owed your sleep.
 *
 * Nothing here suggests a bedtime. The balance is arithmetic on nights
 * already slept; what to do about it depends on tomorrow, which this app
 * knows nothing about.
 */
@Composable
internal fun HealthSleepBankScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    LaunchedEffect(Unit) { state.loadGoals(context) }

    val extras = MaterialTheme.extras
    // The sleep tile's own colour for a night that fell short, and the
    // app's one positive ink for a night that did not. Two colours, and
    // the figure takes whichever the balance earned.
    val short = stagePalette().rem
    val over = extras.positive
    val axes = rememberAxes()
    val window = rememberChartWindow(7)
    var picked by remember { mutableStateOf<Int?>(null) }

    val goal = state.goals.sleepMinutes
    val days = remember(window.from, window.length) {
        (0 until window.length).map { window.from + it }
    }
    val byDay = remember(data.nights) { data.nights.associateBy { it.day } }
    // Null is a night nothing wrote, which is not a night of no sleep. It
    // leaves the balance exactly where it was.
    val nightly = remember(days, byDay, goal) {
        days.map { day -> byDay[day]?.let { it.asleep.toMinutes().toDouble() - goal } }
    }
    val running = remember(nightly) {
        var total = 0.0
        nightly.map { night -> total += night ?: 0.0; total }
    }
    val balance = running.lastOrNull() ?: 0.0
    val logged = nightly.count { it != null }
    val above = nightly.count { it != null && it > 0 }
    val below = nightly.count { it != null && it < 0 }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Sleep bank",
            subtitle = if (window.isNow) "last ${window.length} nights"
            else "${dayLabel(window.from)} to ${dayLabel(window.until)}",
            onBack = onBack,
        )

        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (logged == 0) "—" else signedSpan(balance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        logged == 0 -> extras.disabled
                        balance < 0 -> short
                        else -> over
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        logged == 0 -> "nothing to add up"
                        balance < 0 -> "down"
                        else -> "ahead"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
            Text(
                "Against ${span(goal.toDouble())} a night",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                // Said plainly, because a balance built on four nights of
                // seven is a different number from one built on seven and
                // nothing else on this screen would say so.
                if (logged == 0) {
                    "No night in these has a reading, so there is nothing to add up."
                } else {
                    "$logged of ${window.length} nights have a reading · " +
                        "$above over the goal, $below under."
                },
                style = MaterialTheme.typography.labelMedium,
                color = extras.disabled,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionHeader("How it got there")
        DetailCard {
            WindowBar(
                window = window,
                options = listOf(7, 14, 30),
                onChange = {
                    picked = null
                    state.widen(context, window.daysNeeded)
                },
                label = { "$it nights" },
            )
            if (logged == 0) {
                Text(
                    if (state.loading) "Reading these nights…"
                    else "No night in these was recorded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                return@DetailCard
            }
            BankChart(
                nightly = nightly,
                running = running,
                short = short,
                over = over,
                axis = MaterialTheme.colorScheme.outline,
                height = 150.dp,
                modifier = Modifier.padding(top = 10.dp)
                    .scrubbable(days.size, CHART_GUTTER) { picked = it },
                axes = axes,
                xTicks = dateTicks(
                    days,
                    dayLabel(days.last()),
                ),
                label = { span(it) },
                selected = picked,
                // The balance carried forward, which is the line and the
                // figure at the top of the screen. The bars are the working
                // behind it, and reading out a fortnight of them one by one
                // is not a summary.
                spokenAs = ChartLabel(
                    metric = "Sleep balance",
                    unit = "against your goal",
                    window = window.spokenRange(),
                    figure = {
                        (if (it < 0) "down " else "up ") + spokenMinutes(abs(it))
                    },
                ),
            )
            val at = picked?.takeIf { it in days.indices }
            ChartReadout(
                day = at?.let { index -> days[index] },
                value = at?.let { index ->
                    nightly[index]?.let(::signedSpan) ?: "nothing recorded"
                },
                detail = at?.let { index -> "balance " + signedSpan(running[index]) },
            )
            Text(
                "Bars are each night against the goal. The line is the balance " +
                    "carried forward.",
                style = MaterialTheme.typography.labelMedium,
                color = extras.disabled,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionHeader("Night by night")
        DetailCard {
            // Newest first, which is the order you ask the question in.
            days.reversed().forEachIndexed { index, day ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                NightRow(day, byDay[day]?.asleep?.toMinutes()?.toDouble(), goal, short, over)
            }
        }

        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One night: what it was, and what it did to the balance.
 *
 * A night nothing wrote says so rather than showing a dash that could as
 * easily mean nought hours. It is drawn dimmer for the same reason the
 * balance leaves it out: it is not evidence either way.
 */
@Composable
private fun NightRow(
    day: Long,
    minutes: Double?,
    goal: Int,
    short: androidx.compose.ui.graphics.Color,
    over: androidx.compose.ui.graphics.Color,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 2.dp),
) {
    Text(
        weekday(day),
        style = MaterialTheme.typography.bodyMedium,
        color = if (minutes == null) MaterialTheme.extras.disabled
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(42.dp),
    )
    Text(
        if (minutes == null) "band not worn — left out" else span(minutes),
        style = MaterialTheme.typography.bodyMedium,
        color = if (minutes == null) MaterialTheme.extras.disabled
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
    )
    val delta = minutes?.let { it - goal }
    Text(
        delta?.let(::signedSpan) ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = when {
            delta == null -> MaterialTheme.extras.disabled
            delta < 0 -> short
            else -> over
        },
        textAlign = TextAlign.End,
        modifier = Modifier.width(76.dp),
    )
}

private val weekdayFormat = DateTimeFormatter.ofPattern("EEE")

private fun weekday(epochDay: Long): String =
    weekdayFormat.format(LocalDate.ofEpochDay(epochDay))

/** A length of time, in the hours and minutes the rest of the tab uses. */
private fun span(minutes: Double): String {
    val total = abs(minutes).roundToInt()
    return if (total < 60) "${total}m" else "${total / 60}h ${"%02d".format(total % 60)}m"
}

/** The same, with the sign that makes it a balance rather than a length. */
private fun signedSpan(minutes: Double): String {
    val total = minutes.roundToInt()
    // A true minus, not a hyphen: it sits beside figures at the same
    // weight everywhere else on this tab.
    return (if (total < 0) "−" else "+") + span(minutes)
}
