package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.health.trendOf
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.roundToInt

/**
 * Which series the trend screen is showing.
 *
 * One screen for all of them: a nightly reading against its own usual band
 * is the same shape of question whatever the units, and five hand-written
 * screens would be five places for the same chart to drift apart.
 */
internal enum class TrendMetric(
    val title: String,
    val unit: String,
    val route: String,
    /** The step the histogram buckets by, in the metric's own units. */
    val bucket: Double,
    val decimals: Int,
    /**
     * The same unit with nothing abbreviated, for a screen reader.
     *
     * "bpm" is four characters on a card and a reader saying "b p m"; "°C"
     * is a degree sign most readers skip entirely. Written out once here
     * rather than guessed at by each chart that needs to say one.
     */
    val spokenUnit: String,
) {
    Resting("Resting heart rate", "bpm", "healthtrend", 1.0, 0, "beats per minute"),
    Hrv("HRV", "ms", "healthhrv", 2.0, 0, "milliseconds"),
    Breathing("Breathing rate", "br/min", "healthbreathing", 0.5, 1, "breaths per minute"),
    Skin("Skin temperature", "°C", "healthskin", 0.1, 1, "degrees Celsius"),
    Weight("Weight", "kg", "healthweight", 0.5, 1, "kilograms"),
    ;

    companion object {
        fun of(name: String?): TrendMetric =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: Resting
    }
}

/**
 * One measurement, over time.
 *
 * A line says where it went; the histogram says whether today is rare. Thirty
 * points is enough to shape one, and it answers the question the line cannot
 * — is 56 unusual for me, or just the low end of normal.
 */
