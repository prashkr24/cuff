package com.srineere.airbridge.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceModifier
import androidx.glance.layout.Box
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.srineere.airbridge.MainActivity
import com.srineere.airbridge.R
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Several widgets in one cell, swiped through in place.
 *
 * The one provider here that is not a [androidx.glance.appwidget.GlanceAppWidget].
 * Glance has no `StackView`, and nothing else in a RemoteViews flips
 * between children the way a stack does -- `LazyColumn` would scroll two
 * half-cards past each other, which is a list, not a stack. So this builds
 * the RemoteViews by hand: a `StackView` fed by a `RemoteViewsService`,
 * with each card still composed by Glance from the same `WidgetFace` the
 * five single-card providers draw.
 *
 * What that buys is one cell on the home screen holding as many readings as
 * somebody wants. What it costs is that a card inside a collection cannot
 * carry its own click action -- see [StackFactory.getViewAt].
 */
class StackWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            runCatching { manager.updateAppWidget(id, stackViews(context, id)) }
        }
        runCatching { manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.stack_view) }
    }

    /**
     * Resized, so every card is composed again at the new cell.
     *
     * A stack is the one widget here whose children are measured by
     * something other than the launcher: the cards were composed for the
     * old size and will simply be squashed into the new one until they are
     * built again.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        runCatching { manager.updateAppWidget(appWidgetId, stackViews(context, appWidgetId)) }
        runCatching { manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.stack_view) }
    }

    /**
     * The two chevrons, which are the only way to turn a page.
     *
     * No widget scrolls sideways: the platform allows five adapter-backed
     * views in a RemoteViews and every one of them is vertical or does not
     * scroll at all. So paging is a tap rather than a drag, and the flipper
     * slides between cards so that it still reads as sideways.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_NEXT && action != ACTION_PREVIOUS) {
            super.onReceive(context, intent)
            return
        }
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        // The page is this app's own, not the flipper's.
        //
        // showNext through partiallyUpdateAppWidget arrived and returned
        // cleanly and the card never moved: a partial update reapplies its
        // actions against a view the host rebinds and redraws whenever it
        // feels like it, so an instruction that means "one more than
        // wherever you are" lands somewhere nobody can predict. A stored
        // index and setDisplayedChild say exactly which card, and survive
        // the host throwing the view away and building it again.
        val count = WidgetStore.read(context, appWidgetId)?.designs?.size ?: return
        if (count < 2) return
        val step = if (action == ACTION_NEXT) 1 else -1
        val page = Math.floorMod(StackPage.read(context, appWidgetId) + step, count)
        StackPage.write(context, appWidgetId, page)
        runCatching {
            AppWidgetManager.getInstance(context)
                .updateAppWidget(appWidgetId, stackViews(context, appWidgetId))
        }
        Log.i(STACK_TAG, "paged $appWidgetId to $page of $count")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // The row would otherwise outlive the widget, and Your widgets would
        // list a stack the home screen no longer has.
        WidgetStore.remove(context, appWidgetIds)
        StackPage.remove(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // The first widget of any size is what turns the periodic read on,
        // and a stack is a widget like any other. The five AirWidgetReceiver
        // sizes do this and this one did not: in practice
        // WidgetConfigActivity.place() schedules on both paths, so the gap
        // only showed for a host that drops a stack without running the
        // configuration activity.
        WidgetRefresh.schedule(context)
    }
}

/** The service the platform binds to for one stack's cards. */
class StackWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = StackFactory(
        applicationContext,
        intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ),
    )
}

/**
 * The frame, the adapter and the one pending intent every card taps through.
 */
