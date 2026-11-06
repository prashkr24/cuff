package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.roundToInt

/**
 * The five nightly readings, each as a position in its own band.
 *
 * No composite score. The published method for readiness is a rolling
 * average read against a baseline and a smallest worthwhile change, which is
 * a band and not a number -- so that is what this draws, and the sentence
 * under it says whether anything has actually moved. Inventing a 0-100
 * figure on top would be a clinical claim this app has no standing to make.
 */
@Composable
internal fun HealthVitalsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val data by healthData(LocalContext.current)
    val extras = MaterialTheme.extras

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Vitals", subtitle = "Against your own last 30 nights", onBack = onBack)

        val rows = listOf(
            VitalRow("Resting heart rate", data.restingTrend, "bpm", extras.heart, 0, route = "healthtrend"),
            VitalRow("HRV", data.hrvTrend, "ms", extras.hrv, 0, route = "healthhrv"),
            VitalRow(
                "Breathing rate", data.breathingTrend, "br/min", extras.activity, 1,
                route = "healthbreathing",
            ),
            VitalRow(
                "Skin temperature", data.skinTrend, "°C vs usual", extras.energy, 1,
                signed = true, route = "healthskin",
            ),
        )

        DetailCard {
            rows.forEachIndexed { index, row ->
                VitalLine(row, onOpen)
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        // Asked often enough to be answered here rather than left as a gap
        // somebody has to work out for themselves. Nothing at this end can
        // fix it, so the note says where it stops.
        Text(
            "Blood oxygen is not on this screen. Your band measures it and Google " +
                "Health reads it, but Google Health does not write it to Health " +
                "Connect — oxygen saturation is read-only on its side — so there is " +
                "nothing here to show.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        val movedNames = rows.filter { it.trend?.recentHasMoved == true }
            .map { it.name.lowercase() }

        Readiness(data.hrvTrend ?: data.restingTrend, extras.hrv, movedNames)

        // The card above says whether anything moved; the screen behind
        // this says by how much, and what the figure stands on.
        SettingsRow(
            title = "Readiness",
            subtitle = "This morning against your own mornings, as a figure",
            onClick = { onOpen("readiness") },
        )
        SettingsRow(
            title = "Fitness age",
            subtitle = "Your VO₂ max, expressed in years",
            onClick = { onOpen("fitnessage") },
        )

        Text(
            // This used to describe the smallest worthwhile change, which is a
            // different figure on the same Trend and not the one drawn here.
            "These are your own ranges, built from your own nights: the band holds " +
                "about five readings in six of your own, so a reading outside " +
                "it is one of your own unusual days. They are not reference ranges " +
                "and not a diagnosis — a figure can sit outside yours and be " +
                "perfectly ordinary for a person.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

internal data class VitalRow(
    val name: String,
    val trend: Trend?,
    val unit: String,
    val colour: Color,
    val decimals: Int,
    /** Skin temperature is a change from your baseline, so it carries a sign. */
    val signed: Boolean = false,
    /** Where the row goes, for the two that have a screen of their own. */
    val route: String? = null,
)

/**
 * Readiness, done the only way this app can defend.
 *
 * Not an invented 0-100 score: the published method is a short rolling
 * average read against a longer baseline and your own smallest worthwhile
 * change, which is a band. A number on top of that band would be a clinical
 * claim, so the band is what is drawn and the sentence says what it means.
 */
@Composable
private fun Readiness(trend: Trend?, colour: Color, moved: List<String>) {
    if (trend == null || trend.readings.size < 14) return
    val swc = trend.smallestWorthwhileChange
    val baseline = trend.average
    val recent = trend.recent
    val inside = kotlin.math.abs(recent - baseline) <= swc

    SectionHeader("Readiness")
    DetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (inside) "Normal variation" else "Something has moved",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colour,
                modifier = Modifier.weight(1f),
            )
        }
        // The rail spans three smallest-worthwhile-changes either side of
        // the baseline, so a reading outside the band is visibly outside it
        // rather than pinned to the end.
        val reach = (swc * 3).takeIf { it > 0.0 } ?: 1.0
        fun at(value: Double) =
            (((value - (baseline - reach)) / (reach * 2)).toFloat()).coerceIn(0f, 1f)
        BandRows(
            rails = listOf(BandRail(at(recent), at(baseline - swc), at(baseline + swc), colour)),
            track = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(top = 12.dp),
            rowHeight = 10.dp,
        )
        // One sentence. It used to be three paragraphs explaining a method
        // nobody asked about, which buried the one fact the card carries.
        Text(
            if (moved.isEmpty()) {
                "Your last seven nights sit inside your own usual range."
            } else {
                "Your " + moved.joinToString(" and ") + " " +
                    (if (moved.size == 1) "has" else "have") + " moved more than usual."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun VitalLine(row: VitalRow, onOpen: (String) -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth()
        .then(
            // Only the two with a screen behind them are tappable. A row
            // that looks tappable and does nothing is worse than a flat one.
            if (row.route == null) Modifier else Modifier.clickable { onOpen(row.route) },
        )
        .padding(vertical = 13.dp),
) {
    Text(
        row.name,
        style = MaterialTheme.typography.bodyMedium,
        color = if (row.trend == null) MaterialTheme.extras.disabled
        else MaterialTheme.colorScheme.onSurface,
        // Two lines where it needs them. The rail is the point of this row,
        // so the name gives up width rather than the thing being measured.
        maxLines = 2,
        modifier = Modifier.width(92.dp),
    )

    if (row.trend == null) {
        // A metric nothing writes says so, rather than showing a dash that
        // could equally mean a quiet night.
        Text(
            "nothing written",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.width(66.dp),
            textAlign = TextAlign.End,
        )
        return@Row
    }

    RangeBar(row.trend, row.colour, Modifier.weight(1f))
    Column(Modifier.width(66.dp), horizontalAlignment = Alignment.End) {
        Text(
            format(row.trend.latest, row.decimals, row.signed),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            row.unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The usual band, with tonight's reading placed on it. */
@Composable
private fun RangeBar(trend: Trend, colour: Color, modifier: Modifier) {
    val span = (trend.highest - trend.lowest).takeIf { it > 0.0 } ?: 1.0
    val from = trend.lowest - span * 0.1
    val to = trend.highest + span * 0.1
    fun at(value: Double) = ((value - from) / (to - from)).toFloat().coerceIn(0f, 1f)

    // Taller than it was, with the middle of the band marked: the question
    // this rail answers is "how far from normal", and that needs something
    // to be far from. The dot is the reading; the notch is your middle.
    Box(modifier.height(14.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth
            val middle = (trend.usual.low + trend.usual.high) / 2
            Box(
                Modifier
                    .padding(start = width * at(trend.usual.low))
                    .width(width * (at(trend.usual.high) - at(trend.usual.low)))
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(colour.copy(alpha = 0.28f)),
            )
            Box(
                Modifier
                    .padding(start = (width * at(middle) - 1.dp).coerceIn(0.dp, width - 2.dp))
                    .width(2.dp)
                    .height(14.dp)
                    .background(colour.copy(alpha = 0.55f)),
            )
            Box(
                Modifier
                    .padding(start = (width * at(trend.latest) - 8.dp).coerceIn(0.dp, width - 16.dp))
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colour),
            )
        }
    }
}

private fun format(value: Double, decimals: Int, signed: Boolean = false): String {
    val text = if (decimals == 0) value.roundToInt().toString() else "%.${decimals}f".format(value)
    return if (signed && value > 0) "+$text" else text
}
