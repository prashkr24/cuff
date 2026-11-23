package com.srineere.airbridge.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.srineere.airbridge.health.TileColour
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.height
import androidx.glance.background
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight

/**
 * The half of this app no health app can draw.
 *
 * Every figure here is the last one the app itself read, never a fresh one:
 * the Air answers one BLE session at a time and Google Health holds that
 * session whenever it syncs, so a widget that asked the band every half hour
 * would quietly cost a day of steps and sleep. The age of a reading is on
 * the card beside it, and the figure dims once it is over a day old.
 */
@Composable
internal fun BandWidget(
    face: BandFace,
    size: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    val band = snapshot.band
    when (face) {
        BandFace.Everything -> Everything(size, band, colours, onClick, now)
        BandFace.Battery -> Battery(size, band, colours, onClick, now)
        BandFace.Relay -> Relay(size, band, colours, onClick, now)
        BandFace.Buzzes -> Buzzes(size, band, colours, onClick, now)
    }
}

// ------------------------------------------------------------------- state

@Composable
private fun stateColour(state: BandState, colours: WidgetColours): ColorProvider = when (state) {
    BandState.Relaying -> colours.positive
    BandState.Quiet -> colours.accent
    BandState.Paused -> colours.faint
    BandState.Off -> colours.tile(TileColour.Heart)
}

private fun stateWord(state: BandState, band: BandFacts): String = when (state) {
    BandState.Relaying -> "Relaying"
    BandState.Quiet -> "Quiet until " + clock(band.quietEndMinute)
    BandState.Paused -> if (band.pauseUntil > 0) "Paused until " + clockOf(band.pauseUntil) else "Paused"
    BandState.Off -> "Not relaying"
}


// --------------------------------------------------------------- the faces

/**
 * The state, the charge and the last buzz, in that order.
 *
 * Three children, not eleven: a Glance container keeps ten and drops the
 * rest in silence, so the card above the rule and the footer under it are
 * each one Column rather than a run of siblings.
 */
@Composable
private fun Everything(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    val tall = size == WidgetSize.Tall
    val padding = 14.dp
    val state = band.state(now)
    val stale = band.batteryStale(now)
    WidgetCard(colours, onClick, padding) {
        Column {
            // The state leads, in its own colour, with the quiet window
            // after it: what the band is doing, and what will change it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(stateColour(state, colours), size = 8.dp)
                Spacer(GlanceModifier.width(7.dp))
                Kicker(
                    stateWord(state, band),
                    stateColour(state, colours),
                    size = if (tall) WidgetText.Body else WidgetText.Label,
                    caps = false,
                )
                if (band.quietEnabled) {
                    Sub(
                        " · quiet from " + clock(band.quietStartMinute),
                        colours.faint,
                        size = if (tall) WidgetText.Body else WidgetText.Label,
                    )
                }
            }
            Gap(if (tall) 16.dp else 12.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                Figure(
                    band.battery?.let { "$it%" } ?: "–",
                    if (tall) WidgetText.FigureMax else WidgetText.FigureXL,
                    if (stale) colours.faint else colours.onBackground,
                    weight = FontWeight.Bold,
                )
                Spacer(GlanceModifier.width(9.dp))
                // The band's name, and nothing about a connection: the app
                // knows when it last read the Air, not whether it is
                // reachable now, and the line under this says which.
                Sub("Air", colours.muted, size = WidgetText.Body)
            }
            Gap(9.dp)
            Meter(
                progress = (band.battery ?: 0) / 100f,
                width = innerWidth(padding),
                colour = if (stale) colours.faint else colours.accent,
                track = colours.track,
                height = if (tall) 6.dp else 5.dp,
            )
            Gap(7.dp)
            Sub(
                if (band.batteryAt == 0L) "Not read yet" else "Checked " + ago(band.batteryAt, now),
                colours.faint,
                size = WidgetText.Label,
            )
        }
        Grow()
        Column {
            Rule(colours)
            Gap(9.dp)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker("Last buzz", colours.faint, size = WidgetText.Caption)
                Spacer(GlanceModifier.defaultWeight())
                if (band.lastBuzzAt == 0L) {
                    // "nothing today" was wrong: the reading behind it is
                    // the newest verified buzz there has ever been.
                    Sub("nothing yet", colours.faint, size = WidgetText.Label)
                } else {
                    // Who, in the card's own ink, and what it was beside it
                    // in the muted one. The name is always there now:
                    // [buzzWho] falls through a caller to the app that
                    // carried the alert and then to the app's own word for
                    // its own buzzes, so this card no longer answers "who
                    // buzzed" with "Message".
                    band.lastBuzzWho?.let {
                        Sub(it, colours.onBackground, size = WidgetText.Body)
                        Spacer(GlanceModifier.width(7.dp))
                    }
                    Sub(
                        // A goal crossed is neither a call nor a message,
                        // and the name beside it has already said what it
                        // was, so the kind word goes rather than being
                        // wrong. For anything relayed it stays: how an
                        // alert arrived is the half this app knows.
                        listOfNotNull(
                            if (band.lastBuzzWasOwn) null
                            else if (band.lastBuzzWasCall) "Call" else "Message",
                            ago(band.lastBuzzAt, now),
                        ).joinToString(" · "),
                        colours.muted,
                        size = WidgetText.Label,
                    )
                }
            }
        }
    }
}