@Suppress("DEPRECATION")
internal fun stackViews(context: Context, appWidgetId: Int): RemoteViews {
    // Always the same layout. Sending a different one to animate backwards
    // costs the host a fresh inflate, and the fresh flipper clamps to its
    // first card before the adapter has anything in it.
    val views = RemoteViews(context.packageName, R.layout.widget_stack)
    val intent = Intent(context, StackWidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Unique per widget, or every stack on the home screen is handed
        // the same factory: the platform keys its adapters on the intent,
        // and extras are not part of that key.
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    // The RemoteCollectionItems form of this arrived in API 31 and would
    // drop the service entirely; it would also mean every card travelling
    // in one binder transaction, and this app's minimum is 28 regardless.
    views.setRemoteAdapter(R.id.stack_view, intent)
    views.setEmptyView(R.id.stack_view, R.id.stack_empty)
    views.setOnClickPendingIntent(R.id.stack_previous, pageIntent(context, appWidgetId, false))
    views.setOnClickPendingIntent(R.id.stack_next, pageIntent(context, appWidgetId, true))
    // Clamped to what is actually in the stack now. Reconfiguring five
    // cards down to two leaves the stored page pointing past the end: the
    // flipper clamps what it draws, the route came out null so a tap opened
    // the Health tab, and the next chevron stepped from a number no card
    // was on.
    //
    // Guarded, because a stack whose row cannot be read is not a stack with
    // no row: the one would say "tap to choose" at somebody who already
    // has, and throwing out of here would leave the launcher with no views
    // at all.
    val designs = runCatching {
        WidgetStore.read(context, appWidgetId)?.designs.orEmpty()
    }.getOrDefault(emptyList())
    views.setTextViewText(R.id.stack_empty, context.getString(stackEmptyText(designs)))
    val page = StackPage.read(context, appWidgetId)
        .coerceIn(0, (designs.size - 1).coerceAtLeast(0))
    // The tap belongs to the frame over the flipper rather than to the
    // card; openIntent says why the collection's own template never fires.
    // The route is the showing card's, and this runs on every page turn.
    val showing = designs.getOrNull(page)
    views.setOnClickPendingIntent(
        R.id.stack_tap,
        openIntent(context, appWidgetId, showing?.let { routeFor(it) }),
    )
    // Last, and in every update rather than only when a page turns: the
    // host rebuilds this view whenever it likes, and without it the stack
    // would drop back to its first card each time it did.
    views.setDisplayedChild(R.id.stack_view, page)
    return views
}

/**
 * Which of the two silences an empty stack is in.
 *
 * `setEmptyView` fires on one condition -- the adapter has no items -- and
 * that one condition covers two completely different facts. A stack with
 * no designs has never been told what to hold, and "tap to choose" is the
 * only useful thing to say to it. A stack *with* designs whose cards are
 * not composed has been set up, possibly months ago, and telling that
 * person to choose what the stack holds is telling them their widget has
 * forgotten them.
 *
 * Which is exactly what a reboot did. The sentence used to be baked into
 * `widget_stack.xml`, which is also the stack's `initialLayout` -- the
 * layout the host inflates with no adapter and no actions while it waits
 * for the provider -- so every restart put it on screen and only opening
 * the app took it off again.
 *
 * Pulled out of [stackViews] so the rule can be asserted without a
 * launcher.
 */
internal fun stackEmptyText(designs: List<WidgetDesign>): Int =
    if (designs.isEmpty()) R.string.widget_stack_empty else R.string.widget_stack_quiet

/**
 * Which card of a stack is showing.
 *
 * In the same preferences as everything else a widget knows, keyed by the
 * same id, and dropped with it: a page belongs to one placed widget and
 * means nothing without it.
 */
internal object StackPage {
    private const val PREFS = "air_health"
    private const val PREFIX = "stackpage:"

    fun read(context: Context, appWidgetId: Int): Int = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(PREFIX + appWidgetId, 0)
    }.getOrDefault(0)

    fun write(context: Context, appWidgetId: Int, page: Int) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PREFIX + appWidgetId, page).apply()
        }
    }

    fun remove(context: Context, appWidgetIds: IntArray) {
        runCatching {
            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            appWidgetIds.forEach { edit.remove(PREFIX + it) }
            edit.apply()
        }
    }
}

/** One chevron's tap: turn this stack's page, and nothing else. */
private fun pageIntent(context: Context, appWidgetId: Int, forward: Boolean): PendingIntent {
    val intent = Intent(context, StackWidgetReceiver::class.java).apply {
        action = if (forward) ACTION_NEXT else ACTION_PREVIOUS
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Two PendingIntents are the same one unless something in the intent
        // or the request code tells them apart, and extras never do.
        data = Uri.parse("airstack://$appWidgetId/" + if (forward) "next" else "previous")
    }
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(context, appWidgetId * 2 + if (forward) 1 else 0, intent, flags)
}

