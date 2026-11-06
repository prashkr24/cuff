package com.srineere.airbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.Readiness
import com.srineere.airbridge.health.ReadinessPart
import com.srineere.airbridge.health.readinessOf
import com.srineere.airbridge.ui.theme.extras
import kotlin.math.roundToInt

/**
 * Where this morning sits against your own mornings.
 *
 * The figure and the band it sits in are drawn together, never apart. 68
 * alone is a claim; 68 against your own sixty nights is a reading, and
 * putting the band behind the number rather than beside it means a
 * screenshot cannot separate the two.
 *
 * Fifty is "like you usually are", not "like an average person". There is
 * no population anywhere in this number — which is its whole value, and the
 * reason it is withheld rather than guessed until there are enough nights
 * to have a usual at all.
 */
@Composable
internal fun ReadinessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val extras = MaterialTheme.extras
    val ink = LocalTileColour.current ?: extras.hrv

    // Without this the figure is stuck: the tab reads thirty days, the gate
    // counts sixty readings, and nothing else on this screen would ever ask
    // for more. Widening is one way and costs one read.
    LaunchedEffect(Unit) { state.widen(context, Readiness.WINDOW_DAYS) }
    val readiness = remember(data.snapshot) { readinessOf(data.snapshot) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Readiness", subtitle = "this morning", onBack = onBack)

        DetailCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    readiness.score?.toString() ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (readiness.calibrating) extras.disabled else ink,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        if (readiness.calibrating) "Calibrating"
                        else readiness.word.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        readiness.trend?.let { trend ->
                            "your own ${readiness.days} nights · usual is " +
                                "${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}"
                        } ?: "not enough nights behind it yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.disabled,
                    )
                }
            }

            readiness.trend?.takeIf { !readiness.calibrating }?.let { trend ->
                // The scale is the person's own run of readiness values,
                // widened a little so a figure outside the usual band is
                // visibly outside it rather than pinned to the end.
                ReadinessRail(
                    value = trend.latest,
                    low = trend.usual.low,
                    high = trend.usual.high,
                    from = minOf(trend.lowest, trend.usual.low) - 3,
                    to = maxOf(trend.highest, trend.usual.high) + 3,
                    colour = ink,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Text(
                "Fifty means like you usually are, not like an average person. " +
                    "There is no population in this number.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SectionHeader(
            "What it stands on",
            action = "${readiness.standing.size} of ${readiness.parts.size}",
        )
        DetailCard {
            readiness.parts.forEachIndexed { index, part ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PartRow(part, ink)
            }
        }

        if (readiness.calibrating) {
            SectionHeader("Before it can say anything")
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    (readiness.days.toFloat() / Readiness.NEEDED).coerceIn(0f, 1f),
                                )
                                .height(6.dp).clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                    Text(
                        " ${readiness.days} / ${Readiness.NEEDED}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (readiness.days == 0) {
                        "Nothing has written an HRV reading, which is the one part this " +
                            "cannot be worked out without. Check that it is allowed in " +
                            "Health Connect."
                    } else {
                        "Until there are ${Readiness.NEEDED} nights behind it there is no " +
                            "band to be inside, so the figure is withheld rather than " +
                            "guessed. The parts above are shown throughout — they are " +
                            "measurements either way."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Text(
            "A summary of your own readings, not a verdict on your health. It cannot " +
                "see illness, medication, alcohol or a bad day at work unless one of " +
                "the four above moved.",
            style = MaterialTheme.typography.labelMedium,
            color = extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/**
 * One part: its name, where it sits in its own band, and its figure.
 *
 * The same rail the Vitals screen draws, because it is the same question
 * asked of a different reading. A part with nothing behind it keeps its
 * row and says so, rather than vanishing and leaving a composite that
 * looks complete.
 */
@Composable
private fun PartRow(part: ReadinessPart, ink: Color) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
) {
    Column(Modifier.width(96.dp)) {
        Text(
            part.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (part.present) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.extras.disabled,
        )
        part.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extras.disabled,
                maxLines = 1,
            )
        }
    }
    val usual = part.usual
    if (part.value == null || usual == null) {
        Text(
            "nothing written",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(
            "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            textAlign = TextAlign.End,
            modifier = Modifier.width(66.dp),
        )
        return@Row
    }
    val reach = (usual.high - usual.low).takeIf { it > 0 } ?: 1.0
    ReadinessRail(
        value = part.value,
        low = usual.low,
        high = usual.high,
        from = usual.low - reach,
        to = usual.high + reach,
        colour = ink,
        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
    )
    Text(
        figure(part.value, part.decimals) + part.unit.let { if (it.isEmpty()) "" else " $it" },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(66.dp),
    )
}

/** The usual band, with one reading placed on it. */
@Composable
private fun ReadinessRail(
    value: Double,
    low: Double,
    high: Double,
    from: Double,
    to: Double,
    colour: Color,
    modifier: Modifier = Modifier,
) {
    val span = (to - from).takeIf { it > 0 } ?: 1.0
    val start = ((low - from) / span).toFloat().coerceIn(0f, 1f)
    val end = ((high - from) / span).toFloat().coerceIn(0f, 1f)
    val at = ((value - from) / span).toFloat().coerceIn(0f, 1f)
    Box(
        modifier.height(13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        // Laid out by hand rather than with weights: a band and a dot are
        // placed at fractions of the track, and a Row of spacers rounds
        // each of them to whole pixels in a different direction.
        Layout(
            content = {
                Box(
                    Modifier.height(7.dp).clip(RoundedCornerShape(50))
                        .background(colour.copy(alpha = 0.24f)),
                )
                Box(Modifier.size(11.dp).clip(CircleShape).background(colour))
            },
            modifier = Modifier.fillMaxWidth().height(13.dp),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val bandWidth = ((end - start) * width).roundToInt().coerceAtLeast(2)
            val band = measurables[0].measure(
                constraints.copy(minWidth = bandWidth, maxWidth = bandWidth),
            )
            val dot = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(width, constraints.maxHeight) {
                band.place((start * width).roundToInt(), (constraints.maxHeight - band.height) / 2)
                dot.place(
                    (at * width).roundToInt() - dot.width / 2,
                    (constraints.maxHeight - dot.height) / 2,
                )
            }
        }
    }
}

private fun figure(value: Double, decimals: Int): String =
    if (decimals == 0) value.roundToInt().toString() else "%.${decimals}f".format(value)
