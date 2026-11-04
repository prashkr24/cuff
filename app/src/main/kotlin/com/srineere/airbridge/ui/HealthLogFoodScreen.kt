package com.srineere.airbridge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.srineere.airbridge.health.FoodEntry
import com.srineere.airbridge.health.HealthConnect
import com.srineere.airbridge.health.HealthWriter
import com.srineere.airbridge.health.LogResult
import com.srineere.airbridge.health.Meal
import com.srineere.airbridge.health.SavedFoods
import com.srineere.airbridge.health.SavedFood
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Quick add, not a food database.
 *
 * Calories are the field that matters for the balance; the macros are
 * optional and the screen says so. Nothing here looks anything up, because a
 * food database that is wrong about a portion is worse than a box you typed
 * the number into yourself.
 */
@Composable
internal fun HealthLogFoodScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(Meal.at(LocalTime.now().hour)) }
    var saying by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Two short lists rather than one long one: the last few are for
    // repeating what you already ate, saved foods are the ones worth
    // keeping. Both fill the form rather than saving anything themselves.
    var saved by remember { mutableStateOf(SavedFoods.read(context)) }
    // One question at a time. Everything used to be on screen at once --
    // meal pills, two lists and a five-field form -- and the screen could
    // not say which part you were meant to start with. Now it asks that
    // first, and whichever answer you give leads to the same form.
    var step by remember { mutableStateOf(Step.Start) }

    val ask = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { state.refresh(context, force = true) }

    fun number(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it > 0 }

    val fromMacros = HealthWriter.macroEnergy(number(protein), number(carbs), number(fat))
    val typed = number(kcal)
    val anything = typed != null || fromMacros != null

    fun fill(food: SavedFood) {
        name = food.name
        kcal = food.kcal?.let { figure(it) } ?: ""
        protein = food.protein?.let { figure(it) } ?: ""
        carbs = food.carbs?.let { figure(it) } ?: ""
        fat = food.fat?.let { figure(it) } ?: ""
        saying = null
        step = Step.Type
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            when (step) {
                Step.Start -> "Log food"
                Step.Recent -> "Recent"
                Step.Saved -> "Saved foods"
                Step.Type -> "Log food"
            },
            // Back steps through the flow rather than out of it, so a wrong
            // turn costs one tap instead of starting the whole thing again.
            onBack = { if (step == Step.Start) onBack() else step = Step.Start },
        )

        if (step == Step.Start) {
            SectionHeader("Start from")
            StartRow(
                title = "Something recent",
                subtitle = "Pick from what you have logged lately",
                icon = Icons.Outlined.History,
            ) { step = Step.Recent }
            StartRow(
                title = if (saved.isEmpty()) "Saved foods" else "Saved foods · ${saved.size}",
                subtitle = if (saved.isEmpty()) "Nothing saved yet"
                else "Pick from the ones you keep",
                icon = Icons.Outlined.StarOutline,
            ) { step = Step.Saved }
            StartRow(
                title = "Type it",
                subtitle = "A new entry, from scratch",
                icon = Icons.Outlined.EditNote,
            ) { step = Step.Type }
            Box(Modifier.padding(bottom = 24.dp))
            return@Column
        }

        if (step == Step.Recent || step == Step.Saved) {
            val onSaved = step == Step.Saved
            PickList(
                foods = if (onSaved) saved
                else data.snapshot.food.sortedByDescending { it.at }
                    .distinctBy { (it.name ?: "").trim().lowercase() }
                    .take(8)
                    .map { it.asSavedFood() },
                forgettable = onSaved,
                empty = if (onSaved) "Nothing saved yet. Save a food from an entry and it waits here."
                else "Nothing logged yet, so there is nothing to repeat.",
                onPick = ::fill,
                onForget = { food -> saved = SavedFoods.remove(context, food) },
            )
            Box(Modifier.padding(bottom = 24.dp))
            return@Column
        }

        SectionHeader("Which meal")
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Meal.entries.forEach { option ->
                val on = option == meal
                Surface(
                    color = if (on) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).clickable { meal = option },
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }

        SectionHeader("The entry")
        DetailCard {
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
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                GramField("Protein", protein, Modifier.weight(1f)) { protein = it }
                GramField("Carbs", carbs, Modifier.weight(1f)) { carbs = it }
                GramField("Fat", fat, Modifier.weight(1f)) { fat = it }
            }

            // The macros imply a calorie figure. When the two disagree the
            // screen says so rather than silently keeping one of them.
            if (fromMacros != null && typed != null) {
                Text(
                    if (HealthWriter.agrees(typed, fromMacros)) {
                        "Macros come to ${fromMacros.roundToInt()} kcal — close enough " +
                            "to ${typed.roundToInt()} to keep both."
                    } else {
                        "Macros come to ${fromMacros.roundToInt()} kcal, " +
                            "${abs(typed - fromMacros).roundToInt()} from the " +
                            "${typed.roundToInt()} you typed. Both are saved as typed."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (HealthWriter.agrees(typed, fromMacros)) {
                        MaterialTheme.extras.disabled
                    } else {
                        MaterialTheme.extras.hrv
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        // The running total, so you can see what this entry does before it
        // is saved rather than after.
        // Saved from the entry that created it, which is the only moment
        // anyone knows a food is worth keeping. Only once there is a name to
        // save it under.
        if (name.isNotBlank()) {
            val already = saved.any { it.key == name.trim().lowercase() }
            TextButton(
                onClick = {
                    val food = SavedFood(name.trim(), typed, number(protein), number(carbs), number(fat))
                    saved = if (already) SavedFoods.remove(context, food)
                    else SavedFoods.save(context, food)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(
                    if (already) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(if (already) " Saved to your foods" else " Save this food")
            }
        }

        AfterThis(data, typed ?: fromMacros)

        Button(
            onClick = {
                saving = true
                saying = null
                scope.launch {
                    val result = HealthWriter.logFood(
                        context = context,
                        name = name,
                        kcal = typed,
                        protein = number(protein),
                        carbs = number(carbs),
                        fat = number(fat),
                        meal = meal,
                    )
                    saving = false
                    when (result) {
                        LogResult.Saved -> {
                            state.refresh(context, force = true)
                            onBack()
                        }
                        is LogResult.Refused -> saying = result.why
                    }
                }
            },
            enabled = anything && !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        ) { Text(if (saving) "Saving" else "Save to Health Connect") }

        saying?.let { why ->
            Text(
                why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.heart,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            SettingsRow(
                title = "Allow writing food",
                subtitle = "Opens Health Connect's own permission sheet",
                onClick = { ask.launch(HealthConnect.writePermissions) },
            )
        }

        Text(
            "The entry goes to Health Connect, where Google Health and anything " +
                "else you use can read it. Saved foods stay in Air Bridge — Health " +
                "Connect records what you ate, not a list of foods to pick from.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun GramField(
    label: String,
    value: String,
    modifier: Modifier,
    onChange: (String) -> Unit,
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
)

/**
 * One list, whichever the person asked for.
 *
 * Picking fills the form and moves on to it, so a repeat is two taps and a
 * Save. Nothing here saves anything by itself: the figures still pass
 * through the form, where they can be corrected before they are written.
 */
@Composable
private fun PickList(
    foods: List<SavedFood>,
    forgettable: Boolean,
    empty: String,
    onPick: (SavedFood) -> Unit,
    onForget: (SavedFood) -> Unit,
) {
    if (foods.isEmpty()) {
        Text(
            empty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        return
    }
    foods.forEach { food ->
        SettingsRow(
            title = food.name,
            subtitle = listOfNotNull(
                food.kcal?.let { "${count(it.roundToInt().toLong())} kcal" },
                listOfNotNull(
                    food.protein?.takeIf { it > 0 }?.let { "P ${it.roundToInt()}" },
                    food.carbs?.takeIf { it > 0 }?.let { "C ${it.roundToInt()}" },
                    food.fat?.takeIf { it > 0 }?.let { "F ${it.roundToInt()}" },
                ).joinToString(" ").takeIf { it.isNotEmpty() },
            ).joinToString(" · ").ifEmpty { "No figures" },
            icon = Icons.Outlined.Add,
            onClick = { onPick(food) },
            trailing = {
                // Only a saved food can be forgotten. A recent entry is the
                // record of a thing eaten; it goes from the log, not here.
                if (forgettable) {
                    IconButton(onClick = { onForget(food) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Forget ${food.name}",
                            tint = MaterialTheme.extras.disabled,
                        )
                    }
                }
            },
        )
    }
}

/** One of the three ways in, on the first step. */
@Composable
private fun StartRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) = SettingsRow(title = title, subtitle = subtitle, icon = icon, onClick = onClick)

/** Which part of logging you are in. */
private enum class Step { Start, Recent, Saved, Type }

@Composable
private fun Tab(label: String, on: Boolean, onClick: () -> Unit) = Surface(
    color = if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
    shape = RoundedCornerShape(50),
    modifier = Modifier.clickable(onClick = onClick),
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        color = if (on) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** A figure as a person typed it: no trailing zero on a whole number. */
private fun figure(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString()
    else value.toString()

@Composable
private fun AfterThis(data: HealthData, adding: Double?) {
    val eaten = (data.snapshot.eatenToday ?: 0.0) + (adding ?: 0.0)
    val burned = data.snapshot.burnedToday ?: return
    val balance = eaten - burned
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Today after this",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${count(eaten.roundToInt().toLong())} eaten · " +
                        "${count(burned.roundToInt().toLong())} burned",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                (if (balance < 0) "−" else "+") + count(abs(balance).roundToInt().toLong()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.extras.energy,
            )
        }
    }
}