/**
 * How many cards one stack may hold.
 *
 * Each composed card is held for the life of the factory and carries its
 * own chart bitmap -- about 620 KB -- so this is a memory ceiling rather
 * than a taste. Eight is already more cards than anybody pages through.
 */
internal const val MAX_STACK_CARDS = 8

internal const val ACTION_NEXT = "com.srineere.airbridge.STACK_NEXT"
internal const val ACTION_PREVIOUS = "com.srineere.airbridge.STACK_PREVIOUS"

/**
 * Opening the screen behind the card that is showing.
 *
 * Not the collection's pending-intent template with a fill-in per card,
 * which is the documented way and is the way that did nothing here. That
 * mechanism fires from `AdapterView.performItemClick`, and
 * `AdapterViewFlipper` never calls it: `AdapterViewAnimator` carries no
 * item touch handling at all, which is the same reason this stack needs
 * chevrons rather than a swipe. The template was set, the fill-ins were
 * set, and a tap on a card went nowhere.
 *
 * So the tap is an ordinary click on a frame laid over the flipper, with
 * the showing card's route baked into it. That works because the whole
 * layout is rebuilt on every page turn regardless. It also means this app
 * no longer asks for a mutable pending intent anywhere.
 */
private fun openIntent(context: Context, appWidgetId: Int, route: String?): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        route?.let { putExtra(ROUTE_EXTRA, it) }
        // Extras are no part of a PendingIntent's identity and the request
        // code is one per widget, so without something here every card of
        // every stack would share one intent and open whichever route was
        // stored last.
        data = Uri.parse("airstack://$appWidgetId/open/" + (route ?: "none"))
    }
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getActivity(context, appWidgetId, intent, flags)
}

/**
 * The cards of one stack, composed once per data change.
 *
 * [onDataSetChanged] is allowed to be slow and runs off the main thread,
 * which is why the Health Connect read and every composition happen there
 * rather than in [getViewAt] -- that one is called per card, on a binder
 * thread, while the launcher is waiting to draw.
 */
