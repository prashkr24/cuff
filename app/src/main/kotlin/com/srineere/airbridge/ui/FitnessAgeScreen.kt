package com.srineere.airbridge.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.srineere.airbridge.health.FitnessProfileStore
import com.srineere.airbridge.health.Readiness
import com.srineere.airbridge.health.Sex
import com.srineere.airbridge.health.fitnessAgeOf
import com.srineere.airbridge.ui.theme.extras
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * An estimate of fitness, expressed in years.
 *
 * Called fitness age and never biological age. The published models behind
 * it estimate cardiorespiratory fitness and nothing else — they do not read
 * your cells — and a name claiming otherwise is the claim, not the number.
 *
 * It moves in months, not mornings. A good night does not make you younger,
 * and a figure that changed daily would be measuring the wrong thing.
 */
@Composable
internal fun FitnessAgeScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val extras = MaterialTheme.extras
    val ink = LocalTileColour.current ?: extras.activity

    // VO2 max arrives every week or two, so thirty days is two or three
    // readings and often none at all. Same window as readiness, same reason.
    LaunchedEffect(Unit) { state.widen(context, Readiness.WINDOW_DAYS) }
    var profile by remember { mutableStateOf(FitnessProfileStore.read(context)) }
    val fitness = remember(data.snapshot, profile) { fitnessAgeOf(data.snapshot, profile) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Fitness age", subtitle = "moves in months", onBack = onBack)

        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    fitness.years?.toString() ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (fitness.years == null) extras.disabled else ink,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        fitness.age?.let { "You are $it" } ?: "Your age is not set",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (fitness.years != null) "${fitness.word}, from VO₂ max"
                        else "nothing to work it out from yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.disabled,
                    )
                }
            }
            Text(
                "This moves in months, not mornings. A good night does not make you " +
                    "younger, and a figure that changed daily would be measuring the " +
                    "wrong thing.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SectionHeader("What it stands on")
        DetailCard {
            StandRow(
                "VO₂ max",
                fitness.vo2?.let { "%.1f".format(it) },
                "ml/kg",
                fitness.readAt?.let { "read ${dayLabel(it)}" } ?: "nothing has written one",
                counted = true,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Shown because they are the readings anybody would expect here,
            // and marked as not counted because they are not. A fitness age
            // blended out of three things with weights of this app's own
            // invention would be less defensible than one measurement, not
            // more complete.
            StandRow(
                "Resting heart rate",
                data.restingTrend?.latest?.roundToInt()?.toString(),
                "bpm",
                "not in the figure",
                counted = false,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StandRow(
                "Active minutes a week",
                data.snapshot.sessions
                    .sumOf { java.time.Duration.between(it.start, it.end).toMinutes() }
                    .takeIf { it > 0 }?.toString(),
                "",
                "not in the figure",
                counted = false,
            )
            Text(
                "One measurement, expressed in years. The published model is a VO₂ max " +
                    "model; the two rows under it are context, not inputs.",
                style = MaterialTheme.typography.labelMedium,
                color = extras.disabled,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (fitness.vo2 == null) {
            SectionHeader("Nothing has written a VO₂ max")
            DetailCard {
                Text(
                    "Google Health writes one after a run it can estimate from — started " +
                        "in Google Health, with GPS — every week or two rather than daily. " +
                        "Until one arrives there is no primary input here, and the figure " +
                        "is left blank rather than guessed from the rows above.\n\n" +
                        // Said here because this app made it possible to be
                        // in exactly that state: VO2 max was asked for but
                        // never declared in the manifest, so Health Connect
                        // dropped the request and no install has ever had
                        // the grant. An empty chart looks the same either
                        // way, which is the whole reason the permissions
                        // screen counts them.
                        "If you have had runs like that, check VO₂ max is allowed: " +
                        "Device › Permissions. A reading you have not allowed looks " +
                        "exactly like one nothing has written.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionHeader("The table it is read against")
        SettingsRow(
            title = "Year of birth",
            subtitle = "Only used to find your row in the table",
            onClick = {
                val now = LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, _, _ ->
                        profile = profile.copy(birthYear = year)
                        FitnessProfileStore.write(context, profile)
                    },
                    profile.birthYear ?: (now.year - 30),
                    0,
                    1,
                ).show()
            },
            trailing = {
                Text(
                    profile.birthYear?.toString() ?: "Not set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (profile.birthYear == null) extras.disabled
                    else MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        SettingsRow(
            title = "Reference",
            subtitle = "The norms differ by about eight ml/kg, so this changes the figure",
            trailing = {},
        )
        ReferencePills(profile.sex) { picked ->
            profile = profile.copy(sex = picked)
            FitnessProfileStore.write(context, profile)
        }

        Text(
            "An estimate of fitness expressed in years, because years are easier to " +
                "feel than millilitres per kilogram. It is not a measure of health, " +
                "lifespan or ageing, and the band it is read against is a population " +
                "one — the only figure on this tab that is. Both answers above stay " +
                "on this phone.",
            style = MaterialTheme.typography.labelMedium,
            color = extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** The two reference rows, drawn as every other pill row in this app is. */
@Composable
private fun ReferencePills(chosen: Sex?, onPick: (Sex) -> Unit) = Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
) {
    Sex.entries.forEach { option ->
        val on = option == chosen
        Surface(
            color = if (on) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).clickable { onPick(option) },
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

/** One reading under the figure, and whether it is actually in it. */
@Composable
private fun StandRow(
    name: String,
    value: String?,
    unit: String,
    note: String,
    counted: Boolean,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
) {
    Column(Modifier.weight(1f)) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (counted) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            note,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
        )
    }
    Text(
        value?.let { it + if (unit.isEmpty()) "" else " $unit" } ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (value == null) MaterialTheme.extras.disabled
        else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(92.dp),
    )
}
