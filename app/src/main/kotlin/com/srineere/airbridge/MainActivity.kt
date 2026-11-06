package com.srineere.airbridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.srineere.airbridge.ui.AirBridgeApp
import com.srineere.airbridge.ui.AppSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.AirBridgeTheme
import com.srineere.airbridge.widget.ROUTE_EXTRA
import com.srineere.airbridge.health.JournalReminder
import com.srineere.airbridge.health.MorningMoment
import com.srineere.airbridge.widget.WidgetRefresh
import com.srineere.airbridge.widget.refreshWidgets
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The screen a widget asked for, if this was opened by one.
     *
     * Held as state rather than read once, because the activity is
     * singleTop: tapping a second widget while the app is already open
     * delivers a new intent to the same instance, and reading only the
     * original would silently ignore it.
     *
     * Carries a count as well as the route, because mutableStateOf records
     * nothing when the new value equals the old one. Tapping the *same*
     * widget twice therefore wrote the same String, changed no state, and
     * never restarted the effect that navigates -- so the second tap only
     * brought the app forward on whatever screen it was left on.
     */
    private var openRoute by mutableStateOf<Pair<String, Int>?>(null)
    private var opened = 0

    private fun asked(intent: Intent?) {
        val route = intent?.getStringExtra(ROUTE_EXTRA) ?: return
        openRoute = route to ++opened
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
        asked(intent)
        setContent {
            val appearance by AppSettings.appearance.collectAsState()
            AirBridgeTheme(
                mode = appearance.mode,
                accent = appearance.accent,
                ground = appearance.ground(
                    when (appearance.mode) {
                        ThemeMode.Light -> false
                        ThemeMode.Dark -> true
                        ThemeMode.System -> isSystemInDarkTheme()
                    },
                ),
                edges = appearance.edges,
                custom = appearance.custom?.let { Color(it) },
            ) {
                AirBridgeApp(openRoute?.first, openRoute?.second ?: 0)
            }
        }
        // After setContent, off the main thread: none of these three draws
        // anything. Between them they were six AppWidgetManager binder
        // round-trips, two SharedPreferences loads and two WorkManager
        // enqueues on the critical path to the first frame.
        //
        // Still here rather than dropped: reconcile repairs a widget refresh
        // schedule an update can lose, and the two schedule() calls re-book
        // the morning report and the evening journal, which a cleared
        // WorkManager database would otherwise have silently dropped.
        lifecycleScope.launch(Dispatchers.Default) {
            WidgetRefresh.reconcile(this@MainActivity)
            MorningMoment.schedule(this@MainActivity)
            JournalReminder.schedule(this@MainActivity)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        asked(intent)
    }

    /**
     * Every widget is redrawn when the app is left.
     *
     * This is the one moment the home screen is about to be looked at and
     * the readings behind it have just been refreshed, which makes it worth
     * more than any number of background turns.
     */
    override fun onStop() {
        super.onStop()
        // One scope for the process, not a fresh root Job per onStop. Every
        // background-and-forward -- every screen off -- used to start another
        // uncancellable coroutine that did a full Health Connect read.
        val context = applicationContext
        background.launch { refreshWidgets(context) }
    }

    private companion object {
        /**
         * Outlives the activity on purpose: the redraw is for the home
         * screen the user is on their way to, so cancelling it when this
         * activity stops would cancel the only thing it is for.
         */
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