internal class StackFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var cards: List<RemoteViews> = emptyList()

    override fun onCreate() = Unit

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun onDataSetChanged() {
        // Capped, because every composed card is held in `cards` for as
        // long as this factory is bound and each carries its own chart
        // bitmap -- about 620 KB measured, so the whole catalogue in one
        // stack would be twenty megabytes resident and recomposed in full
        // on every refresh. The configuration screen caps the choice too;
        // this is the defence for a row already stored.
        val stack = runCatching {
            WidgetStore.read(context, appWidgetId)?.designs.orEmpty()
        }.getOrDefault(emptyList()).take(MAX_STACK_CARDS)
        // The one honest way to have no cards: nobody has said what this
        // stack holds. Everything below is written so that a stack which
        // *has* been told always comes out of here with a card per design,
        // whatever Health Connect is doing -- an empty adapter is the
        // launcher's cue to draw the "tap to choose" sentence, and a stack
        // that drew that after every reboot is the bug being fixed.
        if (stack.isEmpty()) {
            cards = emptyList()
            return
        }
        val size = cardSize(context, appWidgetId)
        val cell = WidgetSize.nearest(size.width.value.roundToInt(), size.height.value.roundToInt())
        // One read for the whole stack, as the five single-card providers
        // share one between them: six cards must not be six readings of
        // today taken a moment apart.
        //
        // get() prefers the snapshot kept on disk over waiting on Health
        // Connect and never throws, which is what makes this safe to call
        // from a binder thread the launcher is blocked on. The fallback
        // below is for the case it cannot cover: a first run, with nothing
        // read and nothing kept. The faces draw their own empty states from
        // it, and empty cards are still cards.
        val snapshot = runCatching { runBlocking { WidgetSnapshotCache.get(context) } }
            .getOrDefault(WidgetSnapshot())
        val colours = widgetColours(context)
        val now = System.currentTimeMillis()
        val remote = GlanceRemoteViews()
        val began = System.currentTimeMillis()
        cards = stack.mapNotNull { design ->
            // One card that will not compose is one card: a family that
            // throws must not leave the whole stack empty.
            runCatching {
                runBlocking {
                    remote.compose(context = context, size = size) {
                        // An exact size, not fillMaxSize. A list measures its
                        // children with no height to fill, so match_parent
                        // there collapses to whatever the content happens to
                        // need and the cards come out different heights. Given
                        // the cell exactly, each card fills the view and the
                        // next one is a scroll away.
                        Box(
                            modifier = GlanceModifier
                                .width(size.width)
                                .height(size.height),
                        ) {
                            WidgetFace(design, cell, snapshot, colours, now, onClick = null)
                        }
                    }.remoteViews
                }
            }.getOrNull()
        }
        Log.i(
            STACK_TAG,
            "stack $appWidgetId composed ${cards.size} cards at $size " +
                "in ${System.currentTimeMillis() - began}ms",
        )
    }

    override fun getCount(): Int = cards.size

    /**
     * One card, wrapped in our own frame.
     *
     * The frame is not decoration. Glance hands back a RemoteViews whose
     * layout id is picked from its generated pool and differs card by card;
     * an adapter that returns those directly is telling the platform it has
     * one view type when it has several, and the recycler then draws a
     * steps chart into a sleep card's frame. Wrapped, every item is the
     * same layout and the differences are all inside it.
     *
     * No tap here. A child of a collection cannot carry a pending intent
     * of its own, and the fill-in meant to stand in for one never fires
     * from a flipper; the frame over the flipper carries it instead.
     */
    override fun getViewAt(position: Int): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_stack_item)
        val card = cards.getOrNull(position) ?: return item
        item.removeAllViews(R.id.stack_item)
        item.addView(R.id.stack_item, card)
        return item
    }

    /**
     * The empty frame, rather than the platform's own loading view.
     *
     * Null here does not mean "no loading view": it means the platform
     * builds a default one, and the default one is a `TextView` reading
     * **Loading…** sized to the first card. That is where the word the user
     * reported was coming from -- not from this app's vocabulary, which has
     * no such string in it. It appears whenever the host asks for a card
     * the adapter has not handed over yet, which is every rebind of a
     * stack, and it is the most alarming thing a home screen can say about
     * a widget that is in fact working.
     *
     * The same layout every card wears, so the recycler still sees one
     * shape, and empty, so the moment before a card arrives looks like
     * nothing rather than like a failure.
     */
    override fun getLoadingView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_stack_item)

    /** One, because every card wears the same frame. */
    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        cards = emptyList()
    }
}

/**
 * What one card is composed at.
 *
 * The launcher reports two heights: the short one is landscape and the
 * tall one is portrait, so a card built to `MIN_HEIGHT` on a phone is
 * short by a third. The insets come off because a StackView does not give
 * a child the whole cell -- it keeps a margin to show the edges of the
 * cards behind, and a card composed to the full size has its last line
 * clipped.
 */
private fun cardSize(context: Context, appWidgetId: Int): DpSize {
    val options = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    }.getOrNull() ?: Bundle()

    // The list the launcher publishes from API 31, which is the only
    // source that is actually the cell. The four bounds below it are a
    // range -- "no narrower than this, no taller than that" -- and reading
    // a size out of a range is how a card ends up composed for a cell
    // nobody has.
    val listed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        runCatching {
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    // The portrait one is the taller of the pair a launcher publishes.
    val portrait = listed?.maxByOrNull { it.height }

    val width = portrait?.width?.toInt()
        ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 }
        ?: FALLBACK_WIDE
    val height = portrait?.height?.toInt()
        ?: options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 }
        ?: FALLBACK_TALL

    // The whole cell. The insets that used to come off here were for a
    // StackView, which measured its children smaller than the cell to show
    // the edges of the cards behind; a list gives a child the lot.
    val size = DpSize(width.coerceAtLeast(120).dp, height.coerceAtLeast(60).dp)
    return size
}

private const val FALLBACK_WIDE = 310
private const val FALLBACK_TALL = 150
internal const val STACK_TAG = "AirStack"