@Composable
internal fun HealthTrendScreen(
    metric: TrendMetric,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    val colour = LocalTileColour.current ?: when (metric) {
        TrendMetric.Resting -> MaterialTheme.extras.heart
        TrendMetric.Hrv -> MaterialTheme.extras.hrv
        TrendMetric.Breathing -> MaterialTheme.extras.activity
        TrendMetric.Skin -> MaterialTheme.extras.energy
        TrendMetric.Weight -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val all = when (metric) {
        TrendMetric.Resting -> data.snapshot.restingHeartRate
        TrendMetric.Hrv -> data.snapshot.hrv
        TrendMetric.Breathing -> data.snapshot.respiratoryRate
        TrendMetric.Skin -> data.snapshot.skinTemperature
        TrendMetric.Weight -> data.snapshot.weight
    }
    val axes = rememberAxes()
    // Weight is written by hand, or by a scale somebody steps on when they
    // remember. Thirty days of it is two readings and the card then says it
    // cannot draw a trend -- which is true of the window, not of the person.
    // Ninety is the first one that usually has a line in it.
    val window = rememberChartWindow(if (metric == TrendMetric.Weight) 90 else 30)
    // Opened wide rather than widened by a tap: the tab loads a month, and a
    // default window nobody touched would otherwise ask for days no one read.
    LaunchedEffect(Unit) { state.widen(context, window.daysNeeded) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val readings = window.slice(all) { it.day }
    val trend = remember(readings) { trendOf(readings) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(metric.title, onBack = onBack)

        if (all.size < 3) {
            Text(
                if (all.isEmpty()) {
                    // The commonest cause by far, and invisible from in here:
                    // the app that records it has sharing switched off for
                    // this one measurement. Seeing a figure inside Google
                    // Health says nothing about whether it reaches anyone.
                    "Nothing has written a ${metric.title.lowercase()} reading to " +
                        "Health Connect. Seeing it inside Google Health is not the " +
                        "same thing: open Health Connect, find that app, and check " +
                        "it is allowed to write this one."
                } else {
                    "Not enough readings yet. Three gives a trend; a month gives a " +
                        "useful one."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            return@Column
        }

        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            trend?.let { metric.format(it.latest) } ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            " ${metric.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        if (window.isNow) "latest reading" else "last in this window",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trend?.let { ChangeChip(it.change, metric.unit, colour) }
            }

            // The control sits on the chart it governs. At the top of the
            // screen it would also appear to govern the histogram below.
            WindowBar(
                window = window,
                options = listOf(7, 30, 90),
                onChange = {
                    picked = null
                    state.widen(context, window.daysNeeded)
                },
                modifier = Modifier.padding(top = 10.dp),
                reading = state.wideningTo,
            )

            if (trend == null) {
                Text(
                    if (state.refreshing) "Reading these days…"
                    else "Fewer than three readings in these days, which is not " +
                        "enough to draw a trend through.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                ScatterTrend(
                    trend = trend,
                    colour = colour,
                    modifier = Modifier.padding(top = 10.dp)
                        .scrubbable(readings.size, CHART_GUTTER) { picked = it },
                    height = 140.dp,
                    axes = axes,
                    xTicks = dayTicks(trend.readings, window.isNow),
                    yLabel = { metric.format(it) },
                    selected = picked,
                    label = ChartLabel(
                        metric = metric.title,
                        unit = metric.spokenUnit,
                        window = window.spokenRange(),
                        figure = { metric.format(it) },
                    ),
                )
                val at = picked?.let { readings.getOrNull(it) }
                ChartReadout(
                    day = at?.day,
                    value = at?.let { "${metric.format(it.value)} ${metric.unit}" },
                    detail = at?.let {
                        if (it.value in trend.usual) "inside your usual" else "outside it"
                    },
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row {
                    Stat(
                        "Average",
                        metric.format(trend.average),
                        Modifier.weight(1f),
                        unit = metric.unit,
                    )
                    Stat(
                        "Usual",
                        metric.range(trend.usual.low, trend.usual.high),
                        Modifier.weight(1.4f),
                        unit = metric.unit,
                    )
                    Stat(
                        "Spread",
                        metric.range(trend.lowest, trend.highest),
                        Modifier.weight(1.4f),
                        unit = metric.unit,
                    )
                }
            }
        }

        if (trend != null) {
            SectionHeader("How your readings fall")
            Histogram(readings, trend.latest, colour, metric)

        }

        // Today's beats are a different question from your resting rate, so
        // they are a screen away rather than another chart on this one.
        if (metric == TrendMetric.Resting) {
            SettingsRow(
                title = "Today's heart rate",
                subtitle = "Every beat since this morning, and your time in each zone",
                onClick = { onOpen("healthheart") },
            )
        }

        Text(
            "${readings.size} of these ${window.length} days have a reading.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * A window chooser, sitting on the chart it governs.
 *
 * Choosing a longer window than the tab loaded makes the app read further
 * back, so the control is a request for data and not only a filter over it.
 */
@Composable
internal fun RangePills(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: (Int) -> String = { "$it days" },
) = Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier.fillMaxWidth(),
) {
    options.forEach { option ->
        val on = option == selected
        Surface(
            color = if (on) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(50),
            modifier = Modifier.weight(1f).clickable { onSelect(option) },
        ) {
            Text(
                label(option),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
            )
        }
    }
}

/**
 * How often each value came up, one bar per bucket.
 *
 * The bucket widens until the columns are few enough to label. A tenth of a
 * degree is the right step for skin temperature until you have a four-degree
 * spread, at which point it is forty columns and every label is clipped to
 * a fragment -- which is what "0. 0. 0." under this chart was.
 */
@Composable
private fun Histogram(
    readings: List<Reading>,
    latest: Double,
    colour: Color,
    metric: TrendMetric,
) {
    if (readings.size < 3) return
    val lowest = readings.minOf { it.value }
    val highest = readings.maxOf { it.value }
    val step = remember(lowest, highest, metric) {
        var candidate = metric.bucket
        // Widen in the steps people count in -- 1, 2, 5 of whatever the
        // metric's own unit is -- until a dozen columns cover the range.
        val ladder = listOf(1.0, 2.0, 5.0)
        var decade = 1.0
        var at = 0
        while ((highest - lowest) / candidate > MOST_COLUMNS) {
            candidate = metric.bucket * ladder[at % 3] * decade
            at++
            if (at % 3 == 0) decade *= 10
        }
        candidate
    }
    val buckets = readings.groupingBy { Math.round(it.value / step) }.eachCount()
    val from = buckets.keys.min()
    val until = buckets.keys.max()
    // A gap between two values is part of the shape, so every bucket between
    // the extremes is drawn even when nothing landed in it.
    val columns = (from..until).map { it to (buckets[it] ?: 0) }
    val tallest = columns.maxOf { it.second }.coerceAtLeast(1)
    val latestBucket = Math.round(latest / step)
    var picked by remember(step) { mutableStateOf<Long?>(null) }

    // Label a handful, evenly spaced, always including both ends: a label
    // under every column is a label under none of them.
    val every = ((columns.size + MOST_LABELS - 1) / MOST_LABELS).coerceAtLeast(1)
    val labelled = buildSet {
        columns.indices.filter { it % every == 0 }.forEach { add(it) }
        val last = columns.lastIndex
        if (last - (last / every) * every > every / 2) add(last)
    }

    DetailCard {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            columns.forEach { (bucket, count) ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable { picked = if (picked == bucket) null else bucket },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight((count.toFloat() / tallest).coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                when (bucket) {
                                    picked -> colour
                                    latestBucket -> colour.copy(alpha = 0.8f)
                                    else -> colour.copy(alpha = 0.3f)
                                },
                            ),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            columns.forEachIndexed { index, (bucket, _) ->
                Text(
                    if (index in labelled) metric.format(bucket * step) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val at = picked
        val shown = at ?: latestBucket
        val here = buckets[shown] ?: 0
        Text(
            when {
                at != null -> "$here of your last ${readings.size} readings came in " +
                    "at ${metric.format(at * step)} ${metric.unit}."
                here <= 1 -> "Your latest is the only reading at " +
                    "${metric.format(latest)} ${metric.unit} in ${readings.size}."
                else -> "$here of your last ${readings.size} readings came in at " +
                    "${metric.format(latest)} ${metric.unit}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Enough columns to show a shape, few enough to label. */
private const val MOST_COLUMNS = 12

/** More than this and the labels collide whatever their width. */
private const val MOST_LABELS = 6

private fun TrendMetric.format(value: Double): String {
    val text = if (decimals == 0) value.roundToInt().toString() else formatOneDecimal(value)
    // A reading of minus four hundredths of a degree is not "-0.0"; at one
    // decimal place it is zero, and the sign is an artefact of rounding.
    return if (text.trimStart('-', '\u2212').all { it == '0' || it == '.' }) {
        text.trimStart('-', '\u2212')
    } else {
        text
    }
}

/**
 * A range, written so a negative end cannot read as a subtraction.
 *
 * "-0.3-0.2" is four characters of ambiguity; "-0.3 to 0.2" is not.
 */
private fun TrendMetric.range(low: Double, high: Double): String =
    if (low < 0 || high < 0) "${format(low)} to ${format(high)}"
    else "${format(low)}–${format(high)}" 
