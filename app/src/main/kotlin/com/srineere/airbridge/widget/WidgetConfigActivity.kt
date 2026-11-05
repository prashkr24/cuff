package com.srineere.airbridge.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.AppSettings
import com.srineere.airbridge.ui.ScreenTitle
import com.srineere.airbridge.ui.theme.AirBridgeTheme
import com.srineere.airbridge.ui.theme.ThemeMode
import com.srineere.airbridge.ui.theme.extras
import kotlinx.coroutines.launch

/**
 * The catalogue, which is also the add flow.
 *
 * The launcher reserves a cell and launches this before the widget is
 * placed; nothing exists on the home screen until [setResult] returns OK, so
 * backing out is a cancel and leaves no half-made widget behind. That is
 * also why the size is on the header from the first frame: every list below
 * it is filtered by the cell that was just dropped, and a filter you cannot
 * see reads as a missing widget.
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
                    WidgetSetupFlow(
                        cell = cell,
                        existing = existing,
                        onCancel = ::finish,
                        onConfirm = { config -> place(appWidgetId, config) },
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

// ------------------------------------------------------------------ screens

@Composable
private fun WidgetSetupFlow(
    cell: WidgetSize,
    existing: WidgetConfig?,
    onCancel: () -> Unit,
    onConfirm: (WidgetConfig) -> Unit,
) {
    var chosen by remember { mutableStateOf(existing?.design) }
    var pinned by remember { mutableStateOf(existing?.pinned) }
    var opensTab by remember { mutableStateOf(existing?.opensTab ?: false) }

    val design = chosen
    if (design == null) {
        Catalogue(cell, onCancel) { chosen = it }
    } else {
        Setup(
            design = design,
            cell = cell,
            pinned = pinned,
            opensTab = opensTab,
            onPin = { pinned = it },
            onOpensTab = { opensTab = it },
            onBack = { if (existing == null) chosen = null else onCancel() },
            onCancel = onCancel,
            onConfirm = { onConfirm(WidgetConfig(design, pinned, opensTab)) },
        )
    }
}

@Composable
private fun Catalogue(cell: WidgetSize, onCancel: () -> Unit, onPick: (WidgetDesign) -> Unit) {
    val families = remember(cell) { WidgetCatalogue.families(cell) }
    val total = remember(cell) { WidgetCatalogue.forSize(cell).size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Add a widget", onBack = onCancel)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
            Text(
                "$total fit this space",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SizeChip(cell)
        }

        families.forEach { (family, designs) ->
            Text(
                family.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 2.dp),
            )
            Text(
                family.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            designs.forEach { design -> DesignRow(design) { onPick(design) } }
        }

        Text(
            "Nothing is placed until you confirm — backing out leaves your home screen as it was.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
    }
}

@Composable
private fun DesignRow(design: WidgetDesign, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .heightIn(min = 44.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(swatch(design)),
        )
        Column(Modifier.weight(1f)) {
            Text(design.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                design.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Setup(
    design: WidgetDesign,
    cell: WidgetSize,
    pinned: WidgetSize?,
    opensTab: Boolean,
    onPin: (WidgetSize?) -> Unit,
    onOpensTab: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenTitle(design.title, onBack = onBack)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Text(
                    design.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SizeChip(cell)
            }

            Label("Face")
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Pill("Auto", pinned == null, true) { onPin(null) }
                WidgetSize.entries.forEach { size ->
                    Pill(size.cells, pinned == size, design.fits(size)) {
                        if (design.fits(size)) onPin(size)
                    }
                }
            }
            Note(
                "Auto redraws when you resize it on the home screen. Pin a face and it keeps that " +
                    "one at every size.",
            )

            if (design is WidgetDesign.OfTile) {
                Label("Colour")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(swatch(design)),
                    )
                    Text(
                        "From your ${design.kind.title.lowercase()} tile",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Note("Change it under Edit tiles and the tile and the widget move together.")
            }

            Label("Tapping it")
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Pill(openLabel(design), !opensTab, true) { onOpensTab(false) }
                Pill("The Health tab", opensTab, true) { onOpensTab(true) }
            }

            Note(
                "Refreshes about every half hour, and whenever you open the app. No widget ever " +
                    "asks the Air for a fresh reading — that would cost your steps and sleep " +
                    "their sync.",
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(2f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Add widget")
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Label(text: String) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
)

@Composable
private fun Note(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.extras.disabled,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
)

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

@Composable
private fun androidx.compose.foundation.layout.RowScope.Pill(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val ink = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.extras.disabled
    }
    Surface(
        color = background,
        shape = CircleShape,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp).let {
            if (enabled) it.clip(CircleShape).clickable(onClick = onClick) else it
        },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

private fun openLabel(design: WidgetDesign): String = when (design) {
    is WidgetDesign.OfTile -> design.kind.title
    is WidgetDesign.OfBand -> "The Device tab"
    is WidgetDesign.OfRings -> "The Health tab"
    is WidgetDesign.OfDials -> if (design.face == DialFace.Score) "Sleep" else "The Health tab"
    is WidgetDesign.OfPaper -> if (design.face == PaperFace.Figure) "Steps" else "The Health tab"
    is WidgetDesign.OfType -> when (design.face) {
        TypeFace.Steps -> "Steps"
        TypeFace.Sleep -> "Sleep"
        TypeFace.Battery, TypeFace.Relay -> "The Device tab"
    }
}

@Composable
private fun swatch(design: WidgetDesign): Color {
    val extras = MaterialTheme.extras
    val colour = when (design) {
        is WidgetDesign.OfTile -> design.kind.defaultColour
        is WidgetDesign.OfBand -> TileColour.Accent
        is WidgetDesign.OfRings -> TileColour.Steps
        is WidgetDesign.OfDials -> TileColour.Sleep
        is WidgetDesign.OfType -> design.face.colour
        is WidgetDesign.OfPaper -> TileColour.Sleep
    }
    return when (colour) {
        TileColour.Heart -> extras.heart
        TileColour.Sleep -> extras.sleepRem
        TileColour.Steps -> extras.steps
        TileColour.Hrv -> extras.hrv
        TileColour.Activity -> extras.activity
        TileColour.Energy -> extras.energy
        TileColour.Slate -> extras.disabled
        TileColour.Accent -> MaterialTheme.colorScheme.primary
    }
}
