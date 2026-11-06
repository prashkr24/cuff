package com.srineere.airbridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.sleepScoreOf
import com.srineere.airbridge.health.SessionReader
import com.srineere.airbridge.health.HeartSample
import com.srineere.airbridge.health.SleepScore
import com.srineere.airbridge.health.SleepStage
import com.srineere.airbridge.ui.theme.AirExtraColors
import com.srineere.airbridge.ui.theme.extras
import android.text.format.DateFormat
import java.time.Duration
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Sleep in full.
 *
 * The score is here, but never on its own: a composite is a judgement, so the
 * four things it is made of sit beside it and a bad night can be read rather
 * than merely scored. Efficiency sits with them because that one is a fact --
 * asleep over time in bed, with no weighting to argue about.
 */
@Composable
internal fun HealthSleepScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { state.loadGoals(context) }
    val goals = state.goals
    val extras = MaterialTheme.extras
    val axes = rememberAxes()
    val nights = data.nights
    val window = rememberChartWindow(14)
    var picked by remember { mutableStateOf<Int?>(null) }
    val shown = window.slice(nights) { it.day }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Sleep", onBack = onBack)

        val newest = nights.lastOrNull()
        if (newest == null) {
            Text(
                "No nights yet. Google Health writes them to Health Connect once " +
                    "your band syncs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            return@Column
        }

        // The night the card is about: whichever one the finger is on, or
        // last night when nothing is. Everything below the chart follows it,
        // because a score beside a chart of a different night is two nights
        // pretending to be one.
        val night = picked?.let { shown.getOrNull(it) } ?: newest
        val score = remember(night, nights) { sleepScoreOf(night, nights) }

        // Last night's beats are already loaded; any other night is a read.
        var olderBeats by remember(night.day) { mutableStateOf<List<HeartSample>?>(null) }
        LaunchedEffect(night.day) {
            olderBeats = if (night.day == newest.day) data.snapshot.heartRecent else {
                withContext(Dispatchers.IO) {
                    SessionReader().heartSamples(context, night.start, night.end)
                }
            }
        }
        val beats = olderBeats?.let { sleepingRate(it, night) }

        val sleptEnough = goals.sleepMinutes > 0 && night.asleep.toMinutes() >= goals.sleepMinutes
        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            night.asleep.asHoursMinutes(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (sleptEnough) {
                            Spacer(Modifier.width(8.dp))
                            GoalMark(extras.sleepRem, size = 22.dp)
                        }
                    }
                    Text(
                        // Which night, and whether it reached the goal. The
                        // mark alone would not say what was met.
                        if (night.day == newest.day) "last night" else dayLabel(night.day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sleptEnough) extras.sleepRem
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                data.sleepTrend?.let { trend ->
                    ChangeChip(trend.change, "min", extras.sleepRem)
                }
            }

            WindowBar(
                window = window,
                options = listOf(7, 14, 30),
                onChange = {
                    picked = null
                    state.widen(context, window.daysNeeded)
                },
                modifier = Modifier.padding(top = 10.dp),
                label = { "$it nights" },
            )

            // Taller than it was, with a scale and the dates under it: this
            // is the chart the screen is for, and it was previously the same
            // size as the sparkline on the tile that opens it.
            if (shown.isEmpty()) {
                Text(
                    if (state.loading) "Reading these days…" else "No nights recorded in these days.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                StageBars(
                    nights = shown.map { it.stages() },
                    deep = extras.sleepDeep,
                    light = extras.sleepLight,
                    rem = extras.sleepRem,
                    awake = extras.sleepAwake,
                    height = 150.dp,
                    modifier = Modifier.padding(top = 12.dp)
                        .scrubbable(shown.size, CHART_GUTTER) { picked = it },
                    axes = axes,
                    xTicks = dateTicks(
                        shown.map { it.day },
                        if (window.isNow) "Last night" else dayLabel(shown.last().day),
                    ),
                    goalMinutes = goals.sleepMinutes.toDouble(),
                    goalColour = extras.sleepRem,
                    goalLabel = "${goals.sleepMinutes / 60}h",
                    selected = picked,
                )
                StageKey()
                val at = picked?.let { shown.getOrNull(it) }
                ChartReadout(
                    day = at?.day,
                    value = at?.asleep?.asHoursMinutes(),
                    detail = at?.let { night ->
                        night.efficiency?.let { "${(it * 100).roundToInt()}% efficient" }
                    },
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // The score belongs to the same card as the nights it is made
            // of. Apart, it was a verdict on last night with the evidence
            // for it in a different box.
            ScoreBlock(score)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // Usual is two times joined by a dash, so it needs half again
            // the room of a single figure or it runs into its neighbour.
            Row {
                Stat(
                    "Efficiency",
                    night.efficiency?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    Modifier.weight(1f),
                )
                Stat(
                    "Sleeping HR",
                    beats?.let { "$it bpm" } ?: "—",
                    Modifier.weight(1.3f),
                )
                Stat(
                    "Usual",
                    data.sleepTrend?.let {
                        "${minutes(it.usual.low)}–${minutes(it.usual.high)}"
                    } ?: "—",
                    Modifier.weight(1.5f),
                )
            }
        }

        LastNight(night, newest.day, extras)

        SectionHeader(
            if (night.day == newest.day) "Last night, by stage"
            else "${dayLabel(night.day)}, by stage",
        )
        if (!night.hasStages) {
            Text(
                "This night arrived without a stage breakdown, so it is one block " +
                    "rather than an invented one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        } else {
            val total = night.stages().total.takeIf { it > 0 } ?: 1.0
            StageRow("Deep", night.deep, total, extras.sleepDeep)
            StageRow("Light", night.light, total, extras.sleepLight)
            StageRow("REM", night.rem, total, extras.sleepRem)
            StageRow("Awake", night.awake, total, extras.sleepAwake)
        }

        SettingsRow(
            title = "Sleep bank",
            subtitle = "What these nights add up to, against your goal",
            onClick = { onOpen("healthsleepbank") },
        )

        Text(
            "Stages come from Google Health through Health Connect. A night the band " +
                "was off your wrist is a gap, not a zero.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}


/**
 * A date at either end of a run of days, as x labels.
 *
 * The newest end is named rather than dated, because "Today" and "Last
 * night" are what the data at that end actually is -- and which of the two
 * depends on whether the series counts days or nights.
 */
internal fun dateTicks(days: List<Long>, newest: String = "Today"): List<Pair<Float, String>> {
    if (days.size < 2) return emptyList()
    return listOf(0f to dayLabel(days.first()), 1f to newest)
}

/**
 * The score, and the four things it is made of.
 *
 * A composite is a judgement, so it never appears alone: the parts are not
 * a detail view behind a tap, they are the score's working shown. Each is
 * measured against your own nights, which is why no bar here is marked
 * "good" -- 70 means "like your usual", not "adequate sleep".
 */
@Composable
private fun ColumnScope.ScoreBlock(score: SleepScore?) {
    val total = score?.total
    if (score == null || total == null) {
        Text(
            "Not enough nights behind this one to score it. Five gives the score " +
                "something to measure against.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val colour = MaterialTheme.extras.sleepRem
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(94.dp)) {
            Text(
                "$total",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = colour,
            )
            Text(
                "Sleep score",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            score.parts.forEach { (name, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(86.dp),
                    )
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(value / 100f).height(6.dp)
                                .clip(RoundedCornerShape(50)).background(colour),
                        )
                    }
                    Text(
                        "$value",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(30.dp),
                    )
                }
            }
        }
    }
    Text(
        "Out of ${score.basis}, each measured against your own nights rather " +
            "than a recommended figure. There is no target here.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.extras.disabled,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * The night as it happened.
 *
 * Four lanes, awake at the top, in the same order the stacked bars stack --
 * so a glance between the two charts does not have to be re-learned. This is
 * the one sleep chart that is not a total: it says when the deep sleep came,
 * which is the part a stacked bar throws away.
 */
@Composable
private fun LastNight(night: Night, newestDay: Long, extras: AirExtraColors) {
    if (night.segments.isEmpty()) return
    var at by remember(night.day) { mutableStateOf<Float?>(null) }
    val clock = remember {
        DateTimeFormatter
            .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm"))
            .withZone(ZoneId.systemDefault())
    }
    SectionHeader(if (night.day == newestDay) "Last night" else dayLabel(night.day))
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${clock.format(night.start)} – ${clock.format(night.end)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                when (night.wakes) {
                    0 -> "slept through"
                    1 -> "1 wake"
                    else -> "${night.wakes} wakes"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The lanes are rows, so their names belong beside them. Across the
        // bottom they read as an axis, which is the one thing this chart's
        // horizontal direction is not.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Column(Modifier.width(46.dp).height(86.dp)) {
                listOf("Awake", "REM", "Light", "Deep").forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Hypnogram(
            segments = night.segments.map { segment ->
                HypnogramBar(
                    fromMillis = segment.start.toEpochMilli(),
                    toMillis = segment.end.toEpochMilli(),
                    lane = when (segment.stage) {
                        SleepStage.Awake -> 0
                        SleepStage.Rem -> 1
                        SleepStage.Light -> 2
                        SleepStage.Deep -> 3
                    },
                    colour = when (segment.stage) {
                        SleepStage.Awake -> extras.sleepAwake
                        SleepStage.Rem -> extras.sleepRem
                        SleepStage.Light -> extras.sleepLight
                        SleepStage.Deep -> extras.sleepDeep
                    },
                )
            },
                    modifier = Modifier.weight(1f)
                    .scrubbableAcross(0.dp) { at = it },
                selected = at,
                markerColour = extras.sleepRem,
            )
        }
        // Which stage, and when. A hypnogram's whole point is where in the
        // night something happened, and that needs a clock.
        val moment = at?.let { fraction ->
            val from = night.segments.first().start.toEpochMilli()
            val until = night.segments.last().end.toEpochMilli()
            val want = from + ((until - from) * fraction).toLong()
            night.segments.firstOrNull {
                want in it.start.toEpochMilli()..it.end.toEpochMilli()
            } ?: night.segments.last()
        }
        Row(modifier = Modifier.height(26.dp).padding(top = 8.dp)) {
            if (moment != null) {
                Text(
                    clock.format(moment.start),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "  ${moment.stage.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "  ·  ${moment.length.toMinutes()} min",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extras.disabled,
                )
            }
        }
    }
}

@Composable
private fun StageRow(name: String, length: Duration, total: Double, colour: Color) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(colour))
    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(52.dp))
    Box(
        Modifier
            .weight(1f)
            .height(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth((length.toMinutes() / total).toFloat().coerceIn(0f, 1f))
                .height(7.dp)
                .clip(CircleShape)
                .background(colour),
        )
    }
    Text(
        length.asHoursMinutes(),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.width(62.dp),
        textAlign = TextAlign.End,
    )
}

/** One night, placed in the usual band rather than printed as a bare total. */
@Composable
private fun NightRow(night: Night, low: Double?, high: Double?, colour: Color) {
    val minutes = night.asleep.toMinutes().toDouble()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(
            dayLabel(night.day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        if (low != null && high != null && high > low) {
            val span = (high - low) * 2
            val from = low - (high - low) / 2
            Box(
                Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(((minutes - from) / span).toFloat().coerceIn(0.02f, 1f))
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(colour),
                )
            }
        } else {
            Box(Modifier.weight(1f))
        }
        Text(
            night.asleep.asHoursMinutes(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(62.dp),
            textAlign = TextAlign.End,
        )
    }
}

internal fun Night.stages(): FourStages {
    val stages = FourStages(
        deep = deep.toMinutes().toDouble(),
        light = light.toMinutes().toDouble(),
        rem = rem.toMinutes().toDouble(),
        awake = awake.toMinutes().toDouble(),
    )
    // A night with no breakdown is drawn as one plain bar rather than a
    // breakdown this app made up.
    return if (stages.total > 0) stages
    else FourStages(0.0, asleep.toMinutes().toDouble(), 0.0, 0.0)
}

private fun minutes(value: Double): String {
    val total = value.roundToInt()
    return "${total / 60}h${"%02d".format(total % 60)}"
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

internal fun dayLabel(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    return if (date == LocalDate.now()) "Last night" else date.format(dayFormat)
}

@Composable
internal fun DetailCard(
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Only a card that does something gets a press: a card that shrinks
    // under a finger and then does nothing is a promise it did not keep,
    // and most of these are read rather than tapped.
    val interaction = rememberPressSource()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .then(if (onClick == null) Modifier else Modifier.pressBounce(interaction)),
    ) {
        Column(
            Modifier
                .then(
                    if (onClick == null) Modifier
                    else Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                )
                .padding(14.dp),
            content = content,
        )
    }
}
