package com.srineere.airbridge.widget

import android.content.Context
import com.srineere.airbridge.health.FitnessProfile
import com.srineere.airbridge.health.FitnessProfileStore
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthGoals
import com.srineere.airbridge.health.Readiness
import com.srineere.airbridge.health.readinessToday
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
    /** Who the fitness age table is read for; nothing else uses it. */
    val profile: FitnessProfile = FitnessProfile(),
    /**
     * The morning's readiness, worked out once for the whole home screen.
     *
     * Here rather than in the face, because it is a hundred and twenty
     * days of arithmetic and two readiness cards must not do it twice --
     * nor disagree if one of them ran a minute later.
     */
    val readiness: Readiness? = null,
    val readAt: Long = 0,
    /** How far back this was read, so a wider need can notice a short one. */
    val days: Int = WIDGET_WINDOW_DAYS,
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
internal suspend fun readWidgetSnapshot(
    context: Context,
    days: Int = WIDGET_WINDOW_DAYS,
): WidgetSnapshot {
    // Held, because readiness needs it too and the whole point of this
    // object is that four widgets cost one read.
    val health = loadHealthData(context, days = days)
    return WidgetSnapshot(
        health = health,
        // Never throws the whole snapshot away: a band fact that cannot be
        // read should cost the band widget its figure, not the steps widget
        // its chart.
        band = runCatching { BandStore.read(context) }.getOrDefault(BandFacts()),
        goals = runCatching { HealthGoals.read(context) }.getOrDefault(Goals()),
        tiles = runCatching { HealthSettings.tiles(context) }.getOrDefault(emptyList()),
        profile = runCatching { FitnessProfileStore.read(context) }.getOrDefault(FitnessProfile()),
        readiness = runCatching { readinessToday(context, health.snapshot) }.getOrNull(),
        days = days,
        showTrends = runCatching { HealthSettings.showTrends(context) }.getOrDefault(true),
        readAt = System.currentTimeMillis(),
    )
}

internal const val WIDGET_WINDOW_DAYS = 30

/**
 * How far back the widgets actually need reading.
 *
 * Thirty days for everything else, and a hundred and twenty when a
 * readiness or fitness age card is on the home screen. Those two count
 * readings rather than days: at thirty, readiness would say "calibrating"
 * for ever and fitness age would miss a VO2 max that arrives fortnightly.
 *
 * Conditional rather than simply raising the constant, because the wider
 * read is paid for every half hour by every phone -- and only a phone with
 * one of these two on its home screen has anything to show for it.
 */
internal fun widgetWindowDays(context: Context): Int {
    val wants = runCatching {
        WidgetStore.placed(context).any { (_, config) ->
            config.designs.any { it is WidgetDesign.OfTile && it.kind in LONG_WINDOW }
        }
    }.getOrDefault(false)
    return if (wants) Readiness.WINDOW_DAYS else WIDGET_WINDOW_DAYS
}

private val LONG_WINDOW = setOf(TileKind.Readiness, TileKind.FitnessAge)
