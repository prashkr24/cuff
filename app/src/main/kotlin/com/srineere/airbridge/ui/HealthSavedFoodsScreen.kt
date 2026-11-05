package com.srineere.airbridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Meal
import com.srineere.airbridge.health.SavedFood
import com.srineere.airbridge.health.SavedFoods
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.roundToInt

/**
 * The foods you keep.
 *
 * A list of this app's own, because Health Connect has none: its only food
 * record is a thing eaten at a time, so there is nowhere there to put a food
 * you simply want back tomorrow. The screen says as much, since the obvious
 * question on finding them missing from Google Health is whether something
 * went wrong.
 */
@Composable
internal fun HealthSavedFoodsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(SavedFoods.read(context)) }
    var open by remember { mutableStateOf<String?>(null) }
    // Null is All. A list of forty is long enough that "my breakfasts" is a
    // question worth being able to ask, and short enough that it needs a
    // filter rather than a search.
    var filter by remember { mutableStateOf<Meal?>(null) }
    val shown = remember(saved, filter) {
        if (filter == null) saved else saved.filter { it.meal == filter }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Saved foods", onBack = onBack)

        if (saved.isNotEmpty()) {
            MealPills(
                selected = filter,
                onPick = { filter = it; open = null },
                any = "All",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        if (saved.isNotEmpty() && shown.isEmpty()) {
            Text(
                "Nothing saved under ${filter?.label?.lowercase()} yet. A food takes " +
                    "its meal from the entry it was saved from, and you can change " +
                    "it by opening the food.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        if (saved.isEmpty()) {
            Text(
                "Nothing saved yet. Open an entry in the food log, or fill one in " +
                    "while logging, and save it from there — it waits here for next time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        shown.forEach { food ->
            SavedRow(
                food = food,
                open = open == food.key,
                onOpen = { open = if (open == food.key) null else food.key },
                onSave = { edited ->
                    // Saving under a new name leaves the old one behind, so
                    // the rename is made of a remove and a save rather than
                    // quietly keeping both.
                    saved = SavedFoods.save(context, edited)
                        .let { if (edited.key != food.key) SavedFoods.remove(context, food) else it }
                    open = null
                },
                onDelete = {
                    saved = SavedFoods.remove(context, food)
                    open = null
                },
            )
        }

        Text(
            "These live in Air Bridge, not in Health Connect — it records what you " +
                "ate, not a list of foods to pick from. Changing one here changes " +
                "nothing you have already logged.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One saved food, and the fields that change it.
 *
 * The macros are coloured the way they are everywhere else on this tab, so
 * "P 5 C 72 F 1" reads as three quantities rather than one string of
 * letters and numbers.
 */
@Composable
private fun SavedRow(
    food: SavedFood,
    open: Boolean,
    onOpen: (SavedFood) -> Unit,
    onSave: (SavedFood) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable { onOpen(food) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                food.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                food.meal?.let {
                    Text(
                        it.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extras.disabled,
                    )
                }
                food.kcal?.let {
                    Text(
                        "${count(it.roundToInt().toLong())} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                food.protein?.takeIf { it > 0 }?.let {
                    MacroTotal("P", it, MaterialTheme.extras.heart)
                }
                food.carbs?.takeIf { it > 0 }?.let {
                    MacroTotal("C", it, MaterialTheme.extras.steps)
                }
                food.fat?.takeIf { it > 0 }?.let {
                    MacroTotal("F", it, MaterialTheme.extras.energy)
                }
            }
        }
    }
    if (!open) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        // Held locally and written on Save: a food edited field by field
        // would be rewritten four times while you typed one correction, and
        // half-typed figures would be stored along the way.
        var name by remember(food.key) { mutableStateOf(food.name) }
        var kcal by remember(food.key) { mutableStateOf(food.kcal.text()) }
        var protein by remember(food.key) { mutableStateOf(food.protein.text()) }
        var carbs by remember(food.key) { mutableStateOf(food.carbs.text()) }
        var fat by remember(food.key) { mutableStateOf(food.fat.text()) }
        var meal by remember(food.key) { mutableStateOf(food.meal) }

        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("What") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = kcal,
                onValueChange = { kcal = it },
                label = { Text("Calories") },
                suffix = { Text("kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Field("Protein", protein, Modifier.weight(1f)) { protein = it }
                Field("Carbs", carbs, Modifier.weight(1f)) { carbs = it }
                Field("Fat", fat, Modifier.weight(1f)) { fat = it }
            }
            Text(
                "Meal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            // Any, and not a default of breakfast: a food nobody has placed
            // is a real answer, and guessing one would put it under a filter
            // it does not belong to.
            MealPills(
                selected = meal,
                onPick = { meal = it },
                any = "Any",
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(" Delete", color = MaterialTheme.colorScheme.error)
                }
                Box(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onSave(
                            SavedFood(
                                name = name.trim(),
                                kcal = kcal.number(),
                                protein = protein.number(),
                                carbs = carbs.number(),
                                fat = fat.number(),
                                meal = meal,
                            ),
                        )
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )

/** A figure as a person would type it: no trailing zero on a whole number. */
private fun Double?.text(): String = when {
    this == null -> ""
    this == roundToInt().toDouble() -> roundToInt().toString()
    else -> toString()
}

private fun String.number(): Double? = trim().toDoubleOrNull()?.takeIf { it > 0 }
