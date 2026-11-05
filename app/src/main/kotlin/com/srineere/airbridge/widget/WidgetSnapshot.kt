package com.srineere.airbridge.widget

import android.content.Context
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthGoals
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.Tile
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.HealthStage
import com.srineere.airbridge.ui.loadHealthData

/**
 * One read, for however many widgets are on the home screen.
 *
 * Reading per widget would mean four Health Connect reads for four widgets,
 * each a little out of step with the last, and four battery rows dated
 * minutes apart -- which looks broken even though every figure is true.
 */
data class WidgetSnapshot(
    val health: HealthData = HealthData(),
    val band: BandFacts = BandFacts(),
    val goals: Goals = Goals(),
    val tiles: List<Tile> = emptyList(),
    val showTrends: Boolean = true,
    val readAt: Long = 0,
) {
    val granted: Boolean get() = health.stage == HealthStage.Granted

    /**
     * The colour this kind is drawn in, which is the tile's, not the
     * widget's.
     *
     * A widget with a colour setting of its own would be a second answer to
     * a question the Health tab has already answered, and the two would
     * disagree the first time someone recoloured a tile. Edit tiles moves
     * both, or it moves neither.
     */
    fun colourFor(kind: TileKind): TileColour =
        tiles.firstOrNull { it.kind == kind }?.colour ?: kind.defaultColour
}

/**
 * Everything the widgets need, read off the main thread.
 *
 * The tab's thirty days, and it has to be. A fortnight was the old answer,
 * on the premise that no widget face drew longer than one -- but the Heart
 * and Weight faces head themselves "30 days" and the three nightly vitals
 * "30 nights", exactly as their tiles do, so the premise was already false.
 * What the shorter window actually bought was a tile and a widget side by
 * side with the same heading over different arithmetic: a usual band and a
 * change chip from two weeks, labelled as a month.
 *
 * It costs a wider read every half hour. The alternative was to relabel
 * five faces away from the tiles they exist to mirror.
 */
internal suspend fun readWidgetSnapshot(context: Context, days: Int = WIDGET_WINDOW_DAYS): WidgetSnapshot =
    WidgetSnapshot(
        health = loadHealthData(context, days = days),
        // Never throws the whole snapshot away: a band fact that cannot be
        // read should cost the band widget its figure, not the steps widget
        // its chart.
        band = runCatching { BandStore.read(context) }.getOrDefault(BandFacts()),
        goals = runCatching { HealthGoals.read(context) }.getOrDefault(Goals()),
        tiles = runCatching { HealthSettings.tiles(context) }.getOrDefault(emptyList()),
        showTrends = runCatching { HealthSettings.showTrends(context) }.getOrDefault(true),
        readAt = System.currentTimeMillis(),
    )

internal const val WIDGET_WINDOW_DAYS = 30
