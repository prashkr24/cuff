package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.roundToInt

/**
 * What you have eaten, and what it was made of.
 *
 * Calories are one number and the split is another: a 2,000 kcal day of
 * mostly carbohydrate and a 2,000 kcal day of mostly protein are not the
 * same day, and only the second chart can tell you which you had.
 */
@Composable
internal fun HealthNutritionScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    LaunchedEffect(Unit) { state.loadGoals(context) }
    val goals = state.goals
    val axes = rememberAxes()
    val snapshot = data.snapshot
    val extras = MaterialTheme.extras
    val protein = extras.heart
    val carbs = extras.steps
    val fat = extras.energy

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // The button says Log and now logs. It used to open the food log,
        // which is a list of what is already down -- so the one control on
        // the screen that looked like "add something" was the one that did
        // not, and reading the day back is a row of its own below.
        //
        // Drawn exactly as the food log draws its own: same icon, same
        // label, same accent, because it is the same button for the same job
        // one screen along.
        ScreenTitle(
            "Nutrition",
            onBack = onBack,
            action = {
                Button(
                    onClick = { onOpen("healthlogfood") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalTileColour.current ?: MaterialTheme.extras.energy,
                    ),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Log")
                }
            },
        )

        val eaten = snapshot.eatenToday
        DetailCard {
            Column {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            eaten?.let { count(it.roundToInt().toLong()) } ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (eaten == null) MaterialTheme.extras.disabled
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            " kcal eaten",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        mealsLine(snapshot.mealsDaily[todayEpochDay()]),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // A macro written as zero is the same as one not written at
            // all here: it contributes no share, and keeping it would put a
            // zero-width segment and a divide-by-nothing in the bar.
            val macros = listOfNotNull(
                snapshot.proteinToday?.let { Triple("Protein", it, protein) },
                snapshot.carbsToday?.let { Triple("Carbs", it, carbs) },
                snapshot.fatToday?.let { Triple("Fat", it, fat) },
            ).filter { it.second > 0 }
            if (macros.isEmpty()) {
                Text(
                    "Nothing logged today has macros on it. An entry without them " +
                        "still counts its calories; the split simply has less behind it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                // Grams are what you log; calories are what the split means,
                // so the bar is weighted by energy and the figure is grams.
                val energy = macros.sumOf { it.second * kcalPerGram(it.first) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp)
                        .height(10.dp).clip(RoundedCornerShape(50)),
                ) {
                    macros.forEach { (name, grams, colour) ->
                        val share = (grams * kcalPerGram(name) / energy).toFloat()
                        if (share > 0f) Box(Modifier.weight(share).fillMaxSize().background(colour))
                    }
                }
                macros.forEach { (name, grams, colour) ->
                    MacroRow(
                        name, grams, grams * kcalPerGram(name) / energy, colour,
                        // Protein is the only macro anybody sets a target
                        // for, and the only one with a goal to mark.
                        met = name == "Protein" &&
                            goals.proteinGrams?.let { grams >= it } == true,
                    )
                }
            }
        }

        SplitSection(snapshot, protein, carbs, fat, axes)
        ProteinSection(snapshot.proteinDaily, protein, goals.proteinGrams, axes)

        // Above the balance rather than in the title: reading the day back
        // is a different job from adding to it, and a row can say which day
        // and how many entries where a button in a title bar cannot.
        SettingsRow(
            title = "Food log",
            subtitle = "Every entry for the day, to check or correct",
            onClick = { onOpen("healthfoodlog") },
        )

        SettingsRow(
            title = "Burned against eaten",
            subtitle = "Both running totals, across today",
            onClick = { onOpen("healthbalance") },
        )

        Text(
            "Macros come from whatever app writes them — Google Health's own food " +
                "log counts, as does anything else that writes to Health Connect. " +
                "An entry logged without macros still counts its calories.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun MacroRow(
    name: String,
    grams: Double,
    share: Double,
    colour: Color,
    met: Boolean = false,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(colour))
    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(62.dp))
    Box(
        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier.fillMaxWidth(share.toFloat().coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(50)).background(colour),
        )
    }
    Text(
        "${grams.roundToInt()} g",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        // The mark takes its own room rather than squeezing the figure,
        // which is the same width on every row whether marked or not.
        modifier = Modifier.width(if (met) 38.dp else 52.dp),
    )
    if (met) GoalMark(colour, size = 14.dp)
}

/**
 * Fourteen days of split, each bar its own full height.
 *
 * Normalised on purpose: the question here is what the day was made of, not
 * how big it was. How big it was is the Energy screen's question, and
 * answering both in one chart answers neither.
 */
