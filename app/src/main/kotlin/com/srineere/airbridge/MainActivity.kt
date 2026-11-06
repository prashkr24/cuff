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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * The screen a widget asked for, if this was opened by one.
     *
     * Held as state rather than read once, because the activity is
     * singleTop: tapping a second widget while the app is already open
     * delivers a new intent to the same instance, and reading only the
     * original would silently ignore it.
     */
    private var openRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
        openRoute = intent?.getStringExtra(ROUTE_EXTRA)
        WidgetRefresh.reconcile(this)
        // Re-books the morning report, which an update or a cleared
        // WorkManager database would otherwise have silently dropped.
        MorningMoment.schedule(this)
        JournalReminder.schedule(this)
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
                AirBridgeApp(openRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRoute = intent.getStringExtra(ROUTE_EXTRA)
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
        val context = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            refreshWidgets(context)
        }
    }
}
