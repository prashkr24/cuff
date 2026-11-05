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
    views.setPendingIntentTemplate(R.id.stack_view, openTemplate(context, appWidgetId))
    views.setOnClickPendingIntent(R.id.stack_previous, pageIntent(context, appWidgetId, false))
    views.setOnClickPendingIntent(R.id.stack_next, pageIntent(context, appWidgetId, true))
    // Last, and in every update rather than only when a page turns: the
    // host rebuilds this view whenever it likes, and without it the stack
    // would drop back to its first card each time it did.
    views.setDisplayedChild(R.id.stack_view, StackPage.read(context, appWidgetId))
    return views
}

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

internal const val ACTION_NEXT = "com.srineere.airbridge.STACK_NEXT"
internal const val ACTION_PREVIOUS = "com.srineere.airbridge.STACK_PREVIOUS"

/**
 * The template every card's tap is filled in against.
 *
 * Mutable deliberately, and it is the one place in this app that asks for a
 * mutable pending intent: a collection's children tap by filling in this
 * intent with their own extra, and an immutable template has nothing to
 * fill in. It names an explicit component in this package, so what the
 * fill-in can reach is one activity of our own.
 */
private fun openTemplate(context: Context, appWidgetId: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    var flags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
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
    private var routes: List<String?> = emptyList()

    override fun onCreate() = Unit

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun onDataSetChanged() {
        val stack = WidgetStore.read(context, appWidgetId)?.designs.orEmpty()
        if (stack.isEmpty()) {
            cards = emptyList()
            routes = emptyList()
            return
        }
        val size = cardSize(context, appWidgetId)
        val cell = WidgetSize.nearest(size.width.value.roundToInt(), size.height.value.roundToInt())
        // One read for the whole stack, as the five single-card providers
        // share one between them: six cards must not be six readings of
        // today taken a moment apart.
        val snapshot = runBlocking { WidgetSnapshotCache.get(context) }
        val colours = widgetColours(context)
        val now = System.currentTimeMillis()
        val remote = GlanceRemoteViews()
        val began = System.currentTimeMillis()
        ChartBitmap.bytesDrawn = 0
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
        routes = stack.map { routeFor(it) }
        Log.i(
            STACK_TAG,
            "stack $appWidgetId composed ${cards.size} cards at $size " +
                "in ${System.currentTimeMillis() - began}ms, " +
                "${ChartBitmap.bytesDrawn / 1024}KB of bitmaps",
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
     * The tap is a fill-in against the stack's template rather than an
     * action on the card, because a child of a collection cannot carry a
     * pending intent of its own.
     */
    override fun getViewAt(position: Int): RemoteViews {
        val item = RemoteViews(context.packageName, R.layout.widget_stack_item)
        val card = cards.getOrNull(position) ?: return item
        item.removeAllViews(R.id.stack_item)
        item.addView(R.id.stack_item, card)
        item.setOnClickFillInIntent(
            R.id.stack_item,
            Intent().apply {
                routes.getOrNull(position)?.let { putExtra(ROUTE_EXTRA, it) }
            },
        )
        return item
    }

    override fun getLoadingView(): RemoteViews? = null

    /** One, because every card wears the same frame. */
    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        cards = emptyList()
        routes = emptyList()
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

