package com.srineere.airbridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.health.FoodEntry
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Burned against eaten, as two running totals across today.
 *
 * A single balance figure says where you ended up; two lines say how you got
 * there — that you were ahead all morning and ate it back at dinner. The gap
 * between the lines at any point is the balance at that hour.
 *
 * Both lines stop at the current hour. Drawing burn out to midnight would be
 * a forecast, and this screen has no business making one.
 */
@Composable
internal fun HealthBalanceDayScreen(onBack: () -> Unit) {
    val data by healthData(LocalContext.current)
    val snapshot = data.snapshot
    val extras = MaterialTheme.extras
    val burnedColour = extras.activity
    val eatenColour = extras.energy

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone).toEpochDay()
    val hourNow = LocalTime.now(zone).hour

    // Food arrives as entries at a time, not as an hourly aggregate, so the
    // eaten line is built here from the entries themselves.
    val eatenByHour = DoubleArray(24)
    snapshot.food.filter { it.day == today }.forEach { entry ->
        val hour = entry.at.atZone(zone).hour
        eatenByHour[hour] += entry.kcal ?: 0.0
    }
    val burnedByHour = snapshot.burnedHourly.takeIf { it.size == 24 } ?: List(24) { 0.0 }

    val axes = rememberAxes()
    var picked by remember { mutableStateOf<Int?>(null) }
    val burned = runningTotal(burnedByHour, hourNow)
    val eaten = runningTotal(eatenByHour.toList(), hourNow)
    val haveSomething = burned.lastOrNull()?.let { it > 0 } == true ||
        eaten.lastOrNull()?.let { it > 0 } == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Burned against eaten", subtitle = "Today", onBack = onBack)

        if (!haveSomething) {
            Text(
                "Nothing recorded today yet. Burn arrives from Google Health as " +
                    "your band syncs; food arrives when something logs it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            return@Column
        }

        val balance = (eaten.lastOrNull() ?: 0.0) - (burned.lastOrNull() ?: 0.0)
        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (balance < 0) "−" else "+") +
                            count(abs(balance).roundToInt().toLong()),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (balance < 0) "kcal behind, so far today"
                        else "kcal ahead, so far today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RunningTotals(
                first = burned,
                second = eaten,
                firstColour = burnedColour,
                secondColour = eatenColour,
                modifier = Modifier.padding(top = 14.dp)
                    .scrubbable(burned.size, CHART_GUTTER) { picked = it },
                height = 170.dp,
                axes = axes,
                xTicks = hourAxis(hourNow),
                selected = picked,
            )
            // The hour, and where the two totals stood at the end of it.
            val hour = picked?.takeIf { it in burned.indices }
            Text(
                if (hour == null) {
                    ""
                } else {
                    val gap = eaten[hour] - burned[hour]
                    "%02d:00".format(hour) +
                        "  ${count(burned[hour].roundToInt().toLong())} burned" +
                        " · ${count(eaten[hour].roundToInt().toLong())} eaten" +
                        " · ${if (gap < 0) "−" else "+"}" +
                        count(abs(gap).roundToInt().toLong())
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (hour == null) MaterialTheme.extras.disabled
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dp),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Row {
                Stat("Burned", burned.lastOrNull()?.let { kcal(it) }, Modifier.weight(1f), burnedColour)
                Stat("Eaten", eaten.lastOrNull()?.let { kcal(it) }, Modifier.weight(1f), eatenColour)
                Stat(
                    "Entries",
                    "${snapshot.food.count { it.day == today }}",
                    Modifier.weight(0.8f),
                )
            }
        }

        WhatMoved(snapshot.food.filter { it.day == today }, eatenColour)

        Text(
            "Burn is everything your body spent, not just exercise — the figure " +
                "starts well above zero before you have moved. Food shows only " +
                "what something logged, so a gap here is usually an unlogged meal " +
                "rather than a day of fasting.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}


/**
 * Whole hours across the part of the day that has happened.
 *
 * Four at most, so the labels do not collide; always including the hour it
 * is now, because "how far through the day is this" is the question the
 * chart's right-hand edge raises.
 */
private fun hourAxis(hourNow: Int): List<Pair<Float, String>> {
    if (hourNow < 1) return listOf(0f to "00")
    val every = (hourNow / 4).coerceAtLeast(1)
    val hours = (0..hourNow step every).toMutableList()
    if (hours.last() != hourNow) hours += hourNow
    return hours.map { hour -> hour / hourNow.toFloat() to "%02d".format(hour) }
}


/**
 * The entries behind the steps in the eaten line.
 *
 * A step line raises the question of what each step was, and the answer is
 * already in the data: these are the rows the line is made of, in the order
 * they happened.
 */
@Composable
private fun WhatMoved(entries: List<FoodEntry>, colour: Color) {
    if (entries.isEmpty()) return
    val clock = remember {
        DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm"))
            .withZone(ZoneId.systemDefault())
    }
    SectionHeader("What moved the line")
    DetailCard {
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            ) {
                Text(
                    clock.format(entry.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(54.dp),
                )
                Text(
                    entry.name ?: "Unnamed entry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.name == null) MaterialTheme.extras.disabled
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    entry.kcal?.let { "+" + count(it.roundToInt().toLong()) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colour,
                )
            }
        }
    }
}

/** Cumulative totals up to and including the current hour. */
private fun runningTotal(hours: List<Double>, until: Int): List<Double> {
    var sum = 0.0
    return (0..until.coerceIn(0, 23)).map { hour ->
        sum += hours.getOrElse(hour) { 0.0 }
        sum
    }
}

/** The figure a kcal stat shows, which both lines of this screen share. */
private fun kcal(value: Double): String = count(value.roundToInt().toLong()) + " kcal"
