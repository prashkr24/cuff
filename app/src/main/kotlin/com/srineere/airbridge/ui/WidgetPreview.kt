package com.srineere.airbridge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
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
import com.srineere.airbridge.widget.PaperInks
import com.srineere.airbridge.widget.RingBitmap

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
internal fun WidgetPreview(
    face: Face,
    colour: TileColour,
    modifier: Modifier = Modifier,
    /** True for a family that paints no card, so the thumbnail paints none either. */
    cardless: Boolean = false,
    /** The light half, for the family that is always pale. */
    paper: PaperInks? = null,
    /**
     * False for the Tiles family, which borrows the tab's mixed-case head.
     *
     * The catalogue is a promise about what will land on the home screen,
     * so a thumbnail that shouts a name the widget writes in mixed case is
     * a thumbnail of a different widget.
     */
    caps: Boolean = true,
) {
    val tint = paper?.tile?.invoke(colour) ?: tileColourOf(colour)
    // No border where the card would be, dashed or otherwise. The design
    // sheets drew one to say "these are the widget's bounds", but a sheet
    // is a diagram and this is the thing itself: on a home screen there is
    // nothing there, and the absence is the whole point of the family.
    Surface(
        color = when {
            cardless -> Color.Transparent
            paper != null -> paper.fill
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(78.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (caps) face.kicker.uppercase() else face.kicker,
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
                    color = paper?.ink ?: MaterialTheme.colorScheme.onSurface,
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
                        color = paper?.muted ?: MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = paper?.muted ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    MiniChart(face.chart, colour, paper)
                }
            }
        }
    }
}

