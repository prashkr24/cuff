package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.health.Goals
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Energy: burned against eaten, and whether the arithmetic is holding.
 *
 * The screen is only as honest as the food log, so it says what it is
 * working from rather than printing a deficit built from an unlogged lunch.
 * Burned is total calories -- resting plus movement -- because comparing
 * food against active burn alone would invent a deficit the size of a day's
 * basal rate.
 */
@Composable
internal fun HealthEnergyScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    LaunchedEffect(Unit) { state.loadGoals(context) }
    val goals = state.goals
    val axes = rememberAxes()
    val window = rememberChartWindow(30)
    var picked by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val energy = LocalTileColour.current ?: MaterialTheme.extras.energy
    val burned = data.snapshot.burnedToday
    val eaten = data.snapshot.eatenToday

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Energy", subtitle = "Today", onBack = onBack)

        if (burned == null) {
            Note("No calories burned recorded today. Google Health writes them once your band syncs.")
            return@Column
        }

        DetailCard {
            if (eaten == null) {
                // The honest empty state: half the sum is missing, so the
                // balance is unknowable rather than equal to everything burned.
                Text(
                    count(burned.roundToInt().toLong()),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "kcal burned today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Nothing logged to eat, so there is no balance to show. Air " +
                        "Bridge reads food from any app that writes it to Health " +
                        "Connect — Google Health's own log included.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                val balance = eaten - burned
                val word = if (balance < 0) "Deficit" else "Surplus"
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                (if (balance < 0) "−" else "+") +
                                    count(abs(balance).roundToInt().toLong()),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                " kcal today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                    Surface(
                        color = energy.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            word,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = energy,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                // One above the other, each the full width: these are two
                // quantities on one scale, and side by side they were two
                // half-width tracks that read as one bar in two colours.
                Bar("Burned", burned, maxOf(burned, eaten), energy, Modifier.padding(top = 14.dp))
                Bar(
                    "Eaten",
                    eaten,
                    maxOf(burned, eaten),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // What the target would mean by the end of the day, said as
                // plainly as the design does: a target with no distance to it
                // is just a number in a settings screen.
                Text(
                    targetLine(balance, goals.energyKcal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }

        val all = data.balanceByDay
        val series = window.slice(all) { it.day }
        if (all.size >= 3) {
            SectionHeader("Balance")
            DetailCard {
                WindowBar(
                    window = window,
                    options = listOf(7, 30, 90),
                    onChange = {
                        picked = null
                        state.widen(context, window.daysNeeded)
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (series.isEmpty()) {
                    Text(
                        if (state.loading) "Reading these days…"
                        else "No day in these has both a burn and a logged meal, " +
                            "so there is no balance to draw.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                    return@DetailCard
                }
                DivergingBars(
                    values = series.map { it.value },
                    below = energy,
                    above = MaterialTheme.colorScheme.onSurfaceVariant,
                    axis = MaterialTheme.colorScheme.onSurfaceVariant,
                    height = 150.dp,
                    modifier = Modifier.scrubbable(series.size, CHART_GUTTER) { picked = it },
                    axes = axes,
                    xTicks = listOf(
                        0f to dayLabel(series.first().day),
                        1f to if (window.isNow) "Today" else dayLabel(series.last().day),
                    ),
                    target = goals.energyKcal.takeIf { it != 0 }?.toDouble(),
                    goalLabel = goals.energyKcal.takeIf { it != 0 }
                        ?.let { shortNumber(it.toDouble()) },
                    selected = picked,
                )
                val at = picked?.let { series.getOrNull(it) }
                ChartReadout(
                    day = at?.day,
                    value = at?.let {
                        (if (it.value < 0) "−" else "+") +
                            count(abs(it.value).roundToInt().toLong()) + " kcal"
                    },
                    detail = at?.let { if (it.value < 0) "deficit" else "surplus" },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                // Today is half a day until it ends, so the pace is taken
                // from the days that finished. Including it made the figure
                // swing by a kilogram between breakfast and dinner.
                val whole = series.dropLastWhile { it.day >= LocalDate.now().toEpochDay() }
                    .ifEmpty { series }
                val average = whole.map { it.value }.average()
                val weekly = average * 7 / Goals.KCAL_PER_KG
                // Three figures, each a different question. "Days logged"
                // was a fourth that only said how much of the chart above
                // was real, which the chart already shows by having gaps.
                Row {
                    Stat(
                        "Average",
                        (if (average < 0) "−" else "+") +
                            count(abs(average).roundToInt().toLong()) + " kcal",
                        Modifier.weight(1.4f),
                    )
                    Stat(
                        "A week",
                        (if (weekly < 0) "−" else "+") + "%.1f kg".format(abs(weekly)),
                        Modifier.weight(1.1f),
                    )
                    Stat(
                        "In deficit",
                        "${series.count { it.value < 0 }} days",
                        Modifier.weight(1.2f),
                    )
                }
            }
        }

        Completeness(data)

        if (data.snapshot.proteinToday != null) {
            SectionHeader("Macros today")
            DetailCard {
                Macro("Protein", data.snapshot.proteinToday, MaterialTheme.extras.heart)
                Macro("Carbs", data.snapshot.carbsToday, MaterialTheme.extras.steps)
                Macro("Fat", data.snapshot.fatToday, MaterialTheme.extras.hrv)
            }
        }

        SettingsRow(
            title = "Nutrition",
            subtitle = "The split, a fortnight of it, and the food log",
            onClick = { onOpen("healthnutrition") },
        )
        SettingsRow(
            title = "Burned against eaten",
            subtitle = "Both running totals, across today",
            onClick = { onOpen("healthbalance") },
        )

        data.weightTrend?.let { trend ->
            SectionHeader("Weight")
            DetailCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "%.1f kg".format(trend.latest),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "usual %.1f–%.1f".format(trend.usual.low, trend.usual.high),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ScatterTrend(
                    trend = trend,
                    colour = energy,
                    height = 120.dp,
                    modifier = Modifier.padding(top = 10.dp),
                    axes = axes,
                    xTicks = dayTicks(trend.readings),
                    yLabel = { formatOneDecimal(it) },
                )
                Text(
                    weightCheck(series, trend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Daily weight is noisy, so the line is the trend rather than " +
                        "the last reading. It is the only thing here that checks " +
                        "the arithmetic above.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extras.disabled,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Note(
            "Burned is total calories — resting plus movement — written by " +
                "Google Health. Eaten is whatever any app writes to Health Connect, " +
                "including anything you log here.",
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}




/**
 * What the energy balance predicts against what the scales did.
 *
 * This is the only self-check on the tab. A food log everybody forgets half
 * of produces an enormous paper deficit and a weight that does not move, and
 * the two numbers side by side are the only way to see it. The 7,700 kcal a
 * kilogram figure overstates real loss, so a prediction that runs ahead of
 * the scales by a little is ordinary; one that runs ahead by double is a log
 * with meals missing from it.
 */
private fun weightCheck(
    balance: List<com.srineere.airbridge.health.Reading>,
    weight: com.srineere.airbridge.health.Trend,
): String {
    if (balance.size < 7 || weight.readings.size < 7) {
        return "Not enough days with both a weight and a logged day to check one " +
            "against the other yet."
    }
    val daily = balance.map { it.value }.average()
    val days = balance.size
    val predicted = daily * days / Goals.KCAL_PER_KG
    // Thirds, so one odd morning on the scales does not set the direction.
    val third = (weight.readings.size / 3).coerceAtLeast(1)
    val observed = weight.readings.takeLast(third).map { it.value }.average() -
        weight.readings.take(third).map { it.value }.average()

    val predictedWord = if (predicted < 0) "off" else "on"
    val head = "A " + count(abs(daily).roundToInt().toLong()) + " daily " +
        (if (daily < 0) "deficit" else "surplus") + " over $days days predicts about " +
        "%.1f kg".format(abs(predicted)) + " $predictedWord."
    val moved = "%.1f kg".format(abs(observed))
    return when {
        abs(observed) < 0.3 -> "$head The scales have not moved, which usually " +
            "means the food log is missing meals rather than that the arithmetic is wrong."
        (observed < 0) != (predicted < 0) -> "$head The scales went the other way, " +
            "by $moved. Something in the log is not what was eaten."
        abs(observed) < abs(predicted) / 2 -> "$head You moved $moved, less than half " +
            "of that, so the log is probably incomplete."
        else -> "$head You moved $moved, so the log is roughly honest."
    }
}

/**
 * Where today stands against the target, and what a day like it would mean.
 *
 * The weight figure is the 7,700 kcal-per-kilogram rule of thumb, which
 * overstates real loss because the body spends less as it shrinks. It is a
 * check on whether the food log is plausible, not a forecast.
 */
private fun targetLine(balance: Double, target: Int): String {
    if (target == 0) {
        // No weekly figure from here. Today is part of a day, and seven
        // part-days is not a week -- the pace belongs under the balance
        // chart, where the average is over days that actually finished.
        return "No target set, so this is just where today stands. Set one " +
            "in Goals to draw a line on the chart below."
    }
    val short = target - balance
    val direction = if (target < 0) "deficit" else "surplus"
    return "Target is a " + count(abs(target).toLong()) + " $direction. " +
        when {
            abs(short) < 50 -> "You are there."
            (target < 0 && short < 0) || (target > 0 && short > 0) ->
                "You are " + count(abs(short).roundToInt().toLong()) + " past it."
            else -> "You are " + count(abs(short).roundToInt().toLong()) +
                " short with the evening left."
        }
}

/**
 * How complete today's food log is.
 *
 * The balance is only as good as the log behind it, and a day with one
 * entry on it reads as an enormous deficit rather than as a day nobody
 * finished logging. Comparing today's entry count against the days that
 * were logged properly is the only check available from the data itself.
 */
@Composable
private fun Completeness(data: HealthData) {
    val meals = data.snapshot.mealsDaily
    if (meals.isEmpty()) return
    val today = java.time.LocalDate.now().toEpochDay()
    val todayCount = meals[today] ?: 0
    val usual = meals.filterKeys { it != today }.values.filter { it > 0 }
    if (usual.size < 5) return
    val typical = usual.sorted()[usual.size / 2]
    if (todayCount >= typical) return

    DetailCard {
        Text(
            when (todayCount) {
                0 -> "Nothing logged today, so the figure above is everything you " +
                    "burned and none of what you ate."
                else -> "$todayCount " + (if (todayCount == 1) "entry" else "entries") +
                    " logged today; you usually log $typical by the end of a day. " +
                    "Part of the deficit above is probably a meal nobody wrote down."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.hrv,
        )
    }
}

/** 2,400 as "2.4k", because four of these share one row. */
private fun thousands(value: Double): String =
    if (value >= 1000) "%.1fk".format(value / 1000) else value.roundToInt().toString()

@Composable
private fun Bar(label: String, value: Double, ceiling: Double, colour: Color, modifier: Modifier = Modifier) =
    Column(modifier.padding(top = 10.dp)) {
        Row {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                count(value.roundToInt().toLong()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            Modifier
                .padding(top = 5.dp)
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((value / ceiling).toFloat().coerceIn(0f, 1f))
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(colour),
            )
        }
    }

@Composable
private fun Macro(name: String, grams: Double?, colour: Color) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
) {
    Box(Modifier.height(9.dp).fillMaxWidth(0.03f).clip(CircleShape).background(colour))
    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Text(
        grams?.let { "${it.roundToInt()} g" } ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (grams == null) MaterialTheme.extras.disabled
        else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Note(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.extras.disabled,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
)
