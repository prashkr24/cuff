package com.srineere.airbridge.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.SessionDetail
import com.srineere.airbridge.health.SessionReader
import com.srineere.airbridge.health.Split
import com.srineere.airbridge.health.ASSUMED_MAX_HEART_RATE
import com.srineere.airbridge.health.Zone
import com.srineere.airbridge.health.summarise
import com.srineere.airbridge.health.timeInZones
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One workout, expanded.
 *
 * Everything on this screen is read when it opens and not before: per-second
 * heart rate for every session in a week is thousands of samples that the
 * list behind this never draws.
 *
 * Distance, pace and splits exist only when the session was recorded with
 * them. A session logged by hand shows its duration and nothing it never
 * carried — no estimated pace, no invented route.
 */
@Composable
internal fun HealthSessionScreen(startMillis: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val extras = MaterialTheme.extras
    val axes = rememberAxes()
    var atBeat by remember { mutableStateOf<Float?>(null) }
    // The week the tab holds is the fast path; anything older is read.
    var session by remember(startMillis) {
        mutableStateOf(data.snapshot.sessions.firstOrNull { it.start.toEpochMilli() == startMillis })
    }
    var missing by remember(startMillis) { mutableStateOf(false) }
    var detail by remember(startMillis) { mutableStateOf<SessionDetail?>(null) }
    var loading by remember(startMillis) { mutableStateOf(true) }

    LaunchedEffect(startMillis) {
        loading = true
        val reader = SessionReader()
        val found = session ?: withContext(Dispatchers.IO) {
            reader.session(context, startMillis)
        }?.also { session = it }
        if (found == null) {
            missing = true
            loading = false
            return@LaunchedEffect
        }
        detail = withContext(Dispatchers.IO) { reader.read(context, found.start, found.end) }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val workout = session
        if (workout == null) {
            ScreenTitle("Session", onBack = onBack)
            if (missing) {
                Text(
                    "Health Connect no longer has this workout. It may have been " +
                        "deleted since the list was drawn.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            return@Column
        }

        val clock = remember {
            DateTimeFormatter
                .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM Hm"))
                .withZone(ZoneId.systemDefault())
        }
        val length = Duration.between(workout.start, workout.end)
        ScreenTitle(
            title = workout.exerciseName(),
            subtitle = clock.format(workout.start),
            onBack = onBack,
        )

        val found = detail
        DetailCard {
            // Two rows of three, so each figure keeps a readable size. A
            // figure the session never carried is a dash, not a zero.
            Row {
                Stat(
                    "Distance",
                    found?.distanceKm?.let { "${formatOneDecimal(it)} km" } ?: "—",
                    Modifier.weight(1f),
                )
                Stat("Pace", pace(found?.distanceKm, length), Modifier.weight(1.2f))
                Stat("Time", clockOf(length), Modifier.weight(1f))
            }
            val beats = summarise(found?.samples.orEmpty())
            Row(Modifier.padding(top = 14.dp)) {
                Tinted(
                    "Burned",
                    found?.kcal?.let { "${it.toInt()} kcal" },
                    extras.energy,
                    Modifier.weight(1f),
                )
                Tinted("Avg bpm", beats?.average?.toString(), extras.heart, Modifier.weight(1f))
                Tinted("Peak bpm", beats?.highest?.toString(), extras.zonePeak, Modifier.weight(1f))
            }

            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.width(16.dp).height(16.dp),
                    )
                    Text(
                        "  Reading this session",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (found != null && found.samples.size >= 2) {
                // The session's own heart rate over the same zone bands the
                // Heart screen uses, so a hard run looks hard in both places.
                DayHeartLine(
                    samples = found.samples,
                    colour = extras.heart,
                    modifier = Modifier.padding(top = 14.dp)
                        .scrubbableAcross(CHART_GUTTER) { atBeat = it },
                    height = 150.dp,
                    axes = axes,
                    xTicks = elapsedTicks(found.samples),
                    selected = atBeat,
                    zones = Zone.entries.map { zone ->
                        ZoneBand(
                            from = zone.lowBpm(ASSUMED_MAX_HEART_RATE).toDouble(),
                            until = zone.highBpm(ASSUMED_MAX_HEART_RATE).toDouble(),
                            colour = zone.tint(extras),
                            label = zone.short,
                        )
                    },
                    markPeak = true,
                    peakColour = extras.zonePeak,
                )
                BeatReadout(found.samples, atBeat)
            }
        }

        if (found != null && found.samples.size >= 2) {
            val inZone = timeInZones(found.samples)
            if (inZone.isNotEmpty()) {
                SectionHeader("Time in zone")
                DetailCard {
                    Row(
                        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Zone.entries.forEach { zone ->
                            val share = inZone[zone]?.seconds ?: 0L
                            if (share > 0) {
                                Box(
                                    Modifier.weight(share.toFloat()).fillMaxHeight()
                                        .background(zone.tint(extras)),
                                )
                            }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp)) {
                        Zone.entries.forEach { zone ->
                            val spent = inZone[zone]
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.width(8.dp).height(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(zone.tint(extras)),
                                    )
                                    Text(
                                        "  ${zone.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    spent?.let { shortMinutes(it) } ?: "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (found != null && found.splits.size >= 2) {
            Splits(found.splits)
        }

        Text(
            "Distance, pace and splits exist only when the session was recorded " +
                "with them. A session logged by hand shows its duration and nothing " +
                "it never carried.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}


/**
 * A figure in its own metric's colour.
 *
 * Six numbers in two rows all in the same grey is a table; tinting the three
 * that belong to heart and energy is how you find the one you came for
 * without reading the labels.
 */
@Composable
private fun Tinted(label: String, value: String?, colour: Color, modifier: Modifier) =
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (value == null) MaterialTheme.extras.disabled else colour,
            maxLines = 1,
        )
    }

/** The zone's name, short enough for a chart gutter. */
private val Zone.short: String
    get() = when (this) {
        Zone.Light -> "Light"
        Zone.Moderate -> "Mod"
        Zone.Vigorous -> "Vig"
        Zone.Peak -> "Peak"
    }

/** Minutes from the start of a session, as x labels. */
private fun elapsedTicks(samples: List<com.srineere.airbridge.health.HeartSample>):
    List<Pair<Float, String>> {
    if (samples.size < 2) return emptyList()
    val first = samples.first().atMillis
    val across = (samples.last().atMillis - first).takeIf { it > 0 } ?: return emptyList()
    val minutes = across / 60_000.0
    val every = when {
        minutes > 80 -> 20
        minutes > 40 -> 10
        else -> 5
    }
    return generateSequence(every) { it + every }
        .takeWhile { it <= minutes }
        .map { (it * 60_000 / across.toDouble()).toFloat() to "${it}m" }
        .toList()
}

/**
 * Splits as bars, the shortest bar the fastest kilometre.
 *
 * A pace table is numbers you have to compare yourself; a bar chart does the
 * comparing. The bars are scaled from the fastest rather than from zero,
 * because every kilometre of one run is roughly the same length and a
 * zero-based chart would make them all look identical.
 */
@Composable
private fun Splits(splits: List<Split>) {
    val fastest = splits.filter { it.distanceKm >= 0.95 }.minOfOrNull { it.paceSeconds }
        ?: splits.minOf { it.paceSeconds }
    val slowest = splits.maxOf { it.paceSeconds }
    val colour = MaterialTheme.extras.activity

    SectionHeader("Splits")
    DetailCard {
        splits.forEach { split ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    split.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                val share = if (slowest <= fastest) 1f else {
                    // From a third of the bar at the fastest to the full
                    // width at the slowest, so the spread is visible without
                    // the quickest kilometre vanishing.
                    (0.34 + 0.66 * (split.paceSeconds - fastest) / (slowest - fastest)).toFloat()
                }
                // The last part-kilometre is not a kilometre. Its pace is a
                // short distance multiplied up, so it is drawn faint and
                // left out of the fastest: a 100 m tail at a walking pace
                // would otherwise be the longest bar on the chart.
                val partial = split.distanceKm < 0.95
                Box(
                    Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        Modifier.fillMaxWidth(share.coerceIn(0.05f, 1f)).height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    partial -> colour.copy(alpha = 0.22f)
                                    split.paceSeconds == fastest -> colour
                                    else -> colour.copy(alpha = 0.55f)
                                },
                            ),
                    )
                }
                Text(
                    paceOf(split.paceSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (partial) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (partial) MaterialTheme.extras.disabled
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(56.dp).padding(start = 10.dp),
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Text(
            "Fastest kilometre ${paceOf(fastest)} /km.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Zone.tint(extras: com.srineere.airbridge.ui.theme.AirExtraColors) = when (this) {
    Zone.Light -> extras.zoneLight
    Zone.Moderate -> extras.zoneModerate
    Zone.Vigorous -> extras.zoneVigorous
    Zone.Peak -> extras.zonePeak
}

private fun pace(distanceKm: Double?, length: Duration): String {
    if (distanceKm == null || distanceKm <= 0.0) return "—"
    return paceOf(length.seconds / distanceKm) + " /km"
}

private fun paceOf(secondsPerKm: Double): String {
    val whole = secondsPerKm.toLong()
    return "${whole / 60}:${"%02d".format(whole % 60)}"
}

private fun clockOf(length: Duration): String {
    val minutes = length.toMinutes()
    return if (minutes >= 60) {
        "${minutes / 60}:${"%02d".format(minutes % 60)}:${"%02d".format(length.seconds % 60)}"
    } else {
        "$minutes:${"%02d".format(length.seconds % 60)}"
    }
}

private fun shortMinutes(length: Duration): String {
    val minutes = length.toMinutes()
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes >= 1 -> "${minutes}m"
        else -> "<1m"
    }
}
