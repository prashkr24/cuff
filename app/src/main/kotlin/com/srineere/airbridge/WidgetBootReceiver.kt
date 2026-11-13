package com.srineere.airbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.srineere.airbridge.widget.WidgetRefresh

/**
 * Puts the home screen back after a reboot or an app update.
 *
 * Nothing did, which is the whole of the first bug reported against the
 * stacks. A widget's RemoteViews live in the launcher's memory and in the
 * system's, and neither survives a restart: every widget comes back as
 * whatever its `initialLayout` draws -- a Glance spinner for the five
 * single-card sizes, an empty frame for the stack -- and stays there until
 * its provider sends something. The only thing that sent anything was
 * `MainActivity.onCreate`, so the widgets were fixed by opening the app and
 * by nothing else.
 *
 * **A one-shot redraw, not a new schedule.** `WidgetRefresh`'s half-hourly
 * turn is WorkManager periodic work, which WorkManager itself re-establishes
 * across a reboot, so there is no schedule missing here to put back. What is
 * missing is only the window before that first post-boot turn, which can be
 * the full half hour. One redraw closes it. [WidgetRefresh.ensure] is called
 * beside it for the phone that has genuinely lost the schedule --
 * force-stopped, or restored from a backup -- and it only ever puts one
 * back: `reconcile` would also take one away, and a launcher that has not
 * finished loading its widgets at boot reports none.
 *
 * **And a redraw, not a read.** The redraw composes from the snapshot kept
 * on disk and touches nothing else, so a reboot costs no Health Connect
 * query at all. The widgets appear immediately, with figures whose real age
 * they carry, and the draw itself asks for a fresh read once it has
 * something on screen. A boot receiver that queried Health Connect would be
 * a tax on every restart, paid before the user has looked at the phone.
 *
 * Both broadcasts are protected, so only the system can deliver them, which
 * is what lets this be exported. Deliberately a second receiver rather than
 * another job for [RelayBootReceiver]: that one decides whether to start a
 * foreground service and bails out early in three different ways, and
 * widgets have no business being downstream of any of them.
 */
class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handled) return
        // The application context, because the receiver's own is torn down
        // the moment onReceive returns and the work below outlives it.
        val app = context.applicationContext
        WidgetRefresh.ensure(app)
        WidgetRefresh.redraw(app)
        Log.i("AirBridge", "boot: widgets asked to redraw")
    }

    private companion object {
        val handled = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
