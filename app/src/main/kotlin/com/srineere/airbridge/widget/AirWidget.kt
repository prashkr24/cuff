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
import com.srineere.airbridge.health.HealthEnabled
import com.srineere.airbridge.health.TileKind
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

    /**
     * Nothing in here is allowed to throw, because the launcher's answer
     * to a provider that throws is an empty cell.
     *
     * Every line below was a bare call, and any one of them failing took
     * the whole widget out: `provideContent` is never reached, no
     * `RemoteViews` is handed back, and the home screen is left with a
     * card-shaped hole where this used to be. The reads are not
     * hypothetical either -- [WidgetSnapshotCache.get] goes to Health
     * Connect, which throws for a rate limit and for a provider that was
     * updated underneath us, and both of those happen on a real phone.
     *
     * So each read falls back to something drawable and the face is given
     * whatever did come back. A widget showing the Cuff card and a line
     * about why is a widget somebody can tap; a blank one is a widget
     * they remove.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
            .getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        val config = runCatching { WidgetStore.read(context, appWidgetId) }.getOrNull()
        // Null and empty are different answers. Null is "the read failed",
        // which the card says out loud; an empty snapshot is "nothing has
        // been written", which every face already draws as dashes.
        val snapshot = runCatching { WidgetSnapshotCache.get(context) }.getOrNull()
        val colours = runCatching { widgetColours(context) }.getOrNull()
        val now = System.currentTimeMillis()
        // Read out here rather than inside the composition: it is a
        // preferences lookup, and a composition recomposes.
        val healthOn = runCatching { HealthEnabled.isOn(context) }.getOrDefault(false)
        if (colours == null) {
            // The theme is what every card is drawn out of, so there is no
            // card to draw without it. Left to the launcher deliberately:
            // it keeps the last RemoteViews it was given, which is the
            // widget as it looked a moment ago -- better than anything
            // that could be invented here.
            return
        }
        provideContent { Body(appWidgetId, config, snapshot, colours, now, healthOn) }
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
        snapshot: WidgetSnapshot?,
        colours: WidgetColours,
        now: Long,
        healthOn: Boolean,
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
        if (snapshot == null) {
            // The readings could not be got at all. Named after the thing
            // the person put on their home screen rather than after the
            // failure, because "Cuff · couldn't read just now" is a card
            // that will be right again in half an hour, and a stack trace
            // is not something to put under somebody's clock.
            Empty(
                colours,
                "CUFF",
                if (cell == WidgetSize.Strip) "Couldn't read" else "Couldn't read just now. Tap to open.",
                openFor(config.design),
            )
            return
        }
        WidgetFace(config.design, cell, snapshot, colours, now, openFor(config.design), healthOn)
    }

    @Composable
    private fun NeedsSetup(appWidgetId: Int, colours: WidgetColours, cell: WidgetSize) {
        val context = LocalContext.current
        Empty(
            colours,
            "CUFF",
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
 * place every number on it is explained. This used to be a choice, with the
 * Health tab as the alternative; it is not any more, because a widget that
 * draws resting heart rate has exactly one screen behind it and asking
 * somebody to confirm that was asking them to agree with the app.
 *
 * A face that draws several readings at once has no single screen, so it
 * falls through to the tab, which is the honest answer rather than a
 * setting.
 */
private fun openFor(design: WidgetDesign): Action {
    val route = routeFor(design)
    return actionStartActivity(
        MainActivity::class.java,
        if (route == null) actionParametersOf() else actionParametersOf(WidgetRouteKey to route),
    )
}

/**
 * The screen behind a design, or null where there is no single one.
 *
 * Pulled out of [openFor] because a card inside a stack cannot carry a
 * Glance action: a child of a collection is clicked through a fill-in
 * intent against the collection's own template, so the stack needs the
 * route as a value rather than as an [Action].
 */
internal fun routeFor(design: WidgetDesign): String? {
    return when (design) {
        is WidgetDesign.OfTile -> design.kind.route
        is WidgetDesign.OfBand -> DEVICE_ROUTE
        // Several readings at once, so there is no one screen behind them.
        is WidgetDesign.OfRings -> null
        is WidgetDesign.OfDials -> when (design.face) {
            DialFace.Score -> SLEEP_ROUTE
            else -> null
        }
        is WidgetDesign.OfType -> when (design.face) {
            TypeFace.Steps -> TileKind.Steps.route
            TypeFace.Sleep -> TileKind.Sleep.route
            else -> DEVICE_ROUTE
        }
    }
}

/**
 * One design, drawn at one cell size.
 *
 * The dispatch is here rather than inside the widget class because the
 * stack draws the same faces without being a [GlanceAppWidget] at all: it
 * composes each card through `GlanceRemoteViews` and hands the results to a
 * `StackView`. Two copies of this `when` is two places for a family to be
 * forgotten.
 */
@Composable
internal fun WidgetFace(
    design: WidgetDesign,
    cell: WidgetSize,
    snapshot: WidgetSnapshot,
    colours: WidgetColours,
    now: Long,
    onClick: Action?,
    healthOn: Boolean = true,
) {
    // A widget placed while the health half was on, drawn after it was
    // switched off.
    //
    // Saying so beats both of the alternatives. An empty card is the
    // picture this app already draws for "nothing has been written yet",
    // which sends somebody to Google Health looking for a sync that was
    // never the problem. The last reading, left standing, is a figure on
    // a home screen that nobody checks and that stops being true
    // tomorrow.
    //
    // The tap is left as it was on purpose: every health route is turned
    // away while the half is off and lands on the Device tab, which is
    // where the switch is, so the card and the tap say the same thing.
    if (!healthOn && design.needsHealth()) {
        Empty(
            colours,
            design.title,
            if (cell == WidgetSize.Strip) "Health is off" else "The Health tab is off. Tap to turn it on.",
            onClick,
        )
        return
    }
    val face = WidgetConfig.faceSizeOf(design, cell)
    when (design) {
        is WidgetDesign.OfTile -> TileWidget(design.kind, face, snapshot, colours, onClick)
        is WidgetDesign.OfBand -> BandWidget(design.face, face, snapshot, colours, onClick, now)
        is WidgetDesign.OfRings -> RingsWidget(design.face, face, snapshot, colours, onClick)
        is WidgetDesign.OfDials -> DialsWidget(design.face, face, snapshot, colours, onClick)
        is WidgetDesign.OfType -> TypeWidget(design.face, face, snapshot, colours, onClick, now)
    }
}

