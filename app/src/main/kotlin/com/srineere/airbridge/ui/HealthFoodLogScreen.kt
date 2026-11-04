package com.srineere.airbridge.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.FoodEntry
import com.srineere.airbridge.health.HealthWriter
import com.srineere.airbridge.health.LogResult
import com.srineere.airbridge.health.Meal
import com.srineere.airbridge.health.SavedFood
import com.srineere.airbridge.health.SavedFoods
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date
import kotlin.math.roundToInt

/**
 * What you have eaten, as Health Connect holds it.
 *
 * The Log button opens here rather than straight into a form. What anyone
 * wants first is to see what is already down for today — to check a figure,
 * to notice a double entry, to remember whether lunch went in — and adding
 * is one tap from that rather than instead of it.
 *
 * Every entry shows, whichever app wrote it, and every entry can be removed.
 * A list you can read but not correct is worse than no list.
 */
@Composable
internal fun HealthFoodLogScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val state = LocalHealthState.current
    val data by healthData(context)
    val scope = rememberCoroutineScope()
    val accent = LocalTileColour.current ?: MaterialTheme.extras.energy

    // Days back from today. The window the tab holds is the limit, because
    // beyond it there is nothing read to show.
    var back by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    // Which meals are folded away. Empty is the useful default: you came
    // here to read the day, not to unfold it.
    var folded by remember { mutableStateOf(setOf<Int>()) }
    var saying by remember { mutableStateOf<String?>(null) }

    val day = remember(back) { LocalDate.now().minusDays(back.toLong()).toEpochDay() }
    val entries = remember(data.snapshot.food, day) {
        data.snapshot.food.filter { it.day == day }.sortedBy { it.at }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Food log",
            onBack = onBack,
            action = {
                Button(
                    onClick = { onOpen("healthlogfood") },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Log")
                }
            },
        )

        DayBar(back, oldest = state.days - 1) { back = it }

        DayTotals(entries, accent)

        if (entries.isEmpty()) {
            Text(
                if (back == 0) "Nothing logged today yet."
                else "Nothing was logged that day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }

        // Grouped by meal, in the order a day is eaten rather than the order
        // the records happen to come back in.
        Meal.entries.forEach { meal ->
            val forMeal = entries.filter { it.mealType == meal.type }
            if (forMeal.isEmpty()) return@forEach
            val shut = meal.type in folded
            MealHeader(
                label = meal.label,
                kcal = forMeal.sumOf { it.kcal ?: 0.0 },
                count = forMeal.size,
                shut = shut,
                onToggle = {
                    folded = if (shut) folded - meal.type else folded + meal.type
                    open = null
                },
            )
            if (shut) return@forEach
            forMeal.forEach { entry ->
                EntryRow(
                    entry = entry,
                    open = open == entry.id,
                    accent = accent,
                    onOpen = { open = if (open == entry.id) null else entry.id },
                    onSave = {
                        SavedFoods.save(context, entry.asSavedFood())
                        saying = "${entry.name ?: "Entry"} saved to your foods."
                        open = null
                    },
                    onDelete = {
                        open = null
                        saying = null
                        scope.launch {
                            when (val result = HealthWriter.deleteFood(context, entry.id)) {
                                LogResult.Saved -> state.refresh(context, force = true)
                                is LogResult.Refused -> saying = result.why
                            }
                        }
                    },
                )
            }
        }

        // Anything the entries above could not do, said once at the bottom
        // rather than next to the row that failed and then scrolled away.
        saying?.let { why ->
            Text(
                why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.hrv,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        SettingsRow(
            title = "Saved foods",
            subtitle = "The ones you keep, to log again without typing",
            icon = Icons.Outlined.StarOutline,
            onClick = { onOpen("healthsavedfoods") },
        )

        Text(
            "Everything Health Connect holds for this day, whichever app wrote it. " +
                "Deleting removes it there too.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** Which day is being shown, and the two arrows that change it. */
@Composable
private fun DayBar(back: Int, oldest: Int, onPick: (Int) -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
) {
    IconButton(onClick = { onPick(back + 1) }, enabled = back < oldest) {
        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Earlier day")
    }
    Text(
        dayName(back),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.weight(1f),
    )
    // Nothing to see after today, so the arrow says so rather than wrapping
    // round to a month ago.
    IconButton(onClick = { onPick(back - 1) }, enabled = back > 0) {
        Icon(Icons.Outlined.ChevronRight, contentDescription = "Later day")
    }
}

@Composable
private fun DayTotals(entries: List<FoodEntry>, accent: Color) = DetailCard {
    val kcal = entries.sumOf { it.kcal ?: 0.0 }
    Row(verticalAlignment = Alignment.Bottom) {
        BigNumber(count(kcal.roundToInt().toLong()))
        Text(
            " kcal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        Box(Modifier.weight(1f))
        Text(
            when (entries.size) {
                0 -> "no entries"
                1 -> "1 entry"
                else -> "${entries.size} entries"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(bottom = 5.dp),
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        MacroTotal("P", entries.sumOf { it.protein ?: 0.0 }, MaterialTheme.extras.heart)
        MacroTotal("C", entries.sumOf { it.carbs ?: 0.0 }, MaterialTheme.extras.steps)
        MacroTotal("F", entries.sumOf { it.fat ?: 0.0 }, accent)
    }
}

@Composable
internal fun MacroTotal(letter: String, grams: Double, colour: Color) = Row {
    Text(
        letter,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = colour,
    )
    Text(
        " ${grams.roundToInt()} g",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One meal's heading, which is also the control that folds it.
 *
 * On its own surface rather than floating over the list: as plain text it
 * sat in the same column, at nearly the same size, as the entry names under
 * it, and the eye could not tell a heading from a thing eaten.
 */
@Composable
private fun MealHeader(
    label: String,
    kcal: Double,
    count: Int,
    shut: Boolean,
    onToggle: () -> Unit,
) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth()
        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp)
        .clickable(onClick = onToggle),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .padding(start = 14.dp, end = 8.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (count == 1) "  1 entry" else "  $count entries",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${count(kcal.roundToInt().toLong())} kcal",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (shut) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = if (shut) "Show $label" else "Hide $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp).size(22.dp),
        )
    }
}

/**
 * One entry, and what can be done to it.
 *
 * Tapping opens it, the way tapping a tile opens its options. A delete
 * button on every row is a column of them to catch with a thumb while
 * scrolling, and the one control that throws work away should not be the
 * easiest to hit by accident.
 */
@Composable
private fun EntryRow(
    entry: FoodEntry,
    open: Boolean,
    accent: Color,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val clock = remember(context) { DateFormat.getTimeFormat(context) }
    SettingsRow(
        title = entry.name?.takeIf { it.isNotBlank() } ?: "Untitled",
        subtitle = listOfNotNull(
            clock.format(Date(entry.at.toEpochMilli())),
            entry.kcal?.let { "${count(it.roundToInt().toLong())} kcal" },
        ).joinToString(" · "),
        onClick = onOpen,
        trailing = {},
    )
    if (!open) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroTotal("P", entry.protein ?: 0.0, MaterialTheme.extras.heart)
                MacroTotal("C", entry.carbs ?: 0.0, MaterialTheme.extras.steps)
                MacroTotal("F", entry.fat ?: 0.0, accent)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                // Keeping it and removing it are the two things anyone wants
                // from an entry they have stopped to look at.
                TextButton(onClick = onSave) {
                    Icon(
                        Icons.Outlined.StarOutline,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(" Save food")
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(" Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

internal fun FoodEntry.asSavedFood() = SavedFood(
    name = name?.takeIf { it.isNotBlank() } ?: "Untitled",
    kcal = kcal,
    protein = protein,
    carbs = carbs,
    fat = fat,
)

/** Today and yesterday by name, anything older by date. */
@Composable
private fun dayName(back: Int): String = when (back) {
    0 -> "Today"
    1 -> "Yesterday"
    else -> {
        val date = LocalDate.now().minusDays(back.toLong())
        remember(back) {
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM").format(date)
        }
    }
}
