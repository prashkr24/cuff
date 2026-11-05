package com.srineere.airbridge.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.srineere.airbridge.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * One widget class behind five providers.
 *
 * The launcher's picker gets one entry per cell size and this draws whatever
 * that cell was told to show. Five [GlanceAppWidget]s would be five copies
 * of the same dispatch; what actually differs between the sizes lives in
 * `res/xml` and nowhere else.
 */
class AirWidget : GlanceAppWidget() {

    /**
     * Exact, which costs a recomposition per resize and is worth it.
     *
     * Responsive looked cheaper: pre-render the five declared sizes and let
     * the launcher pick. But then [LocalSize] is the size we *declared*, not
     * the cell we were given, and launchers hand out cells that are nothing
     * like either -- a 4x4 here came back as a card half again as tall as
     * the 300dp the layout had been measured for. The RemoteViews stretches,
     * the composition does not, and the result is a widget with its content
     * crushed into the top third and a field of empty card beneath it.
     *
     * Exact reports the real cell, so every height below can be arithmetic
     * on it rather than a constant that is right at one size and wrong at
     * the rest.
     */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetStore.read(context, appWidgetId)
        val snapshot = WidgetSnapshotCache.get(context)
        val colours = widgetColours(context)
        val now = System.currentTimeMillis()
        provideContent { Body(appWidgetId, config, snapshot, colours, now) }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        // The row would otherwise outlive the widget, and Your widgets would
        // list something the home screen no longer has.
        WidgetStore.remove(context, intArrayOf(GlanceAppWidgetManager(context).getAppWidgetId(glanceId)))
        super.onDelete(context, glanceId)
    }

    @Composable
    private fun Body(
        appWidgetId: Int,
        config: WidgetConfig?,
        snapshot: WidgetSnapshot,
        colours: WidgetColours,
        now: Long,
    ) {
        val size = LocalSize.current
        val cell = WidgetSize.nearest(size.width.value.roundToInt(), size.height.value.roundToInt())
        if (config == null) {
            // Either a restore, or a launcher that skipped the configuration
            // activity. Saying so beats drawing a guess: tapping opens the
            // catalogue, which is where the choice belongs.
            NeedsSetup(appWidgetId, colours, cell)
            return
        }
        val face = config.face(cell)
        val onClick = openFor(config)
        when (val design = config.design) {
            is WidgetDesign.OfTile -> TileWidget(design.kind, face, snapshot, colours, onClick)
            is WidgetDesign.OfBand -> BandWidget(design.face, face, snapshot, colours, onClick, now)
            is WidgetDesign.OfRings -> RingsWidget(design.face, face, snapshot, colours, onClick)
        }
    }

    @Composable
    private fun NeedsSetup(appWidgetId: Int, colours: WidgetColours, cell: WidgetSize) {
        val context = LocalContext.current
        Empty(
            colours,
            "Air Bridge",
            if (cell == WidgetSize.Strip) "Tap to choose" else "Tap to choose what this shows",
            // The id travels as an action parameter, which Glance puts in the
            // launching intent's extras under that key -- the same key the
            // launcher itself uses, so the activity reads one thing either way.
            actionStartActivity(
                ComponentName(context, WidgetConfigActivity::class.java),
                actionParametersOf(AppWidgetIdKey to appWidgetId),
            ),
        )
    }
}

/**
 * Where a tap lands.
 *
 * A widget opens the screen about the thing it is drawing, which is the one
 * place every number on it is explained. The Health tab is offered as an
 * alternative because someone with four widgets may want the tab rather than
 * four different screens.
 */
private fun openFor(config: WidgetConfig): Action {
    val route = when (val design = config.design) {
        is WidgetDesign.OfTile -> if (config.opensTab) null else design.kind.route
        is WidgetDesign.OfBand -> if (config.opensTab) null else DEVICE_ROUTE
        // Several readings at once, so there is no one screen behind them.
        is WidgetDesign.OfRings -> null
    }
    return actionStartActivity(
        MainActivity::class.java,
        if (route == null) actionParametersOf() else actionParametersOf(WidgetRouteKey to route),
    )
}

internal const val DEVICE_ROUTE = "device"

/** Carried into MainActivity as an extra, so a tap lands on the right screen. */
internal val WidgetRouteKey = ActionParameters.Key<String>(ROUTE_EXTRA)

/** The launcher's own key for a widget id, reused so the activity reads one thing. */
internal val AppWidgetIdKey = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)

const val ROUTE_EXTRA = "air_route"

/**
 * One read for however many widgets are on the screen.
 *
 * [GlanceAppWidget.provideGlance] runs once per widget, so four widgets
 * would mean four Health Connect reads a minute apart, four battery rows
 * dated differently, and four charts that disagree about today -- all of
 * them true, and the set of them obviously broken. They share one read
 * instead, for as long as it is fresh.
 */
internal object WidgetSnapshotCache {
    private val mutex = Mutex()
    private var held: WidgetSnapshot? = null

    suspend fun get(context: Context, maxAge: Long = FRESH_MILLIS): WidgetSnapshot = mutex.withLock {
        val existing = held
        if (existing != null && System.currentTimeMillis() - existing.readAt < maxAge) return existing
        readWidgetSnapshot(context).also { held = it }
    }

    /** Called when something the widgets draw has certainly changed. */
    fun invalidate() {
        held = null
    }

    /** Long enough that a screen of widgets costs one read, short enough to feel current. */
    private const val FRESH_MILLIS = 60_000L
}

/**
 * Redraws every placed widget, after a theme change or a fresh read.
 *
 * By id, not by `updateAll`. `updateAll` resolves its targets through
 * Glance's own receiver index, which is built when a receiver has run in
 * this process -- so immediately after the configuration activity places the
 * first widget it finds nothing, and the widget sits on the home screen
 * still showing the "tap to choose" face it composed before the choice was
 * stored. The launcher knows exactly which ids exist; ask it.
 */
internal suspend fun refreshWidgets(context: Context, reread: Boolean = true) {
    if (reread) WidgetSnapshotCache.invalidate()
    val manager = GlanceAppWidgetManager(context)
    val widget = AirWidget()
    liveIds(context).forEach { id ->
        // One failure is one widget, not all of them: an id the launcher
        // reported and has since dropped must not stop the rest redrawing.
        runCatching { widget.update(context, manager.getGlanceIdBy(id)) }
    }
}

/** Redraws one, which is what placing or reconfiguring a widget needs. */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int) {
    runCatching {
        AirWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
    }
}

// --------------------------------------------------------- the five sizes

/**
 * One receiver per cell, and nothing in any of them.
 *
 * They exist because an `appwidget-provider` is declared per receiver, and
 * the only thing that differs between the five is that XML: the cell it
 * asks the launcher for, and the description in the picker.
 */
class StripWidgetReceiver : AirWidgetReceiver()
class SquareWidgetReceiver : AirWidgetReceiver()
class RowWidgetReceiver : AirWidgetReceiver()
class WideWidgetReceiver : AirWidgetReceiver()
class TallWidgetReceiver : AirWidgetReceiver()

abstract class AirWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = AirWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetStore.remove(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // The first widget of any size is what turns the periodic read on.
        WidgetRefresh.schedule(context)
    }
}
