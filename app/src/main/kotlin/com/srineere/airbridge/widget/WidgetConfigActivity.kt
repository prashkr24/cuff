package com.srineere.airbridge.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.ui.AppSettings
import com.srineere.airbridge.ui.ScreenTitle
import com.srineere.airbridge.ui.WidgetGallery
import com.srineere.airbridge.ui.theme.AirBridgeTheme
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.launch

/**
 * The catalogue, which is also the add flow.
 *
 * The launcher reserves a cell and launches this before the widget is
 * placed; nothing exists on the home screen until [setResult] returns OK, so
 * backing out is a cancel and leaves no half-made widget behind.
 *
 * One screen, and one tap. There used to be a second -- pick a design, then
 * answer two questions about it before an Add button -- and both questions
 * have since been taken away from the person and answered by the app: the
 * face follows whatever cell the widget ends up in, and a tap opens the
 * screen about the reading on the card. What was left of that screen was a
 * confirmation of a choice already made.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelled unless and until the person says otherwise.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        AppSettings.load(this)
        val cell = cellFor(appWidgetId)
        val changing = WidgetStore.read(this, appWidgetId) != null

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
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                    AddWidget(
                        cell = cell,
                        changing = changing,
                        onCancel = ::finish,
                        onPick = { design -> place(appWidgetId, WidgetConfig(design)) },
                    )
                }
            }
        }
    }

    private fun place(appWidgetId: Int, config: WidgetConfig) {
        WidgetStore.write(this, appWidgetId, config)
        WidgetRefresh.schedule(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        // Drawn before finishing, so the widget is never blank on arrival --
        // and by id, because the launcher composed it once already, before
        // there was anything to draw.
        lifecycleScope.launch {
            refreshWidget(applicationContext, appWidgetId)
            finish()
        }
    }

    /**
     * Which cell was dropped, from the provider that launched this.
     *
     * The options bundle is the obvious source and is unreliable at this
     * moment -- several launchers have not written the size yet when the
     * configuration activity starts. The provider is certain: there is one
     * per size, which is the whole point of having five.
     */
    private fun cellFor(appWidgetId: Int): WidgetSize {
        val name = runCatching {
            AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
        }.getOrNull()
        return when {
            name == null -> WidgetSize.Wide
            name.endsWith("StripWidgetReceiver") -> WidgetSize.Strip
            name.endsWith("SquareWidgetReceiver") -> WidgetSize.Square
            name.endsWith("RowWidgetReceiver") -> WidgetSize.Row
            name.endsWith("TallWidgetReceiver") -> WidgetSize.Tall
            else -> WidgetSize.Wide
        }
    }
}

// ------------------------------------------------------------------ screen

/**
 * The app's own widget page, with the dropped cell applied to it.
 *
 * Everything this app can draw is here, in the same order and the same
 * pictures as under Device. What will not go in the cell that was just
 * dropped is dimmed rather than removed, because a catalogue that quietly
 * drops half its entries answers "where did the sleep widget go" with
 * nothing at all.
 */
@Composable
private fun AddWidget(
    cell: WidgetSize,
    changing: Boolean,
    onCancel: () -> Unit,
    onPick: (WidgetDesign) -> Unit,
) {
    val context = LocalContext.current
    // A made-up person's week, as the catalogue and the tile editor use. The
    // previews are about what a widget *is*; a grid of dashes on a phone
    // that has not synced says nothing about that, and this way the add
    // flow costs no Health Connect read at the moment somebody is waiting.
    val snapshot = remember(context) { sampleWidgetSnapshot(HealthSettings.tiles(context)) }
    val now = remember { System.currentTimeMillis() }
    val inks = remember(context) { widgetColours(context).paperInks }
    val fitting = remember(cell) { WidgetCatalogue.forSize(cell).size }

    Column(
        Modifier
            .fillMaxSize()
            // The status bar and the gesture pill. Inside the app a
            // Scaffold hands every screen these; this one is its own
            // activity drawing edge to edge, so without them the title
            // sat level with the clock and the last row of previews ran
            // under the home indicator.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(if (changing) "Change this widget" else "Add a widget", onBack = onCancel)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text(
                "$fitting fit this space. Tap one to place it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SizeChip(cell)
        }

        WidgetGallery(
            snapshot = snapshot,
            now = now,
            inks = inks,
            fits = { it.fits(cell) },
            onPick = onPick,
        )

        Text(
            "Nothing is placed until you pick one — backing out leaves your home screen as it " +
                "was. Resize it afterwards and it redraws itself to suit the new cell, and " +
                "tapping it opens the screen about what it is showing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
    }
}

@Composable
private fun SizeChip(size: WidgetSize) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = CircleShape,
) {
    Text(
        "${size.label} · ${size.cells}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
