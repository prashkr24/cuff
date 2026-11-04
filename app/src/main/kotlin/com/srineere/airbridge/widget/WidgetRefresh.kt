package com.srineere.airbridge.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps the home screen roughly current without ever touching the band.
 *
 * Half an hour, which is also the floor WorkManager enforces for periodic
 * work. Nothing here opens a BLE session: the worker reads Health Connect,
 * which this app holds the background-read permission for, and the band
 * figures come from whatever the app last stored.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val placed = liveIds(applicationContext)
        // Nothing on the home screen: stop rather than read Health Connect
        // every half hour for a widget that was removed weeks ago.
        if (placed.isEmpty()) {
            WidgetStore.prune(applicationContext, emptySet())
            WidgetRefresh.cancel(applicationContext)
            return Result.success()
        }
        WidgetStore.prune(applicationContext, placed)
        refreshWidgets(applicationContext)
        // A read that fails is not a reason to stop the schedule: Health
        // Connect may simply have been updating. The next turn tries again.
        return Result.success()
    }
}

object WidgetRefresh {
    private const val NAME = "air-widgets"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            // Keep, not Replace: replacing on every app start resets the
            // period, and a widget on a phone that is opened often would
            // then never actually reach its half hour.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    /**
     * Turns the schedule on when there is something to refresh and off when
     * there is not. Called when the app starts, which is also the cheapest
     * moment to notice that every widget has been removed.
     */
    fun reconcile(context: Context) {
        if (liveIds(context).isEmpty()) cancel(context) else schedule(context)
    }
}

/** What the launcher actually has, across all five providers. */
internal fun liveIds(context: Context): Set<Int> {
    val manager = AppWidgetManager.getInstance(context) ?: return emptySet()
    return RECEIVERS.flatMap { receiver ->
        runCatching {
            manager.getAppWidgetIds(ComponentName(context, receiver)).toList()
        }.getOrDefault(emptyList())
    }.toSet()
}

/**
 * The provider behind one cell size.
 *
 * Used to offer a widget from inside the app: `requestPinAppWidget` needs a
 * component, and the five receivers are the only thing that differs between
 * the sizes.
 */
internal fun receiverFor(size: WidgetSize): Class<out AirWidgetReceiver> = when (size) {
    WidgetSize.Strip -> StripWidgetReceiver::class.java
    WidgetSize.Square -> SquareWidgetReceiver::class.java
    WidgetSize.Row -> RowWidgetReceiver::class.java
    WidgetSize.Wide -> WideWidgetReceiver::class.java
    WidgetSize.Tall -> TallWidgetReceiver::class.java
}

/**
 * Asks the launcher to place one, where the launcher supports being asked.
 *
 * Android does not run the configuration activity for a widget pinned this
 * way, so it lands showing "Tap to choose" -- which is the face that exists
 * for exactly this, and tapping it opens the catalogue. Returns false where
 * the launcher has no pin support, and the screen then falls back to telling
 * the person how to do it by hand.
 */
internal fun offerWidget(context: Context, size: WidgetSize): Boolean {
    val manager = AppWidgetManager.getInstance(context) ?: return false
    if (!manager.isRequestPinAppWidgetSupported) return false
    return runCatching {
        manager.requestPinAppWidget(ComponentName(context, receiverFor(size)), null, null)
    }.getOrDefault(false)
}

private val RECEIVERS = listOf(
    StripWidgetReceiver::class.java,
    SquareWidgetReceiver::class.java,
    RowWidgetReceiver::class.java,
    WideWidgetReceiver::class.java,
    TallWidgetReceiver::class.java,
)
