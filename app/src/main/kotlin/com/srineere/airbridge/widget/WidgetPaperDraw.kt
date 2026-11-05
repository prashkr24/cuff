package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.srineere.airbridge.R
import com.srineere.airbridge.ui.asHoursMinutes

/**
 * A pale card, which is the one thing [WidgetCard] cannot give this family.
 *
 * That one takes its fill from whichever half of the palette the launcher
 * resolves; this one is always the light half, because a pale card is the
 * whole premise of Paper.
 */
@Composable
private fun PaperCard(
    colours: WidgetColours,
    onClick: Action?,
    padding: Dp = 14.dp,
    fill: ColorProvider = colours.paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = GlanceModifier
        .fillMaxSize()
        .background(
            imageProvider = ImageProvider(R.drawable.widget_card),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(fill),
        )
        .padding(padding)
    Column(modifier = if (onClick != null) card.clickable(onClick) else card, content = content)
}

@Composable
internal fun PaperWidget(
    face: PaperFace,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    if (!snapshot.granted) {
        PaperCard(colours, onClick) {
            Sub("Air Bridge", colours.paperMuted, size = 10)
            Grow()
            Sub("Open the app to connect Health Connect", colours.paperMuted, size = 11, lines = 3)
        }
        return
    }
    when (face) {
        PaperFace.Figure -> FigureFace(size, snapshot, colours, onClick)
        PaperFace.Sentence -> SentenceFace(size, snapshot, colours, onClick)
        PaperFace.Rail -> RailFace(snapshot, colours, onClick)
        PaperFace.Day -> DayFace(snapshot, colours, onClick)
    }
}

@Composable
private fun FigureFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val steps = snapshot.health.snapshot.stepsToday
    val goal = snapshot.goals.steps
    PaperCard(colours, onClick, padding = if (size == WidgetSize.Strip) 11.dp else 14.dp) {
        Sub("Steps", colours.paperMuted, size = 10)
        Grow()
        Figure(
            steps?.let { "%,d".format(it) } ?: "–",
            when (size) {
                WidgetSize.Strip -> 24
                WidgetSize.Square -> 34
                else -> 46
            },
            colours.paperInk,
        )
        if (size != WidgetSize.Strip && goal > 0 && steps != null) {
            Gap(8.dp)
            Sub(
                if (steps >= goal) "past %,d".format(goal) else "%,d to go".format(goal - steps),
                colours.paperMuted,
                size = 11,
            )
        }
    }
}

@Composable
private fun SentenceFace(
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
) {
    val night = snapshot.health.lastNight
    val ring = paperRing(snapshot.health, snapshot.goals)
    PaperCard(colours, onClick) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ring != null) {
                // Forced light, because the card is light. Left to resolve
                // itself the arc would come out of the dark half at night
                // and draw a ring nobody can see on its own card.
                RingImage(listOf(ring), colours, RING_SIDE, stroke = 0.1f, light = true)
                Spacer(GlanceModifier.width(14.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Sub("Last night", colours.paperMuted, size = 10)
                Gap(5.dp)
                Figure(
                    night?.asleep?.asHoursMinutes() ?: "–",
                    if (size == WidgetSize.Tall) 34 else 28,
                    colours.paperInk,
                )
            }
        }
        Gap(if (size == WidgetSize.Tall) 16.dp else 10.dp)
        Sub(
            paperSentence(snapshot.health, snapshot.goals),
            colours.paperMuted,
            size = if (size == WidgetSize.Tall) 13 else 11,
            lines = if (size == WidgetSize.Tall) 4 else 2,
        )
    }
}

@Composable
private fun RailFace(snapshot: WidgetSnapshot, colours: WidgetColours, onClick: Action?) {
    val readings = paperReadings(snapshot.health, snapshot.goals)
    if (readings.isEmpty()) {
        PaperCard(colours, onClick) { Sub("Nothing read yet", colours.paperMuted, size = 11) }
        return
    }
    PaperCard(colours, onClick, padding = 12.dp) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            readings.forEachIndexed { index, reading ->
                // A hairline, not a gap: the rail is the one face here with
                // no colour in it at all, so the division has to come from
                // somewhere.
                if (index > 0) Hairline(colours)
                Column(modifier = GlanceModifier.defaultWeight().padding(horizontal = 9.dp)) {
                    Sub(reading.label, colours.paperMuted, size = 9)
                    Gap(4.dp)
                    Figure(reading.figure, 17, colours.paperInk)
                }
            }
        }
    }
}

@Composable
private fun Hairline(colours: WidgetColours) {
    Box(
        modifier = GlanceModifier
            .width(1.dp)
            .height(26.dp)
            .background(
                imageProvider = ImageProvider(R.drawable.widget_bar),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colours.paperEdge),
            ),
    ) {}
}

@Composable
private fun DayFace(snapshot: WidgetSnapshot, colours: WidgetColours, onClick: Action?) {
    val readings = paperReadings(snapshot.health, snapshot.goals)
    if (readings.isEmpty()) {
        PaperCard(colours, onClick) { Sub("Nothing read yet", colours.paperMuted, size = 11) }
        return
    }
    PaperCard(colours, onClick) {
        Figure("Today", 16, colours.paperInk)
        Gap(12.dp)
        readings.chunked(2).forEachIndexed { row, pair ->
            if (row > 0) Gap(10.dp)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                pair.forEachIndexed { index, reading ->
                    if (index > 0) Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) { Pane(reading, colours) }
                }
                // An odd reading keeps its half rather than stretching
                // across a card built for two.
                if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
            }
        }
        Grow()
        Sub(paperSentence(snapshot.health, snapshot.goals), colours.paperMuted, size = 11, lines = 3)
    }
}

/** One soft pane inside the Day face: a dot, a word, a figure, a line. */
@Composable
private fun Pane(reading: PaperReading, colours: WidgetColours) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                imageProvider = ImageProvider(R.drawable.widget_card),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colours.paperPage),
            )
            .padding(11.dp),
    ) {
        Dot(colours.paperTile(reading.colour), size = 7.dp)
        Gap(7.dp)
        Sub(reading.label, colours.paperMuted, size = 9)
        Gap(4.dp)
        Figure(reading.figure, 20, colours.paperInk)
        Gap(3.dp)
        Sub(reading.detail, colours.paperMuted, size = 9)
    }
}

private val RING_SIDE = 66.dp