internal const val DEVICE_ROUTE = "device"
internal const val SLEEP_ROUTE = "healthsleep"

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
    // Volatile because refresh() writes it from whatever thread called
    // refreshWidgets while the stack's onDataSetChanged reads it through
    // get() on a binder thread. Without the barrier the stack could redraw
    // from the snapshot the refresh was meant to replace, while the Glance
    // faces beside it drew the new one.
    @Volatile private var held: WidgetSnapshot? = null

    /**
     * Whether Health Connect has actually been read in *this* process.
     *
     * Not the same question as "is [held] set". A cold process restores
     * what it kept on disk into [held] without reading anything, and the
     * difference between those two states is what [get] turns on.
     */
    @Volatile private var read = false

    /** Whether the kept entry has been looked for. Only read under the lock. */
    private var looked = false

    suspend fun get(context: Context, maxAge: Long = FRESH_MILLIS): WidgetSnapshot = mutex.withLock {
        // A snapshot read for thirty days cannot answer a readiness card
        // that wants a hundred and twenty, so a held one that is too short
        // is as stale as an old one however recently it was taken.
        val want = widgetWindowDays(context)
        val existing = held ?: kept(context)
        if (existing != null && existing.days >= want &&
            System.currentTimeMillis() - existing.readAt < maxAge
        ) {
            return existing
        }
        if (existing != null && !read) {
            // A cold process draws what it kept and reads afterwards.
            //
            // This is the whole of the reboot fix. The alternative is what
            // was here before: the first thing a widget does on a phone
            // that has just started is to block on Health Connect, which
            // after a reboot is itself still waking up -- so the launcher
            // sat on a spinner, or on an empty stack, for as long as that
            // took, and drew nothing at all if it failed. The figures
            // below are a few minutes old and carry their real age; the
            // read that corrects them is on its way before this returns.
            WidgetRefresh.readNow(context)
            return existing
        }
        // Nothing kept and nothing held: there is genuinely nothing to
        // draw, so this one does wait. A failed read falls back to
        // whatever there was rather than to an empty card.
        reread(context, want) ?: existing ?: WidgetSnapshot(days = want)
    }

    /**
     * Reads Health Connect now and keeps what comes back.
     *
     * Called by the worker rather than left to the next draw. It used to
     * be the other way round -- the worker emptied the cache and let
     * whichever widget drew first pay for the read -- and that cannot work
     * now that a draw prefers the kept entry: emptying would simply send
     * the next draw back to the disk copy it had just been told was stale.
     */
    suspend fun refresh(context: Context) {
        mutex.withLock { reread(context, widgetWindowDays(context)) }
    }

    /**
     * Re-reads the band's facts and leaves everything else alone.
     *
     * The band is the one part of this snapshot that changes because of
     * something this app did -- a buzz delivered, a battery read -- rather
     * than because Health Connect moved. Throwing the whole snapshot away
     * to pick up a buzz would mean a Health Connect read per buzz, which a
     * burst of messages turns into a read every few seconds.
     */
    suspend fun refreshBand(context: Context) = mutex.withLock {
        val existing = held ?: kept(context) ?: return@withLock
        held = existing.copy(
            band = runCatching { BandStore.read(context) }.getOrDefault(existing.band),
        )
    }

    /**
     * One read, held, kept on disk, and never allowed to throw.
     *
     * A read that fails leaves the last one standing. Health Connect
     * throws for a rate limit and for a provider that is mid-update, and
     * both of those are a reason to draw the figures from ten minutes ago
     * rather than to blank a home screen.
     */
    private suspend fun reread(context: Context, want: Int): WidgetSnapshot? {
        val fresh = runCatching { readWidgetSnapshot(context, want) }.getOrNull() ?: return null
        held = fresh
        read = true
        WidgetSnapshotStore.write(context, fresh)
        return fresh
    }

    /**
     * The entry on disk, looked for once and then never again.
     *
     * Once, because after the first look either [held] holds it or there
     * was nothing to hold, and a process that reads the file on every draw
     * is a process doing file IO on a binder thread the launcher is
     * waiting on.
     */
    private fun kept(context: Context): WidgetSnapshot? {
        if (looked) return null
        looked = true
        return runCatching { restoreWidgetSnapshot(context) }.getOrNull()?.also { held = it }
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
    if (reread) WidgetSnapshotCache.refresh(context)
    val manager = GlanceAppWidgetManager(context)
    val widget = AirWidget()
    refreshStacks(context)
    glanceIds(context).forEach { id ->
        // One failure is one widget, not all of them: an id the launcher
        // reported and has since dropped must not stop the rest redrawing.
        runCatching { widget.update(context, manager.getGlanceIdBy(id)) }
    }
}

/** Redraws one, which is what placing or reconfiguring a widget needs. */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int) {
    if (appWidgetId in stackIds(context)) {
        refreshStacks(context, intArrayOf(appWidgetId))
        return
    }
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
