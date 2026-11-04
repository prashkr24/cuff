package com.srineere.airbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.srineere.airbridge.ui.AirBridgeApp
import com.srineere.airbridge.ui.AppSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.AirBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
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
                AirBridgeApp()
            }
        }
    }
}