@Composable
private fun MiniChart(chart: Chart?, colour: TileColour, paper: PaperInks? = null) {
    val tint = paper?.tile?.invoke(colour) ?: tileColourOf(colour)
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
            val top = chart.ceiling ?: chart.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
            // The newest reading, not the biggest one. The widget picks out
            // the last bar; a preview that picked out the tallest would be
            // highlighting a different day from the thing it is a picture of.
            val newest = if (chart.highlightLast) chart.values.indexOfLast { it > 0f } else -1
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

        // A week of nights, each as tall as the night was. Not normalised,
        // for the reason the widget gives: a six hour night and a nine hour
        // one are different nights.
        is Chart.StageWeek -> Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().height(22.dp),
        ) {
            val longest = chart.nights
                .maxOfOrNull { night -> night.sumOf { it.first.toDouble() } }
                ?.toFloat()?.takeIf { it > 0f } ?: 1f
            chart.nights.forEach { night ->
                val total = night.sumOf { it.first.toDouble() }.toFloat()
                Column(
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Spacer(Modifier.weight((1f - total / longest).coerceAtLeast(0.001f)))
                    night.reversed().forEach { (share, stage) ->
                        Box(
                            Modifier
                                .weight(((share / longest)).coerceAtLeast(0.01f))
                                .fillMaxWidth()
                                .background(stageColourOf(stage)),
                        )
                    }
                }
            }
        }

        // Where each reading sits across its own range, over the band it
        // usually lands in. The thumbnail draws three of the five rails:
        // at this height any more is a grey smear.
        is Chart.Rails -> BandRows(
            rails = chart.rails.take(3).map { (trend, each) -> trend?.rail(tileColourOf(each)) },
            track = trackColour,
            modifier = Modifier.fillMaxWidth(),
            rowHeight = 3.dp,
            gap = 3.dp,
        )

        // A day of beats. The thumbnail keeps the shape and drops the
        // scale, which is what every other chart here does too.
        is Chart.Beats -> {
            val bands = bandsOf(chart.samples, count = 40)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.fillMaxWidth().height(22.dp),
            ) {
                val lowest = bands.minOfOrNull { band -> band.lowest }?.toFloat() ?: 0f
                val highest = bands.maxOfOrNull { band -> band.highest }?.toFloat() ?: 1f
                val span = (highest - lowest).coerceAtLeast(1f)
                bands.forEach { band ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(
                                top = 22.dp * (1f - (band.highest - lowest) / span),
                                bottom = 22.dp * ((band.lowest - lowest) / span),
                            )
                            .clip(RoundedCornerShape(1.dp))
                            .background(tint),
                    )
                }
            }
        }

        // Two climbs against each other. The thumbnail keeps the pair and
        // drops the shading, which at this height would be one more line.
        // Two lines with the gap shaded between them, because that is what
        // lands on the home screen. Drawn as bars, this thumbnail sold a
        // chart the widget does not have: a climb is one unbroken line and
        // a column chart is twenty-four separate readings.
        is Chart.Climb -> {
            val reach = maxOf(
                chart.first.maxOrNull() ?: 0f,
                chart.second.maxOrNull() ?: 0f,
            ).coerceAtLeast(1f)
            val top = reach * CLIMB_HEADROOM
            val burnInk = tileColourOf(chart.firstColour)
            val foodInk = tileColourOf(chart.secondColour)
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val count = maxOf(chart.first.size, chart.second.size)
                if (count < 2) return@Canvas
                val span = (count - 1).toFloat()
                val dot = 2f.dp.toPx()
                fun at(index: Int) = (size.width - dot) * (index / span)
                fun up(value: Float) = size.height - size.height * (value / top)

                if (chart.first.size == chart.second.size) {
                    val area = Path().apply {
                        moveTo(at(0), up(chart.first[0]))
                        chart.first.indices.forEach { lineTo(at(it), up(chart.first[it])) }
                        for (i in chart.second.indices.reversed()) {
                            lineTo(at(i), up(chart.second[i]))
                        }
                        close()
                    }
                    drawPath(
                        area,
                        (if (chart.second.last() < chart.first.last()) burnInk else foodInk)
                            .copy(alpha = 0.14f),
                    )
                }
                listOf(chart.first to burnInk, chart.second to foodInk).forEach { (values, ink) ->
                    if (values.size < 2) return@forEach
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val px = at(index)
                        val py = up(value)
                        when {
                            index == 0 -> path.moveTo(px, py)
                            // The eaten line steps, here as everywhere.
                            values === chart.second -> {
                                path.lineTo(px, up(values[index - 1]))
                                path.lineTo(px, py)
                            }
                            else -> path.lineTo(px, py)
                        }
                    }
                    drawPath(path, ink, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(ink, radius = dot, center = Offset(at(values.lastIndex), up(values.last())))
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

        // The tile's own scatter, drawn natively. Compose can place a dot;
        // Glance cannot, which is why the widget pays for a bitmap and this
        // does not. Same quarter-span padding, same twelve per cent band.
        is Chart.Scatter -> Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val values = chart.values
            if (values.isEmpty()) return@Canvas
            val lowest = values.min()
            val highest = values.max()
            val span = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
            val low = lowest - span * 0.25
            val high = highest + span * 0.25
            fun y(value: Double) = (size.height - (value - low) / (high - low) * size.height).toFloat()
            if (chart.low != null && chart.high != null) {
                val top = y(chart.high)
                drawRect(
                    color = tint.copy(alpha = 0.12f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, (y(chart.low) - top).coerceAtLeast(1f)),
                )
            }
            val radius = 1.8.dp.toPx()
            val usable = (size.width - radius * 2).coerceAtLeast(1f)
            values.forEachIndexed { index, value ->
                val last = index == values.lastIndex
                drawCircle(
                    color = if (last) tint else tint.copy(alpha = 0.8f),
                    radius = if (last) radius * 1.5f else radius,
                    center = Offset(
                        if (values.size <= 1) size.width / 2f
                        else radius + usable * index / (values.size - 1f),
                        y(value),
                    ),
                )
            }
        }

        // The same arcs the widget draws, drawn natively. Compose can make a
        // curve; Glance cannot, which is why the widget pays for a bitmap
        // and this does not.
        is Chart.Arcs -> Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            val side = kotlin.math.min(size.width, size.height)
            // The widget's own numbers, not a second set: a thumbnail whose
            // rings are a different thickness is a thumbnail of a different
            // widget.
            val strokePx = side * RingBitmap.STROKE
            val gapPx = side * RingBitmap.GAP
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

        // One arc each, side by side, each with its figure inside. The
        // widget adds a label under every figure; a thumbnail has no room
        // for three of those and the figure is the part that carries.
        is Chart.Dials -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            chart.parts.forEach { dial ->
                val tint = tileColourOf(dial.colour)
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val side = kotlin.math.min(size.width, size.height)
                        val strokePx = side * DIAL_PREVIEW_STROKE
                        val box = side - strokePx
                        val topLeft = Offset(
                            (size.width - side) / 2f + strokePx / 2f,
                            (size.height - side) / 2f + strokePx / 2f,
                        )
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
                        val swept = dial.fraction.coerceIn(0f, 1f) * 360f
                        if (swept > 0f) {
                            drawArc(
                                color = tint,
                                startAngle = -90f,
                                sweepAngle = swept.coerceAtMost(359.9f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Text(
                        dial.centre,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = if (chart.parts.size > 1) 11.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Across, with a hairline between: the one picture that tells the
        // Rail widget apart from the Day widget, which holds the same four
        // readings and is nothing like it.
        is Chart.Rail -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chart.parts.forEachIndexed { index, (name, value, each) ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(paper?.edge ?: MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 7.5.sp,
                        color = paper?.muted ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = paper?.ink ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        color = paper?.muted ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = paper?.tile?.invoke(each) ?: tileColourOf(each),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A dial is one arc with room to spare, so it is drawn thicker than a ring. */
private const val DIAL_PREVIEW_STROKE = 0.16f

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

/** The tab's headroom above a climb, so the thumbnail is scaled as it is. */
private const val CLIMB_HEADROOM = 1.08f
