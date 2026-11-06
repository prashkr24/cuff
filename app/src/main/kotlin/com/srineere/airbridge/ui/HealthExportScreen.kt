package com.srineere.airbridge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.HealthExport
import com.srineere.airbridge.health.HealthReader
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * A file of your own readings, kept where you choose.
 *
 * The range leads, because it decides the size of everything under it and
 * is the one thing anybody actually picks. Everything is the default: this
 * is a backup far more often than it is a query.
 *
 * Each row carries its own count, since "steps" and "steps, 412 days of
 * them" are different promises and only the second can be checked before
 * the button is pressed. The count comes from a real read of the chosen
 * range rather than from what the tab happens to have loaded.
 */
@Composable
internal fun HealthExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = LocalHealthState.current
    val accent = LocalTileColour.current ?: MaterialTheme.colorScheme.primary

    var range by remember { mutableStateOf(HealthExport.Range.All) }
    val chosen = remember { mutableStateListOf(*HealthExport.Part.entries.toTypedArray()) }
    var snapshot by remember { mutableStateOf<HealthSnapshot?>(null) }
    var counting by remember { mutableStateOf(true) }
    var saying by remember { mutableStateOf<String?>(null) }
    var writing by remember { mutableStateOf(false) }
    // How far through the workouts, which is the only part that reads.
    // Null while nothing is being counted, so the button says "Writing"
    // for the fast parts and counts only where there is something to
    // count.
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Re-read whenever the range changes. It is the same read the tab does,
    // over a different number of days, so nothing here is a second way of
    // getting at the data.
    LaunchedEffect(range) {
        counting = true
        saying = null
        snapshot = withContext(Dispatchers.IO) {
            runCatching { HealthReader().read(context, range.days) }.getOrNull()
        }
        counting = false
    }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val reading = snapshot
        if (uri == null || reading == null) return@rememberLauncherForActivityResult
        writing = true
        saying = null
        // On the state's own scope, not this screen's. A zip of a year of
        // workouts takes long enough to leave the screen during, and on the
        // screen's scope the write was cancelled mid-stream: `use` closed a
        // half-written stream and left a truncated .zip at the URI the
        // person chose, with nothing to say it had failed.
        state.write {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        HealthExport.write(context, out, reading, chosen.toSet(), range) { done, total ->
                            progress = if (total > 1) done to total else null
                        }
                    } ?: error("Nothing would open that file")
                }
            }
            writing = false
            progress = null
            saying = result.fold(
                onSuccess = { "Saved ${chosen.size} file${if (chosen.size == 1) "" else "s"}." },
                onFailure = { "Could not write it: ${it.message ?: "unknown error"}" },
            )
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Export", onBack = onBack)

        Text(
            "A file of your own readings, with the rolling averages already worked " +
                "out in the columns — so it answers something the moment it opens, " +
                "without a formula.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        SectionHeader("How far back")
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            HealthExport.Range.entries.forEach { option ->
                RangePill(option.label, option == range) { range = option }
            }
        }
        Text(
            "Health Connect keeps what it keeps. Beyond thirty days it needs the " +
                "history permission, which this app asks for.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        SectionHeader("What goes in", action = null)
        val counts = snapshot?.let { HealthExport.counts(it, context) }.orEmpty()
        DetailCard {
            HealthExport.Part.entries.forEachIndexed { index, part ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PartRow(
                    part = part,
                    count = when {
                        counting -> "counting…"
                        else -> counts[part] ?: "—"
                    },
                    on = part in chosen,
                    onToggle = {
                        if (part in chosen) chosen.remove(part) else chosen.add(part)
                    },
                )
            }
        }

        Button(
            onClick = {
                save.launch("air-bridge-${LocalDate.now()}.zip")
            },
            enabled = chosen.isNotEmpty() && !counting && !writing && snapshot != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                when {
                    !writing -> "  Export"
                    // Named rather than a bare fraction: this count is
                    // workouts and nothing else, and "41 of 96" beside a
                    // word like Writing reads as the whole export.
                    progress != null -> "  Workout ${progress?.first} of ${progress?.second}"
                    else -> "  Writing"
                }
            )
        }
        Text(
            "Saves a zip through Android's own file picker. Nothing is uploaded; " +
                "where it goes is your choice.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )

        saying?.let { why ->
            Text(
                why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.hrv,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        Text(
            "This app is unofficial and rides a protocol an update can change. Your " +
                "readings should outlive it — which is why this is here, and why it " +
                "includes the parts Health Connect does not hold.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** One range, drawn as the meal pills are. Four fit across at equal widths. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RangePill(
    label: String,
    on: Boolean,
    onPick: () -> Unit,
) = Surface(
    color = if (on) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(50),
    modifier = Modifier.weight(1f).clickable(onClick = onPick),
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

/**
 * One kind, its count, and whether it goes in.
 *
 * The whole row toggles, not just the box: a 21dp target beside a 13sp
 * label is a box nobody can hit on a moving train.
 */
@Composable
private fun PartRow(
    part: HealthExport.Part,
    count: String,
    on: Boolean,
    onToggle: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onToggle),
) {
    Checkbox(checked = on, onCheckedChange = { onToggle() })
    Column(Modifier.weight(1f).padding(start = 4.dp)) {
        Text(part.label, style = MaterialTheme.typography.bodyMedium)
        Text(
            part.detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
        )
    }
    Text(
        count,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