@Composable
private fun SplitSection(
    snapshot: com.srineere.airbridge.health.HealthSnapshot,
    protein: Color,
    carbs: Color,
    fat: Color,
    axes: Axes,
) {
    val days = (snapshot.proteinDaily.map { it.day } + snapshot.carbsDaily.map { it.day } +
        snapshot.fatDaily.map { it.day }).distinct().sorted().takeLast(14)
    if (days.size < 3) return
    val byDay = { series: List<Reading> -> series.associate { it.day to it.value } }
    val p = byDay(snapshot.proteinDaily)
    val c = byDay(snapshot.carbsDaily)
    val f = byDay(snapshot.fatDaily)
    val logged = days.count { (p[it] ?: 0.0) + (c[it] ?: 0.0) + (f[it] ?: 0.0) > 0 }
    var picked by remember { mutableStateOf<Int?>(null) }

    SectionHeader("What you ate")
    DetailCard {
        // Each bar is as tall as that day's calories, and the stripes inside
        // it are where they came from. Drawn as equal-height bars the chart
        // said every day was the same size, which was the one thing it could
        // not have meant.
        StackedBars(
            bars = days.map { day ->
                listOf(
                    (p[day] ?: 0.0) * 4 to protein,
                    (c[day] ?: 0.0) * 4 to carbs,
                    (f[day] ?: 0.0) * 9 to fat,
                )
            },
            height = 150.dp,
            modifier = Modifier.scrubbable(days.size, CHART_GUTTER) { picked = it },
            axes = axes,
            xTicks = listOf(0f to dayLabel(days.first()), 1f to "Today"),
            selected = picked,
        )
        val onDay = picked?.let { days.getOrNull(it) }
        ChartReadout(
            day = onDay,
            value = onDay?.let { day ->
                val kcal = (p[day] ?: 0.0) * 4 + (c[day] ?: 0.0) * 4 + (f[day] ?: 0.0) * 9
                if (kcal <= 0) "nothing logged" else count(kcal.roundToInt().toLong()) + " kcal"
            },
            detail = onDay?.let { day ->
                listOfNotNull(
                    p[day]?.takeIf { it > 0 }?.let { "P ${it.roundToInt()}" },
                    c[day]?.takeIf { it > 0 }?.let { "C ${it.roundToInt()}" },
                    f[day]?.takeIf { it > 0 }?.let { "F ${it.roundToInt()}" },
                ).joinToString(" ").takeIf { it.isNotEmpty() }
            },
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf("Protein" to protein, "Carbs" to carbs, "Fat" to fat).forEach { (name, tint) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                    Text(
                        "  $name",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (days.size - logged > 0) {
            Text(
                when (days.size - logged) {
                    1 -> "One day in this fortnight has nothing logged."
                    else -> "${days.size - logged} days in this fortnight have nothing logged."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Protein in grams, because it is the macro people actually set a target
 * for — and a share of calories hides whether the grams went up at all.
 */
@Composable
private fun ProteinSection(
    series: List<Reading>,
    colour: Color,
    goal: Int?,
    axes: Axes,
) {
    val recent = series.takeLast(14).filter { it.value > 0 }
    if (recent.size < 3) return
    val average = recent.map { it.value }.average()
    val days = series.takeLast(14)
    var pickedProtein by remember { mutableStateOf<Int?>(null) }
    SectionHeader("Protein")
    DetailCard {
        GoalBars(
            values = days.map { it.value },
            // Your own goal when you have set one, your own average when you
            // have not: either way the line is yours, not a recommendation.
            target = goal?.toDouble() ?: average,
            colour = colour,
            dim = colour.copy(alpha = 0.35f),
            height = 140.dp,
            modifier = Modifier.scrubbable(days.size, CHART_GUTTER) { pickedProtein = it },
            axes = axes,
            xTicks = listOf(0f to dayLabel(days.first().day), 1f to "Today"),
            goalLabel = "${(goal ?: average.roundToInt()).toInt()}g",
            yLabel = { "${it.roundToInt()}g" },
            selected = pickedProtein,
        )
        val at = pickedProtein?.let { days.getOrNull(it) }
        ChartReadout(
            day = at?.day,
            value = at?.let { "${it.value.roundToInt()} g protein" },
            detail = at?.let {
                val against = goal?.toDouble() ?: average
                if (it.value >= against) "at or over" else "under"
            },
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Row {
            Stat("Average", "${average.roundToInt()} g", Modifier.weight(1f))
            Stat("Best", "${recent.maxOf { it.value }.roundToInt()} g", Modifier.weight(1f))
            Stat(
                if (goal == null) "Days logged" else "Goal met",
                if (goal == null) "${recent.size} of 14"
                else "${recent.count { it.value >= goal }} of ${recent.size}",
                Modifier.weight(1.2f),
            )
        }
    }
}

/** The Atwater factors, which is how a gram becomes a calorie everywhere. */
private fun kcalPerGram(macro: String): Double = if (macro == "Fat") 9.0 else 4.0

private fun mealsLine(entries: Int?): String = when (entries) {
    null, 0 -> "nothing logged today"
    1 -> "one entry today"
    else -> "$entries entries today"
}

private fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()
