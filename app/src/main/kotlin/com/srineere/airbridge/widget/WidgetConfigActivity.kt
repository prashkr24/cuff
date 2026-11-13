package com.srineere.airbridge.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.srineere.airbridge.ui.SectionHeader
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
import com.srineere.airbridge.health.HealthEnabled
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
        val stack = isStack(appWidgetId)
        val cell = cellFor(appWidgetId, stack)
        val existing = WidgetStore.read(this, appWidgetId)

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
                        stack = stack,
                        existing = existing,
                        onCancel = ::finish,
                        onPick = { picked -> place(appWidgetId, WidgetConfig(picked), stack) },
                    )
                }
            }
        }
    }

    private fun place(appWidgetId: Int, config: WidgetConfig, stack: Boolean) {
        WidgetStore.write(this, appWidgetId, config)
        WidgetRefresh.schedule(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        // Drawn before finishing, so the widget is never blank on arrival --
        // and by id, because the launcher composed it once already, before
        // there was anything to draw.
        //
        // A stack is told directly rather than looked up: the launcher does
        // not list the id under its provider until this activity returns
        // OK, so asking which provider owns it here answers "none".
        lifecycleScope.launch {
            if (stack) {
                refreshStacks(applicationContext, intArrayOf(appWidgetId))
            } else {
                refreshWidget(applicationContext, appWidgetId)
            }
            finish()
        }
    }

    private fun isStack(appWidgetId: Int): Boolean = providerOf(appWidgetId)
        ?.endsWith("StackWidgetReceiver") == true

    private fun providerOf(appWidgetId: Int): String? = runCatching {
        AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className
    }.getOrNull()

    /**
     * Which cell was dropped, from the provider that launched this.
     *
     * The options bundle is the obvious source and is unreliable at this
     * moment -- several launchers have not written the size yet when the
     * configuration activity starts. The provider is certain: there is one
     * per size, which is the whole point of having five.
     */
    private fun cellFor(appWidgetId: Int, stack: Boolean): WidgetSize {
        // The stack is the one provider without a size of its own, so it is
        // the one that has to ask the options bundle. It is also the one
        // that can afford to: a stack is resizable and recomposes its cards
        // when it moves, so a cell read a moment too early costs a redraw
        // rather than a wrong face.
        if (stack) {
            val options = runCatching {
                AppWidgetManager.getInstance(this).getAppWidgetOptions(appWidgetId)
            }.getOrNull()
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
            if (width > 0 && height > 0) return WidgetSize.nearest(width, height)
            return WidgetSize.Wide
        }
        val name = providerOf(appWidgetId)
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
    stack: Boolean,
    existing: WidgetConfig?,
    onCancel: () -> Unit,
    onPick: (List<WidgetDesign>) -> Unit,
) {
    val context = LocalContext.current
    // A made-up person's week, as the catalogue and the tile editor use. The
    // previews are about what a widget *is*; a grid of dashes on a phone
    // that has not synced says nothing about that, and this way the add
    // flow costs no Health Connect read at the moment somebody is waiting.
    val snapshot = remember(context) { sampleWidgetSnapshot(HealthSettings.tiles(context)) }
    val now = remember { System.currentTimeMillis() }
    val inks = remember(context) { widgetColours(context).paperInks }
    // Read here rather than collected: this is its own activity, started
    // by the launcher, and it is gone again in one choice -- the switch
    // cannot move underneath it.
    val healthOn = remember(context) { HealthEnabled.isOn(context) }
    val fitting = remember(cell, healthOn) { WidgetCatalogue.forSize(cell, healthOn).size }
    val changing = existing != null

    // A stack is built rather than chosen, so the list is held here until
    // there is something to place. Order is the order they were tapped,
    // which is the order they are swiped through -- and can be changed
    // afterwards without starting again.
    val chosen = remember { mutableStateListOf<WidgetDesign>().apply { addAll(existing?.designs.orEmpty()) } }

    Column(
        Modifier
            .fillMaxSize()
            // The status bar and the gesture pill. Inside the app a
            // Scaffold hands every screen these; this one is its own
            // activity drawing edge to edge, so without them the title
            // sat level with the clock and the last row of previews ran
            // under the home indicator.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenTitle(
                when {
                    stack && changing -> "Change this stack"
                    stack -> "Build a stack"
                    changing -> "Change this widget"
                    else -> "Add a widget"
                },
                onBack = onCancel,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Text(
                    if (stack) {
                        "$fitting fit here. Tap as many as you like — they share one cell."
                    } else {
                        "$fitting fit here. Tap one to place it."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SizeChip(cell)
            }

            if (stack && chosen.isNotEmpty()) {
                StackLayers(
                    cards = chosen,
                    onMove = { from, to ->
                        if (to in chosen.indices) chosen.add(to, chosen.removeAt(from))
                    },
                    onRemove = { chosen.removeAt(it) },
                )
            }

            WidgetGallery(
                snapshot = snapshot,
                now = now,
                inks = inks,
                healthOn = healthOn,
                fits = { it.fits(cell) },
                onPick = { design ->
                    if (!stack) {
                        onPick(listOf(design))
                    } else if (!chosen.remove(design)) {
                        // Capped: see MAX_STACK_CARDS. Every card in a stack
                        // is composed and held with its own bitmap, so the
                        // whole catalogue in one cell is a widget that runs
                        // the app out of memory rather than a long stack.
                        if (chosen.size < MAX_STACK_CARDS) chosen.add(design)
                    }
                },
                place = if (!stack) null else {
                    { design -> chosen.indexOf(design).takeIf { it >= 0 }?.plus(1) }
                },
            )

            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }

        if (stack) {
            Button(
                onClick = { onPick(chosen.toList()) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp)
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    when (chosen.size) {
                        0 -> "Pick at least one"
                        1 -> "Add this one"
                        else -> "Add these ${chosen.size}"
                    },
                )
            }
        }
    }
}

/**
 * The cards already in the stack, in the order they will be swiped through.
 *
 * Above the catalogue rather than below it. The catalogue is long, and a
 * list of what you have chosen that only appears after you have scrolled
 * past everything is a list you will not find.
 *
 * Reordering is a pair of arrows rather than a drag. A drag is the nicer
 * gesture and this list lives inside a scrolling column, where a long press
 * that starts a drag and a long press that starts a scroll are the same
 * gesture until it is too late to tell.
 */
@Composable
private fun StackLayers(
    cards: List<WidgetDesign>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    SectionHeader("Your stack")
    Text(
        "Swiped through in this order. Resizing the stack resizes them all.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.extras.disabled,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
    )
    cards.forEachIndexed { index, card ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(22.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Text(
                card.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 6.dp),
            )
            IconButton(
                onClick = { onMove(index, index - 1) },
                enabled = index > 0,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(
                onClick = { onMove(index, index + 1) },
                enabled = index < cards.lastIndex,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Take out of the stack")
            }
        }
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
