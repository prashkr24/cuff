package com.srineere.airbridge.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.trendOf
import com.srineere.airbridge.ui.theme.extras
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Heart rate variability, in full.
 *
 * The one metric on this tab with no good direction, which is why there is
 * no target on this screen and no colour anywhere meaning better or worse.
 * The useful reading is movement away from your own band, so that is what
 * the screen is built out of.
 */
@Composable
internal fun HealthHrvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    val colour = LocalTileColour.current ?: MaterialTheme.extras.hrv
    val all = data.snapshot.hrv
    val axes = rememberAxes()
    val window = rememberChartWindow(30)
    var picked by remember { mutableStateOf<Int?>(null) }
    val readings = window.slice(all) { it.day }
    val trend = remember(readings) { trendOf(readings) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("HRV", onBack = onBack)

        if (all.size < 3) {
            Text(
                "Nothing has written a variability reading yet. Your band records " +
                    "it overnight, and Google Health passes it on once it syncs.",
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
                            trend?.latest?.roundToInt()?.toString() ?: "—",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            " ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        // HRV is written overnight, so the newest figure
                        // is last night's only if a night ended today.
                        // Before the band syncs it belongs to yesterday.
                        when {
                            trend?.readings?.lastOrNull()?.day ==
                                java.time.LocalDate.now().toEpochDay() -> "last night, RMSSD"
                            window.isNow -> "latest reading, RMSSD"
                            else -> "last in this window"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                trend?.let { ChangeChip(it.change, "ms", colour) }
            }

            WindowBar(
                window = window,
                options = listOf(7, 30, 90),
                onChange = {
                    picked = null
                    state.widen(context, window.daysNeeded)
                },
                modifier = Modifier.padding(top = 10.dp),
                label = { "$it nights" },
            )

            if (trend == null) {
                Text(
                    if (state.loading) "Reading these days…"
                    else "Fewer than three nights in these days have a reading.",
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
                    yLabel = { "${it.roundToInt()}" },
                    selected = picked,
                )
                val at = picked?.let { readings.getOrNull(it) }
                ChartReadout(
                    day = at?.day,
                    value = at?.let { "${it.value.roundToInt()} ms" },
                    detail = at?.let {
                        if (it.value in trend.usual) "inside your usual" else "outside it"
                    },
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row {
                    Stat("Average", "${trend.average.roundToInt()}", Modifier.weight(1f), unit = "ms")
                    Stat(
                        "Usual",
                        "${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}",
                        Modifier.weight(1.2f),
                        unit = "ms",
                    )
                    Stat(
                        "Spread",
                        "${trend.lowest.roundToInt()}–${trend.highest.roundToInt()}",
                        Modifier.weight(1.2f),
                        unit = "ms",
                    )
                }
            }
        }

        WeekdayCard(readings, colour)

        Text(
            "HRV is personal: a figure that is high for you may be low for someone " +
                "else, and the useful signal is movement away from your own band, " +
                "not the number. There is no target here and no colour saying good " +
                "or bad, because this app is not qualified to set one.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * The average for each day of the week.
 *
 * A pattern you can act on — Sundays are low, Wednesdays are high — rather
 * than a verdict about the number itself. This is the one place in the app
 * where the week is Monday to Sunday, because the weekday *is* the subject:
 * a rolling window would put the same day in a different column every time.
 */
@Composable
private fun WeekdayCard(readings: List<Reading>, colour: androidx.compose.ui.graphics.Color) {
    if (readings.size < 7) return
    val byDay = remember(readings) {
        readings.groupBy { LocalDate.ofEpochDay(it.day).dayOfWeek }
            .mapValues { (_, rows) -> rows.map { it.value }.average() }
    }
    if (byDay.size < 3) return
    val highest = byDay.values.max()
    val lowest = byDay.values.min()
    val lowestDay = byDay.entries.minByOrNull { it.value }?.key
    val locale = Locale.getDefault()

    SectionHeader("By weekday")
    DetailCard {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(80.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                val value = byDay[day]
                Box(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (value != null) {
                        // Scaled from a floor below the lowest day, not from
                        // zero: every weekday would otherwise be a full bar
                        // with a few pixels between them.
                        val floor = lowest - (highest - lowest).coerceAtLeast(1.0)
                        val share = ((value - floor) / (highest - floor)).toFloat()
                        Box(
                            Modifier.fillMaxWidth()
                                .fillMaxHeight(share.coerceIn(0.08f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (day == lowestDay) colour else colour.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (lowestDay != null) {
            Text(
                "Lowest on ${lowestDay.getDisplayName(TextStyle.FULL, locale)}s, " +
                    "averaging ${byDay.getValue(lowestDay).roundToInt()} ms against " +
                    "${highest.roundToInt()} at your best.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