/** A hairline across the card, which is the only divider in this app. */
@Composable
private fun Rule(colours: WidgetColours) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                imageProvider = ImageProvider(com.srineere.airbridge.R.drawable.widget_bar),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(colours.outline),
            ),
    ) {}
}

@Composable
private fun Battery(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    if (band.battery == null) {
        Empty(colours, "Battery", "Open the app once to read the band", onClick)
        return
    }
    val padding = 11.dp
    WidgetCard(colours, onClick, padding) {
        when (size) {
            WidgetSize.Strip, WidgetSize.Row -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BatteryFigure(band, colours, now, size = WidgetText.FigureM)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(readAge(band.batteryAt, now), colours.faint, size = WidgetText.Label)
                }
                Gap(7.dp)
                Meter(
                    progress = band.battery / 100f,
                    width = innerWidth(padding),
                    colour = if (band.batteryStale(now)) colours.faint else colours.accent,
                    track = colours.track,
                    height = 4.dp,
                )
            }
            else -> {
                Kicker("Battery", colours.accent)
                Grow()
                BatteryFigure(band, colours, now, size = WidgetText.FigureHero)
                Gap(9.dp)
                Meter(
                    progress = band.battery / 100f,
                    width = innerWidth(padding),
                    colour = if (band.batteryStale(now)) colours.faint else colours.accent,
                    track = colours.track,
                )
                Gap(7.dp)
                Sub("Air · " + readAge(band.batteryAt, now), colours.faint, size = WidgetText.Label)
            }
        }
    }
}

@Composable
private fun BatteryFigure(band: BandFacts, colours: WidgetColours, now: Long, size: Int) {
    val percent = band.battery
    Figure(
        if (percent == null) "–" else "$percent%",
        size,
        // Older than a day and the figure steps back. It is still the last
        // true reading, and it is no longer a statement about right now.
        if (percent == null || band.batteryStale(now)) colours.muted else colours.onBackground,
    )
}


@Composable
private fun Relay(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    val state = band.state(now)
    WidgetCard(colours, onClick) {
        when (size) {
            WidgetSize.Square -> {
                Kicker("Relay", colours.accent)
                Grow()
                Dot(stateColour(state, colours), size = 11.dp)
                Gap(9.dp)
                Figure(stateWord(state, band), 17, colours.onBackground)
                Grow()
                Sub(quietNote(band), colours.faint, size = WidgetText.Label)
            }
            else -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Dot(stateColour(state, colours), size = 8.dp)
                    Spacer(GlanceModifier.width(7.dp))
                    Figure(stateWord(state, band), if (size == WidgetSize.Row) WidgetText.FigureS else WidgetText.FigureXS, colours.onBackground)
                    if (size == WidgetSize.Row) {
                        Spacer(GlanceModifier.defaultWeight())
                        Sub(quietNote(band), colours.muted, size = WidgetText.Label)
                    }
                }
                if (size == WidgetSize.Strip) {
                    Gap(3.dp)
                    Sub(quietNote(band), colours.faint, size = WidgetText.Caption)
                }
            }
        }
    }
}


/**
 * How many got through since midnight, and -- where there is room -- what
 * they were.
 *
 * The count on its own is a number nobody can check. "Fourteen" answers
 * how busy the wrist has been and nothing else; the question anybody
 * actually has in front of a home screen is which fourteen, and whether
 * the one they are waiting for was among them. So every size past a 2x2
 * spends its extra room on the list rather than on a bigger number.
 *
 * The list is attempts, not arrivals, while the figure above it is
 * arrivals only. They can disagree -- four rows under a count of two --
 * and that disagreement is the useful part: the two that failed are the
 * rows worth having a widget for. A buzz that worked already told you.
 */
