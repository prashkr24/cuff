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
        BandFace.LastBuzz -> LastBuzz(size, band, colours, onClick, now)
        BandFace.Relay -> Relay(size, band, colours, onClick, now)
        BandFace.Quiet -> Quiet(size, band, colours, onClick, now)
        BandFace.Buzzes -> Buzzes(size, band, colours, onClick)
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

@Composable
private fun StateLine(band: BandFacts, colours: WidgetColours, now: Long, size: Int = 12) {
    val state = band.state(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(stateColour(state, colours), size = 7.dp)
        Spacer(GlanceModifier.width(6.dp))
        Sub(stateWord(state, band), colours.onBackground, size = size)
    }
}

// --------------------------------------------------------------- the faces

@Composable
private fun Everything(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    val tall = size == WidgetSize.Tall
    val padding = 12.dp
    WidgetCard(colours, onClick, padding) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Band", colours.accent)
            Spacer(GlanceModifier.defaultWeight())
            Sub(readAge(band.batteryAt, now), colours.faint, size = 9)
        }
        Gap(if (tall) 10.dp else 6.dp)
        StateLine(band, colours, now, size = if (tall) 13 else 12)
        Gap(if (tall) 12.dp else 8.dp)
        BatteryFigure(band, colours, now, size = if (tall) 44 else 32)
        Gap(8.dp)
        Meter(
            progress = (band.battery ?: 0) / 100f,
            width = innerWidth(padding),
            colour = if (band.batteryStale(now)) colours.faint else colours.accent,
            track = colours.track,
            height = if (tall) 6.dp else 5.dp,
        )
        Grow()
        val who = band.lastBuzzWho
        if (band.lastBuzzAt == 0L) {
            Sub("No buzz yet today", colours.faint, size = 10)
        } else {
            Sub(who ?: if (band.lastBuzzWasCall) "A call" else "A message", colours.onBackground, size = 13)
            Gap(2.dp)
            Sub(
                listOfNotNull(
                    if (band.lastBuzzWasCall) "Call" else "Message",
                    ago(band.lastBuzzAt, now),
                    "${band.buzzesToday} today",
                ).joinToString(" · "),
                colours.faint,
                size = 10,
            )
        }
    }
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
                    BatteryFigure(band, colours, now, size = 22)
                    Spacer(GlanceModifier.defaultWeight())
                    Sub(readAge(band.batteryAt, now), colours.faint, size = 10)
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
                BatteryFigure(band, colours, now, size = 38)
                Gap(9.dp)
                Meter(
                    progress = band.battery / 100f,
                    width = innerWidth(padding),
                    colour = if (band.batteryStale(now)) colours.faint else colours.accent,
                    track = colours.track,
                )
                Gap(7.dp)
                Sub("Air · " + readAge(band.batteryAt, now), colours.faint, size = 10)
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
private fun LastBuzz(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    if (band.lastBuzzAt == 0L) {
        Empty(colours, "Last buzz", "Nothing has reached the band yet", onClick)
        return
    }
    val who = band.lastBuzzWho ?: if (band.lastBuzzWasCall) "A call" else "A message"
    WidgetCard(colours, onClick) {
        when (size) {
            WidgetSize.Strip, WidgetSize.Row -> {
                Figure(who, 18, colours.onBackground)
                Gap(3.dp)
                Sub(ago(band.lastBuzzAt, now), colours.faint, size = 10)
            }
            else -> {
                Kicker("Last buzz", colours.tile(TileColour.Hrv))
                Grow()
                Figure(who, 22, colours.onBackground)
                Gap(4.dp)
                Sub(
                    if (band.lastBuzzWasCall) "Call · rang until answered" else "Message",
                    colours.muted,
                    size = 11,
                )
                Grow()
                Sub(ago(band.lastBuzzAt, now), colours.faint, size = 11)
            }
        }
    }
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
                Sub(quietNote(band), colours.faint, size = 10)
            }
            else -> {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Dot(stateColour(state, colours), size = 8.dp)
                    Spacer(GlanceModifier.width(7.dp))
                    Figure(stateWord(state, band), if (size == WidgetSize.Row) 17 else 14, colours.onBackground)
                    if (size == WidgetSize.Row) {
                        Spacer(GlanceModifier.defaultWeight())
                        Sub(quietNote(band), colours.muted, size = 11)
                    }
                }
                if (size == WidgetSize.Strip) {
                    Gap(3.dp)
                    Sub(quietNote(band), colours.faint, size = 9)
                }
            }
        }
    }
}

@Composable
private fun Quiet(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
    now: Long,
) {
    if (!band.quietEnabled) {
        Empty(colours, "Quiet hours", "Quiet hours are off", onClick)
        return
    }
    val quiet = band.quietNow(now)
    WidgetCard(colours, onClick) {
        if (size == WidgetSize.Row) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker(if (quiet) "Quiet until" else "Next quiet", colours.faint)
                Spacer(GlanceModifier.defaultWeight())
                Figure(
                    clock(if (quiet) band.quietEndMinute else band.quietStartMinute),
                    17,
                    colours.onBackground,
                )
                Spacer(GlanceModifier.width(7.dp))
                Sub(
                    "→ " + clock(if (quiet) band.quietStartMinute else band.quietEndMinute),
                    colours.muted,
                    size = 11,
                )
            }
        } else {
            Figure(clock(if (quiet) band.quietEndMinute else band.quietStartMinute), 24, colours.onBackground)
            Gap(4.dp)
            Sub(if (quiet) "Quiet until" else "Quiet from", colours.faint, size = 10)
        }
    }
}

@Composable
private fun Buzzes(
    size: WidgetSize,
    band: BandFacts,
    colours: WidgetColours,
    onClick: Action?,
) {
    WidgetCard(colours, onClick) {
        if (size == WidgetSize.Square) {
            Kicker("Today", colours.tile(TileColour.Hrv))
            Grow()
            Figure("${band.buzzesToday}", 40, colours.onBackground)
            Gap(5.dp)
            Sub(if (band.buzzesToday == 1) "buzz today" else "buzzes today", colours.muted, size = 11)
        } else {
            Figure("${band.buzzesToday}", 24, colours.onBackground)
            Gap(4.dp)
            Sub(if (band.buzzesToday == 1) "buzz today" else "buzzes today", colours.faint, size = 10)
        }
    }
}

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
