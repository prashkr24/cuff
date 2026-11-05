package com.srineere.airbridge.widget

import com.srineere.airbridge.health.Tile
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.sampleGoals
import com.srineere.airbridge.ui.sampleHealthData

/**
 * A band face reduced to the same shape a tile's is.
 *
 * So that the catalogue can draw one preview composable for both. The
 * widgets themselves still draw band faces by hand -- they have states a
 * [Face] has no room for, like a charge that has gone stale -- but a
 * thumbnail needs a kicker, a figure and a line, which is exactly this.
 */
internal fun bandFace(face: BandFace, band: BandFacts, now: Long): Face {
    val charge = band.battery?.let { "$it%" } ?: "–"
    return when (face) {
        BandFace.Everything -> Face(
            kicker = "Band",
            figure = charge,
            sub = stateWordFor(band, now),
            meter = band.battery?.div(100f),
        )
        BandFace.Battery -> Face(
            kicker = "Battery",
            figure = charge,
            sub = if (band.batteryAt == 0L) "not checked yet" else "read " + ago(band.batteryAt, now),
            meter = band.battery?.div(100f),
        )
        BandFace.LastBuzz -> Face(
            kicker = "Last buzz",
            figure = band.lastBuzzWho
                ?: if (band.lastBuzzAt == 0L) "–" else if (band.lastBuzzWasCall) "A call" else "A message",
            sub = if (band.lastBuzzAt == 0L) "nothing yet" else ago(band.lastBuzzAt, now),
        )
        BandFace.Relay -> Face(
            kicker = "Relay",
            figure = stateWordFor(band, now),
            sub = if (band.quietEnabled) "quiet from " + clock(band.quietStartMinute) else "no quiet hours",
        )
        BandFace.Quiet -> Face(
            kicker = "Quiet",
            figure = clock(band.quietStartMinute),
            sub = "→ " + clock(band.quietEndMinute),
        )
        BandFace.Buzzes -> Face(
            kicker = "Today",
            figure = "${band.buzzesToday}",
            sub = if (band.buzzesToday == 1) "buzz" else "buzzes",
        )
    }
}

private fun stateWordFor(band: BandFacts, now: Long): String = when (band.state(now)) {
    BandState.Relaying -> "Relaying"
    BandState.Quiet -> "Quiet"
    BandState.Paused -> "Paused"
    BandState.Off -> "Not relaying"
}

/**
 * A made-up person's readings, for the catalogue.
 *
 * The same sample the tile editor draws, and for the same reason: a
 * catalogue is about what a widget *is*, and a column of dashes on a phone
 * that has not synced yet says nothing about that. It also means the screen
 * costs no Health Connect read to open.
 *
 * [tiles] is the one real thing passed in, because the colours are the
 * person's own -- a preview in somebody else's mint would be a preview of
 * somebody else's widget.
 */
internal fun sampleWidgetSnapshot(tiles: List<Tile>): WidgetSnapshot = WidgetSnapshot(
    health = sampleHealthData(),
    band = SAMPLE_BAND,
    goals = sampleGoals,
    tiles = tiles,
    showTrends = true,
    readAt = System.currentTimeMillis(),
)

private val SAMPLE_BAND = BandFacts(
    battery = 72,
    batteryAt = System.currentTimeMillis() - 2 * 60 * 60 * 1000,
    relaying = true,
    quietEnabled = true,
    quietStartMinute = 22 * 60,
    quietEndMinute = 7 * 60,
    lastBuzzAt = System.currentTimeMillis() - 2 * 60 * 1000,
    lastBuzzWho = "Priya",
    lastBuzzWasCall = true,
    buzzesToday = 14,
)

/** The colour a design is drawn in, for a preview that has no widget yet. */
internal fun WidgetSnapshot.colourFor(design: WidgetDesign): TileColour = when (design) {
    is WidgetDesign.OfTile -> colourFor(design.kind)
    is WidgetDesign.OfBand -> TileColour.Accent
    is WidgetDesign.OfRings -> when (design.face) {
        RingFace.Arcs -> TileColour.Steps
        RingFace.Summary, RingFace.Trends -> TileColour.Slate
    }
}

/** The reading a design shows, whichever half of the app it comes from. */
internal fun faceOf(design: WidgetDesign, snapshot: WidgetSnapshot, now: Long): Face? = when (design) {
    is WidgetDesign.OfTile -> faceFor(design.kind, snapshot)
    is WidgetDesign.OfBand -> bandFace(design.face, snapshot.band, now)
    is WidgetDesign.OfRings -> ringsFace(design.face, snapshot)
}

/**
 * A rings face as a [Face], for the thumbnail.
 *
 * The arcs face has three readings and no single figure, so its figure is
 * empty and the preview gives the whole card to the arcs -- which is what
 * the Square widget does too.
 */
private fun ringsFace(face: RingFace, snapshot: WidgetSnapshot): Face? = when (face) {
    RingFace.Arcs -> ringsOf(snapshot.health, snapshot.goals)
        .takeIf { it.isNotEmpty() }
        ?.let { rings ->
            Face(
                kicker = "Today",
                figure = "",
                sub = rings.joinToString(" · ") { it.label.lowercase() },
                chart = Chart.Arcs(rings.map { it.fraction to it.colour }),
            )
        }
    RingFace.Summary -> summaryRows(snapshot.health).takeIf { it.isNotEmpty() }?.let {
        Face(kicker = "Summary", figure = "", sub = "", chart = Chart.Rows(it))
    }
    RingFace.Trends -> trendRows(snapshot.health).takeIf { it.isNotEmpty() }?.let {
        Face(kicker = "Trends", figure = "", sub = "", chart = Chart.Rows(it))
    }
}