@Composable
private fun Buzzes(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    val unit = if (band.buzzesToday == 1) "buzz today" else "buzzes today"
    WidgetCard(colours, onClick) {
        when (size) {
            // One line. The figure, and the last one, which is the single
            // most useful row of the list when only one will fit.
            WidgetSize.Row -> Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Figure("${band.buzzesToday}", WidgetText.FigureL, colours.onBackground)
                Spacer(GlanceModifier.width(6.dp))
                Sub("today", colours.faint, size = WidgetText.Label)
                Spacer(GlanceModifier.defaultWeight())
                band.recent.firstOrNull()?.let { last ->
                    Column(horizontalAlignment = Alignment.End) {
                        Sub(
                            last.who,
                            if (last.arrived) colours.onBackground else colours.muted,
                            size = WidgetText.Body,
                        )
                        Sub("last · " + clockOf(last.atMillis), colours.faint, size = WidgetText.Label)
                    }
                }
            }

            WidgetSize.Wide, WidgetSize.Tall -> {
                val tall = size == WidgetSize.Tall
                if (tall) {
                    Kicker("Today", colours.tile(TileColour.Hrv))
                    Gap(6.dp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Figure("${band.buzzesToday}", WidgetText.FigureXL, colours.onBackground)
                        Spacer(GlanceModifier.width(8.dp))
                        Sub(unit, colours.muted, size = WidgetText.Label)
                    }
                } else {
                    // Wide has two lines' worth of room in all, so the
                    // kicker shares the figure's line rather than taking
                    // one of its own.
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Kicker("Today", colours.tile(TileColour.Hrv))
                        Spacer(GlanceModifier.defaultWeight())
                        Figure("${band.buzzesToday}", WidgetText.FigureL, colours.onBackground)
                        Spacer(GlanceModifier.width(6.dp))
                        Sub("buzzes", colours.muted, size = WidgetText.Label)
                    }
                }
                Gap(if (tall) 10.dp else 8.dp)
                Hairline(colours.outline)
                Gap(if (tall) 4.dp else 2.dp)
                BuzzList(band, colours, now, rows = if (tall) 6 else 3)
            }

            WidgetSize.Square -> {
                Kicker("Today", colours.tile(TileColour.Hrv))
                Grow()
                Figure("${band.buzzesToday}", WidgetText.FigureHero, colours.onBackground)
                Gap(5.dp)
                Sub(unit, colours.muted, size = WidgetText.Label)
            }

            WidgetSize.Strip -> {
                Figure("${band.buzzesToday}", WidgetText.FigureL, colours.onBackground)
                Gap(4.dp)
                Sub(unit, colours.faint, size = WidgetText.Label)
            }
        }
    }
}

/**
 * The rows under the figure, newest first.
 *
 * A count of zero at seven in the morning is true and useless, and a card
 * that went blank every night until the first buzz would be a card people
 * take off the home screen. So the list runs past midnight: it says where
 * today ended and keeps going into yesterday, which is the only thing on
 * the card that is worth reading before breakfast.
 */
@Composable
private fun BuzzList(band: BandFacts, colours: WidgetColours, now: Long, rows: Int) {
    if (band.recent.isEmpty()) {
        BuzzLine("No buzzes yet", null, colours.faint, colours, rows)
        return
    }
    val midnight = startOfDay(now)
    // One line, between the last of today and the first of yesterday, and
    // only when there is something each side of it to separate.
    var ruled = band.recent.first().atMillis < midnight
    if (ruled) BuzzLine("Nothing since midnight", null, colours.faint, colours, rows)
    var drawn = if (ruled) 1 else 0
    band.recent.forEach { row ->
        if (drawn >= rows) return@forEach
        if (!ruled && row.atMillis < midnight) {
            ruled = true
            BuzzLine("Earlier", null, colours.faint, colours, rows)
            drawn++
            if (drawn >= rows) return@forEach
        }
        BuzzLine(
            text = row.who,
            at = row.atMillis,
            // A failed row steps back rather than shouting. It is still
            // the thing worth seeing, and a home screen is not a place to
            // be alarmed at from across a room.
            colour = if (row.arrived) colours.onBackground else colours.muted,
            colours = colours,
            rows = rows,
            failed = !row.arrived,
        )
        drawn++
    }
}

@Composable
private fun BuzzLine(
    text: String,
    at: Long?,
    colour: ColorProvider,
    colours: WidgetColours,
    rows: Int,
    failed: Boolean = false,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(if (rows > 3) 26.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sub(if (failed) "$text · failed" else text, colour, size = WidgetText.Body)
        Spacer(GlanceModifier.defaultWeight())
        if (at != null) Sub(clockOf(at), colours.faint, size = WidgetText.Label)
    }
}

/** The rule between the figure and the list. */
@Composable
private fun Hairline(colour: ColorProvider) {
    Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(colour))
}

/** Midnight this morning, which is where "today" starts for the count. */
private fun startOfDay(now: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = now
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

// ------------------------------------------------------------------- words

private fun quietNote(band: BandFacts): String =
    if (!band.quietEnabled) "No quiet hours" else "quiet from " + clock(band.quietStartMinute)

/** Minutes past midnight as a clock, which is how the rules store a window. */
internal fun clock(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)

private fun clockOf(atMillis: Long): String {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = atMillis }
    return "%02d:%02d".format(
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
    )
}

/**
 * How long ago, in the fewest words that are still true.
 *
 * Rounded down, never up: "2 hr ago" for something 2 hours 50 minutes old
 * would be a reading claiming to be newer than it is, which is the one
 * direction this must not err in.
 */
internal fun ago(atMillis: Long, now: Long): String {
    if (atMillis <= 0L) return "never"
    val minutes = ((now - atMillis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} hr ago"
        minutes < 60 * 24 * 2 -> "yesterday"
        else -> "${minutes / (60 * 24)} days ago"
    }
}

private fun readAge(atMillis: Long, now: Long): String =
    if (atMillis <= 0L) "not checked yet" else "read " + ago(atMillis, now)
