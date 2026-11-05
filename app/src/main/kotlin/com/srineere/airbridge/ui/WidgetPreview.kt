package com.srineere.airbridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srineere.airbridge.health.SleepStage
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.widget.Chart
import com.srineere.airbridge.widget.Face

/**
 * A widget at thumbnail size.
 *
 * Drawn from the widget's own [Face], not from a second description of it:
 * a catalogue whose previews are written separately is a catalogue that
 * goes quietly out of date, and the first person to notice is someone who
 * placed a widget expecting what the picture showed.
 *
 * It is a likeness rather than a rendering. Glance composes into
 * RemoteViews, which cannot be drawn inside this app's own UI at all, so
 * the shapes here are Compose's -- same kicker, same figure, same chart,
 * at a size where that is all there is room for anyway.
 */
@Composable
internal fun WidgetPreview(face: Face, colour: TileColour, modifier: Modifier = Modifier) {
    val tint = tileColourOf(colour)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(78.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    face.kicker.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                face.chip?.let {
                    Text(
                        "  $it",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }

            if (face.figure.isNotEmpty()) Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 5.dp),
            ) {
                Text(
                    face.figure,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                face.unit?.let {
                    Text(
                        " $it",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            face.meter?.let { progress ->
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tint),
                    )
                }
            }

            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                // A face with no chart -- most of the band's -- would be a
                // figure and a hole. Its second line goes there instead,
                // which is what the widget itself puts under the figure.
                if (face.chart == null) {
                    Text(
                        face.sub,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    MiniChart(face.chart, colour)
                }
            }
        }
    }
}

@Composable
private fun MiniChart(chart: Chart?, colour: TileColour) {
    val tint = tileColourOf(colour)
    val dim = tint.copy(alpha = 0.42f)
    // A DrawScope is not a composable, so anything from the theme has to be
    // read out here and handed in.
    val arcColours = (chart as? Chart.Arcs)?.parts?.map { tileColourOf(it.second) }.orEmpty()
    val trackColour = MaterialTheme.colorScheme.surfaceContainerHighest
    when (chart) {
        null -> Unit

        is Chart.Bars -> Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().height(22.dp),
        ) {
            val top = chart.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            // The newest reading, not the biggest one. The widget picks out
            // the last bar; a preview that picked out the tallest would be
            // highlighting a different day from the thing it is a picture of.
            val newest = chart.values.indexOfLast { it > 0f }
            chart.values.forEachIndexed { index, value ->
                if (chart.blankZeros && value <= 0f) {
                    Box(Modifier.weight(1f))
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight((value / top).coerceIn(0.08f, 1f))
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (index == newest) tint else dim),
                    )
                }
            }
        }

        is Chart.Stages -> Row(
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            modifier = Modifier.fillMaxWidth().height(10.dp),
        ) {
            val total = chart.parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1f)
            chart.parts.forEach { (share, stage) ->
                Box(
                    Modifier
                        .weight((share / total).coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(stageColourOf(stage)),
                )
            }
        }

        is Chart.Split -> Row(
            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
            modifier = Modifier.fillMaxWidth().height(10.dp),
        ) {
            val total = chart.parts.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1f)
            chart.parts.forEach { (share, each) ->
                Box(
                    Modifier
                        .weight((share / total).coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(tileColourOf(each)),
                )
            }
        }

        // The same arcs the widget draws, drawn natively. Compose can make a
        // curve; Glance cannot, which is why the widget pays for a bitmap
        // and this does not.
        is Chart.Arcs -> Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            val side = kotlin.math.min(size.width, size.height)
            val strokePx = side * 0.115f
            val gapPx = side * 0.04f
            var inset = strokePx / 2f
            arcColours.forEachIndexed { index, colour ->
                val box = side - inset * 2f
                if (box <= strokePx) return@forEachIndexed
                val topLeft = Offset((size.width - side) / 2f + inset, inset)
                val arcSize = Size(box, box)
                drawArc(
                    color = trackColour,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                val swept = chart.parts[index].first.coerceIn(0f, 1f) * 360f
                if (swept > 0f) {
                    drawArc(
                        color = colour,
                        startAngle = -90f,
                        sweepAngle = swept.coerceAtMost(359.9f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
                inset += strokePx + gapPx
            }
        }

        // Three readings will not fit under a figure at this size, so the
        // thumbnail shows the two that do and lets the widget show the rest.
        is Chart.Rows -> Column(Modifier.fillMaxWidth()) {
            chart.rows.take(2).forEach { (name, value, each) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tileColourOf(each),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One health colour, in whichever mode is on. */
@Composable
internal fun tileColourOf(colour: TileColour): Color {
    val extras = MaterialTheme.extras
    return when (colour) {
        TileColour.Heart -> extras.heart
        TileColour.Sleep -> extras.sleepRem
        TileColour.Steps -> extras.steps
        TileColour.Hrv -> extras.hrv
        TileColour.Activity -> extras.activity
        TileColour.Energy -> extras.energy
        TileColour.Slate -> extras.disabled
        TileColour.Accent -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun stageColourOf(stage: SleepStage): Color {
    val extras = MaterialTheme.extras
    return when (stage) {
        SleepStage.Deep -> extras.sleepDeep
        SleepStage.Light -> extras.sleepLight
        SleepStage.Rem -> extras.sleepRem
        SleepStage.Awake -> extras.sleepAwake
    }
}
