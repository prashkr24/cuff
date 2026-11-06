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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.srineere.airbridge.health.HealthReader
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.exerciseName
import com.srineere.airbridge.health.SessionReader
import com.srineere.airbridge.health.SessionSummary
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Burned against eaten, as two running totals across a day.
 *
 * A single balance figure says where you ended up; two lines say how you got
 * there — that you were ahead all morning and ate it back at dinner. The gap
 * between the lines at any point is the balance at that hour.
 *
 * Today's lines stop at the current hour, because drawing burn out to
 * midnight would be a forecast and this screen has no business making one.
 * A day that is over has no such edge, so its lines run the whole way.
 */
@Composable
internal fun HealthBalanceDayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val snapshot = data.snapshot
    val extras = MaterialTheme.extras
    val burnedColour = extras.activity
    val eatenColour = extras.energy

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone).toEpochDay()
    // Days back from today, counted the way the food log counts them.
    var back by remember { mutableIntStateOf(0) }
    val day = today - back
    val hourNow = if (back == 0) LocalTime.now(zone).hour else 23

    // Today's hourly burn rides in the snapshot, because every screen that
    // wants it wants today's. Any other day is read when it is asked for.
    var olderBurn by remember(day) { mutableStateOf<List<Double>?>(null) }
    LaunchedEffect(day) {
        if (back > 0) {
            olderBurn = withContext(Dispatchers.IO) {
                HealthReader().burnedHourly(context, LocalDate.ofEpochDay(day))
            }
        }
    }

    // Food arrives as entries at a time, not as an hourly aggregate, so the
    // eaten line is built here from the entries themselves.
    //
    // Remembered, as the tile that draws the same two lines already is:
    // picked is read below, so a drag across the chart rebuilt both arrays
    // and both running totals on every pointer event.
    val (burned, eaten) = remember(snapshot.food, snapshot.burnedHourly, olderBurn, day, back, hourNow) {
        val eatenByHour = DoubleArray(24)
        snapshot.food.filter { it.day == day }.forEach { entry ->
            val hour = entry.at.atZone(zone).hour
            eatenByHour[hour] += entry.kcal ?: 0.0
        }
        val hourlySource = if (back == 0) snapshot.burnedHourly else olderBurn.orEmpty()
        val burnedByHour = hourlySource.takeIf { it.size == 24 } ?: List(24) { 0.0 }
        runningTotal(burnedByHour, hourNow) to runningTotal(eatenByHour.toList(), hourNow)
    }

    val axes = rememberAxes()
    var picked by remember { mutableStateOf<Int?>(null) }
    val haveSomething = burned.lastOrNull()?.let { it > 0 } == true ||
        eaten.lastOrNull()?.let { it > 0 } == true

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Burned against eaten", subtitle = dayName(back), onBack = onBack)

        // Above the empty case, not below it: a day with nothing in it is
        // the day you most need the arrows to get off.
        DayBar(back, oldest = state.days - 1) { back = it }

        if (!haveSomething) {
            Text(
                if (back == 0) {
                    "Nothing recorded today yet. Burn arrives from Google Health as " +
                        "your band syncs; food arrives when something logs it."
                } else {
                    "Nothing recorded that day — no burn from your band, and nothing " +
                        "logged to eat."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Box(Modifier.padding(bottom = 24.dp))
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
                        when {
                            back > 0 && balance < 0 -> "kcal behind, over that day"
                            back > 0 -> "kcal ahead, over that day"
                            balance < 0 -> "kcal behind, so far today"
                            else -> "kcal ahead, so far today"
                        },
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
                // Where the two lines end and what is between them, which is
                // the shaded gap the chart is drawn for. Said the same way
                // the figure above it is, so the two agree.
                description = spokenBalance(
                    day = dayName(back),
                    burned = burned.lastOrNull() ?: 0.0,
                    eaten = eaten.lastOrNull() ?: 0.0,
                ),
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
                Stat(
                    "Burned",
                    burned.lastOrNull()?.let { kcal(it) },
                    Modifier.weight(1f),
                    burnedColour,
                    unit = "kcal",
                )
                Stat(
                    "Eaten",
                    eaten.lastOrNull()?.let { kcal(it) },
                    Modifier.weight(1f),
                    eatenColour,
                    unit = "kcal",
                )
                Stat(
                    "Entries",
                    "${snapshot.food.count { it.day == day }}",
                    Modifier.weight(0.8f),
                )
            }
        }

        WhatMoved(
            entries = snapshot.food.filter { it.day == day },
            sessions = snapshot.sessions.filter {
                it.start.atZone(zone).toLocalDate().toEpochDay() == day
            },
            eatenColour = eatenColour,
            burnedColour = burnedColour,
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
 * Everything that moved either line, in the order the day happened.
 *
 * Food and workouts in one list rather than two. The chart above is one
 * picture of a day, and "what moved it" is a question about the day rather
 * than about which kind of record the answer came out of.
 *
 * Both figures are additions, and the colour says which line each one was
 * added to. A workout was briefly drawn as a minus, which read as though it
 * were taken off the balance; it is not. Burn is everything the body spent,
 * a run is part of that, and it moves the burn line up exactly as a meal
 * moves the eaten line up.
 */
@Composable
private fun WhatMoved(
    entries: List<FoodEntry>,
    sessions: List<Session>,
    eatenColour: Color,
    burnedColour: Color,
) {
    if (entries.isEmpty() && sessions.isEmpty()) return
    val context = LocalContext.current
    val clock = remember {
        DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm"))
            .withZone(ZoneId.systemDefault())
    }
    // A session's calories live in their own records, so each one costs a
    // read. They land one at a time and a row shows its length until its
    // figure arrives, rather than holding the whole list back.
    val details = remember { mutableStateMapOf<Long, SessionSummary>() }
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

    val dim = MaterialTheme.extras.disabled
    val plain = MaterialTheme.colorScheme.onSurface
    val moments = (
        entries.map { entry ->
            Moment(
                at = entry.at,
                name = entry.name ?: "Unnamed entry",
                nameColour = if (entry.name == null) dim else plain,
                figure = entry.kcal?.let { "+" + count(it.roundToInt().toLong()) } ?: "—",
                figureColour = eatenColour,
            )
        } + sessions.map { session ->
            val summary = details[session.start.toEpochMilli()]
            Moment(
                at = session.start,
                name = session.exerciseName() + " · " + session.length.asHoursMinutes(),
                nameColour = plain,
                figure = summary?.kcal?.let { "+" + count(it.roundToInt().toLong()) } ?: "—",
                figureColour = burnedColour,
            )
        }
        ).sortedBy { it.at }

    SectionHeader("What moved the line")
    DetailCard {
        moments.forEachIndexed { index, moment ->
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
                    clock.format(moment.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(54.dp),
                )
                Text(
                    moment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = moment.nameColour,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    moment.figure,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = moment.figureColour,
                )
            }
        }
    }
    if (sessions.isNotEmpty()) {
        Text(
            "A workout's calories are already inside the burn line — they raise " +
                "it rather than coming off what you ate. Burn is everything your " +
                "body spent, not just what you spent on purpose.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

/** One thing that happened, whichever record it came out of. */
private data class Moment(
    val at: Instant,
    val name: String,
    val nameColour: Color,
    val figure: String,
    val figureColour: Color,
)

/** Cumulative totals up to and including the current hour. */
private fun runningTotal(hours: List<Double>, until: Int): List<Double> {
    var sum = 0.0
    return (0..until.coerceIn(0, 23)).map { hour ->
        sum += hours.getOrElse(hour) { 0.0 }
        sum
    }
}

/** The figure a kcal stat shows, which both lines of this screen share. */
/** The figure alone; the two stats that use it carry "kcal" on their labels. */
private fun kcal(value: Double): String = count(value.roundToInt().toLong())

/**
 * The chart said in one sentence: where the two lines end, and the gap
 * between them.
 *
 * The gap is the whole point of the drawing — it is the shaded area, and
 * the figure in large type above it — so it is said rather than left to be
 * subtracted. "Behind" and "ahead" are the words the card already uses for
 * which side of the balance a day is on.
 */
private fun spokenBalance(day: String, burned: Double, eaten: Double): String =
    "Burned against eaten. $day. ${kcal(burned)} calories burned, ${kcal(eaten)} eaten, " +
        (if (eaten < burned) "behind by " else "ahead by ") +
        kcal(abs(eaten - burned)) + "."
