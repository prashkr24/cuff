package com.srineere.airbridge.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        // A poke knows that only this app's own facts moved, so it redraws
        // without going back to Health Connect for a reading that has not.
        if (inputData.getBoolean(WidgetRefresh.BAND_ONLY, false)) {
            WidgetSnapshotCache.refreshBand(applicationContext)
            refreshWidgets(applicationContext, reread = false)
            return Result.success()
        }
        refreshWidgets(applicationContext)
        // A read that fails is not a reason to stop the schedule: Health
        // Connect may simply have been updating. The next turn tries again.
        return Result.success()
    }
}

object WidgetRefresh {
    private const val NAME = "air-widgets"
    private const val NOW = "air-widgets-now"
    internal const val BAND_ONLY = "band_only"

    /**
     * Redraw soon, because something this app just did changed what a
     * widget says.
     *
     * The half-hourly schedule is for Health Connect, which moves on its
     * own and slowly. The band's facts move when the relay buzzes, and
     * nothing told the widgets -- so "Last buzz" showed whatever was true
     * at the last half-hourly turn, which read as the line being broken.
     *
     * Unique work with REPLACE and a few seconds' delay, so twenty messages
     * in a burst are one redraw shortly after the last of them rather than
     * twenty redraws during it.
     */
    fun poke(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setInitialDelay(4, TimeUnit.SECONDS)
                    .setInputData(workDataOf(BAND_ONLY to true))
                    .build(),
            )
        }
    }

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

/**
 * What the launcher actually has, across all six providers.
 *
 * The stack is in here with the other five and has to be: this set is what
 * `prune` keeps, and a stack left out of it would have its row deleted the
 * first time the worker ran.
 */
internal fun liveIds(context: Context): Set<Int> {
    val manager = AppWidgetManager.getInstance(context) ?: return emptySet()
    return RECEIVERS.flatMap { receiver ->
        runCatching {
            manager.getAppWidgetIds(ComponentName(context, receiver)).toList()
        }.getOrDefault(emptyList())
    }.toSet()
}

/** The ids drawn by Glance, which is every provider except the stack. */
internal fun glanceIds(context: Context): Set<Int> {
    val manager = AppWidgetManager.getInstance(context) ?: return emptySet()
    return RECEIVERS.filterNot { it == StackWidgetReceiver::class.java }.flatMap { receiver ->
        runCatching {
            manager.getAppWidgetIds(ComponentName(context, receiver)).toList()
        }.getOrDefault(emptyList())
    }.toSet()
}

internal fun stackIds(context: Context): IntArray = runCatching {
    AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, StackWidgetReceiver::class.java))
}.getOrDefault(IntArray(0))

/**
 * Rebuilds the frame and tells the platform the cards are stale.
 *
 * Two calls, not one. Updating the RemoteViews alone leaves the adapter
 * holding the cards it composed last time; notifying alone leaves a stack
 * whose frame was built for a different cell.
 */
internal fun refreshStacks(context: Context, ids: IntArray = stackIds(context)) {
    if (ids.isEmpty()) return
    val manager = AppWidgetManager.getInstance(context) ?: return
    ids.forEach { runCatching { manager.updateAppWidget(it, stackViews(context, it)) } }
    runCatching { manager.notifyAppWidgetViewDataChanged(ids, com.srineere.airbridge.R.id.stack_view) }
}

private val RECEIVERS = listOf(
    StripWidgetReceiver::class.java,
    SquareWidgetReceiver::class.java,
    RowWidgetReceiver::class.java,
    WideWidgetReceiver::class.java,
    TallWidgetReceiver::class.java,
    StackWidgetReceiver::class.java,
)
