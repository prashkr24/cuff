package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.HealthConnect
import androidx.health.connect.client.PermissionController
import com.srineere.airbridge.health.Zone
import com.srineere.airbridge.health.ASSUMED_MAX_HEART_RATE
import com.srineere.airbridge.health.SessionSummary
import com.srineere.airbridge.health.SessionReader
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The last seven days of sessions.
 *
 * Health Connect holds an exercise type, a start, an end and sometimes a
 * title; distance and heart rate live in their own records and are only
 * joined up when they exist. So a session shows what it actually carried --
 * a walk logged by hand shows its length and says nothing it never had.
 */
@Composable
internal fun HealthActivityScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    LaunchedEffect(Unit) { state.loadGoals(context) }
    val goals = state.goals
    val activity = LocalTileColour.current ?: MaterialTheme.extras.activity
    val axes = rememberAxes()
    val sessions = data.snapshot.sessions

    // Each row carries its own four numbers, which means a read per session.
    // They are fetched once, off the main thread, and the rows draw dashes
    // until each one lands rather than holding the whole list back.
    var pickedDay by remember { mutableStateOf<Int?>(null) }
    val details = remember { mutableStateMapOf<Long, SessionSummary>() }

    // Health Connect owns the permission sheet, and coming back from it is
    // the one moment this screen must re-read rather than trust its cache.
    val scope = rememberCoroutineScope()
    val ask = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        details.clear()
        state.refresh(context, force = true)
    }
    LaunchedEffect(sessions) {
        val reader = SessionReader()
        sessions.forEach { session ->
            val key = session.start.toEpochMilli()
            if (details.containsKey(key)) return@forEach
            details[key] = withContext(Dispatchers.IO) {
                reader.summary(context, session.start, session.end)
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Activity", onBack = onBack)

        if (sessions.isEmpty()) {
            Text(
                "No sessions in the last seven days. Workouts appear here once " +
                    "Google Health writes them to Health Connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            return@Column
        }

        val total = sessions.fold(Duration.ZERO) { sum, session -> sum + session.length }
        val longest = sessions.maxByOrNull { it.length.seconds }
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now(zone).toEpochDay()
        val days = (6L downTo 0L).map { today - it }
        val byDay = sessions.groupBy { it.start.atZone(zone).toLocalDate().toEpochDay() }
            .mapValues { (_, rows) -> rows.sumOf { it.length.toMinutes() }.toDouble() }

        // One card: the week's total, the days it came from, and the goal it
        // is measured against. They were three cards saying one thing.
        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${total.toMinutes()}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "minutes in the last 7 days · goal ${goals.activeMinutes}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChangeChip(
                    (total.toMinutes() - goals.activeMinutes).toDouble(),
                    "min",
                    activity,
                )
            }

            GoalBars(
                values = days.map { byDay[it] ?: 0.0 },
                // A weekly goal spread evenly over seven days: the line is
                // the pace that reaches it, not a daily target anybody set.
                target = goals.activeMinutes / 7.0,
                colour = activity,
                dim = activity.copy(alpha = 0.45f),
                height = 140.dp,
                modifier = Modifier.padding(top = 12.dp)
                    .scrubbable(days.size, CHART_GUTTER) { pickedDay = it },
                axes = axes,
                xTicks = weekdayTicks(days),
                selected = pickedDay,
                goalLabel = "${(goals.activeMinutes / 7.0).roundToInt()}m",
                yLabel = { "${it.roundToInt()}m" },
            )
            val onDay = pickedDay?.let { days.getOrNull(it) }
            ChartReadout(
                day = onDay,
                value = onDay?.let { "${(byDay[it] ?: 0.0).roundToInt()} min" },
                detail = onDay?.let { day ->
                    val count = sessions.count {
                        it.start.atZone(zone).toLocalDate().toEpochDay() == day
                    }
                    when (count) {
                        0 -> "no sessions"
                        1 -> "1 session"
                        else -> "$count sessions"
                    }
                },
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            val summaries = sessions.mapNotNull { details[it.start.toEpochMilli()] }
            val burned = summaries.sumOf { it.kcal ?: 0.0 }
            val averages = summaries.mapNotNull { it.averageBpm }
            Row {
                Stat("Sessions", "${sessions.size}", Modifier.weight(1f))
                Stat(
                    "Burned",
                    if (burned > 0) count(burned.roundToInt().toLong()) + " kcal" else "—",
                    Modifier.weight(1.4f),
                )
                Stat(
                    "Avg bpm",
                    averages.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString()
                        ?: "—",
                    Modifier.weight(0.9f),
                )
                Stat(
                    "Peak bpm",
                    summaries.mapNotNull { it.peakBpm }.maxOrNull()?.toString() ?: "—",
                    Modifier.weight(0.9f),
                )
            }
            Text(
                "Longest ${longest?.length?.asHoursMinutes() ?: "—"} · " +
                    "${days.count { (byDay[it] ?: 0.0) > 0 }} of 7 days active",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // A dash on every row is worth one offer to fix it, in one place,
        // rather than the same sentence under each session.
        if (details.values.any { !it.distanceAllowed }) {
            SettingsRow(
                title = "Allow distance",
                subtitle = "Health Connect has not been given it, so no session " +
                    "here can show how far it went",
                onClick = { ask.launch(HealthConnect.readPermissions) },
            )
        }

        // Five is a glance. "Show all" used to expand this list, which
        // only ever held seven days -- so it promised everything and gave
        // you the same week. It opens the history instead.
        SectionHeader(
            text = "Last 7 days",
            action = "Show all",
            onAction = { onOpen("healthsessions") },
        )
        sessions.take(5).forEach { session ->
            SessionRow(session, details[session.start.toEpochMilli()], activity) {
                onOpen("healthsession/${session.start.toEpochMilli()}")
            }
        }

        Text(
            "Sessions are read from Health Connect. Air Bridge records none of its " +
                "own: the Air already reports them through Google Health, and a " +
                "second copy would be counted twice by everything that reads your " +
                "totals.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun SessionRow(
    session: Session,
    detail: SessionSummary?,
    colour: Color,
    onOpen: () -> Unit,
) = DetailCard(onClick = onOpen) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Text(
                session.exerciseName().take(1),
                style = MaterialTheme.typography.titleMedium,
                color = colour,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(session.exerciseName(), style = MaterialTheme.typography.bodyLarge)
            Text(
                session.whenLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            session.length.asHoursMinutes(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.extras.disabled,
        )
    }

    // The four figures worth seeing without opening the session, each in
    // its own metric's colour, and the session's shape beside them. A walk
    // the band recorded without heart data keeps its shape and says what is
    // missing rather than printing a plausible zero.
    val extras = MaterialTheme.extras
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Row(Modifier.weight(1f)) {
            Metric(
                "km",
                detail?.distanceKm?.let { formatOneDecimal(it) },
                MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f),
            )
            Metric(
                "kcal",
                detail?.kcal?.let { count(it.roundToInt().toLong()) },
                extras.energy,
                Modifier.weight(1f),
            )
            Metric("avg", detail?.averageBpm?.toString(), extras.heart, Modifier.weight(1f))
            Metric("peak", detail?.peakBpm?.toString(), extras.zonePeak, Modifier.weight(1f))
        }
        if (detail != null && detail.trace.size >= 2) {
            SparkLine(
                values = detail.trace,
                colour = colour,
                bands = listOf(
                    Zone.Vigorous.range() to extras.zoneVigorous,
                    Zone.Peak.range() to extras.zonePeak,
                ),
                modifier = Modifier.width(86.dp).padding(start = 10.dp),
            )
        }
    }
    if (detail != null && detail.averageBpm == null) {
        Text(
            "no heart data",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The beats a zone covers, for the bands behind a row's spark line. */
private fun Zone.range(): ClosedFloatingPointRange<Double> =
    lowBpm(ASSUMED_MAX_HEART_RATE).toDouble()..highBpm(ASSUMED_MAX_HEART_RATE).toDouble()

/** One of a session's figures, or a dash where the session carried none. */
@Composable
private fun Metric(label: String, value: String?, colour: Color, modifier: Modifier) =
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
        )
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (value == null) MaterialTheme.extras.disabled else colour,
            maxLines = 1,
        )
    }

internal val Session.length: Duration get() = Duration.between(start, end)

/**
 * The title the writer gave it, or the exercise type's own name. Health
 * Connect stores the type as an int, and the handful this band produces are
 * worth naming properly rather than printing "Workout" for all of them.
 */
internal fun Session.exerciseName(): String = title?.takeIf { it.isNotBlank() } ?: when (exerciseType) {
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> "Run"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> "Cycle"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> "Swim"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"
    androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    else -> "Workout"
}

private val sessionFormat = DateTimeFormatter.ofPattern("EEE · HH:mm")

internal fun Session.whenLabel(): String =
    start.atZone(ZoneId.systemDefault()).format(sessionFormat)
