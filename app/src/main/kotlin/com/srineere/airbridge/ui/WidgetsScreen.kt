package com.srineere.airbridge.ui

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.widget.WidgetCatalogue
import com.srineere.airbridge.widget.WidgetConfig
import com.srineere.airbridge.widget.WidgetDesign
import com.srineere.airbridge.widget.WidgetFamily
import com.srineere.airbridge.widget.WidgetSnapshot
import com.srineere.airbridge.widget.WidgetStore
import com.srineere.airbridge.widget.colourFor
import com.srineere.airbridge.widget.faceOf
import com.srineere.airbridge.widget.liveIds
import com.srineere.airbridge.widget.sampleWidgetSnapshot
import com.srineere.airbridge.ui.theme.extras

/**
 * Everything this app can put on a home screen, drawn.
 *
 * A catalogue, not a placing tool. Android has exactly one way to put a
 * widget on a home screen and it belongs to the launcher; an app that offers
 * a second one has two answers to the same question, and the one it owns is
 * the worse of them -- a pinned widget skips the configuration activity, so
 * it arrives knowing nothing about what it is meant to show.
 *
 * What this screen is for is the half the launcher's picker cannot do: show
 * the thirteen tiles and the band faces as pictures rather than as five
 * entries called "Air Bridge", and say honestly which families are drawn on
 * the canvas and not yet built.
 */
@Composable
internal fun WidgetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Reconciled against the launcher rather than trusted: a widget removed
    // while this app was not running leaves its row behind, and a list of
    // widgets that are not there is worse than no list.
    val placed by produceState(initialValue = emptyList<Pair<Int, WidgetConfig>>(), context) {
        val live = liveIds(context)
        WidgetStore.prune(context, live)
        value = WidgetStore.placed(context).filter { it.first in live }
    }
    // A made-up person's week, as the tile editor uses. A catalogue is about
    // what a widget *is*, and a grid of dashes on a phone that has not
    // synced yet says nothing about that -- it also means this screen costs
    // no Health Connect read to open. The colours are the person's own.
    val snapshot = remember(context) { sampleWidgetSnapshot(HealthSettings.tiles(context)) }
    val now = remember { System.currentTimeMillis() }
    val families = remember { WidgetCatalogue.families() }
    val notBuilt = remember { WidgetCatalogue.notBuilt }
    val expanded = remember { mutableStateMapOf<WidgetFamily, Boolean>() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Widgets", onBack = onBack)

        Text(
            "Long-press your home screen, choose Widgets, then Air Bridge. There are five sizes; " +
                "dropping one opens this app, where you pick which of these it shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        if (placed.isNotEmpty()) {
            SectionHeader("On your home screen")
            placed.forEach { (_, config) -> PlacedRow(config, snapshot, now) }
        }

        families.forEach { (family, designs) ->
            val open = expanded[family] == true
            val shown = if (open || designs.size <= SHOWN) designs else designs.take(SHOWN)
            SectionHeader(
                family.label,
                action = when {
                    designs.size <= SHOWN -> null
                    open -> "Fewer"
                    else -> "All ${designs.size}"
                },
                onAction = { expanded[family] = !open },
            )
            Text(
                family.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            )
            PreviewGrid(shown, snapshot, now)
        }

        if (notBuilt.isNotEmpty()) {
            SectionHeader("Other styles")
            Text(
                "Drawn and chosen, not written yet. Each is the same readings in a different " +
                    "hand — the arcs and dials need a bitmap behind them, and Paper assumes " +
                    "a pale wallpaper.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            notBuilt.forEach { ComingRow(it) }
        }

        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "To change a widget you have already placed, long-press it and choose Edit."
            } else {
                "This version of Android has no Edit on a widget's long-press menu, so changing " +
                    "one means taking it off and putting it back."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )

        Text(
            "These previews are a made-up week, like the tile editor's. Widgets refresh about " +
                "every half hour and whenever you leave the app, and none of them ever asks the " +
                "Air for a fresh charge — the band answers one connection at a time, and " +
                "taking it would stop Google Health syncing your steps and sleep.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

/** Two to a row, because one preview a row would be a very long screen. */
@Composable
private fun PreviewGrid(designs: List<WidgetDesign>, snapshot: WidgetSnapshot, now: Long) {
    designs.chunked(2).forEach { pair ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            pair.forEach { design ->
                Column(Modifier.weight(1f)) {
                    val face = faceOf(design, snapshot, now)
                    if (face == null) {
                        // A kind nothing writes still gets a card, because
                        // the catalogue is where someone looks for a reading
                        // they have not turned on yet.
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "–",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.extras.disabled,
                                )
                            }
                        }
                    } else {
                        WidgetPreview(face, snapshot.colourFor(design), Modifier.fillMaxWidth())
                    }
                    Text(
                        design.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    Text(
                        design.summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // An odd design on the last row keeps its half rather than
            // stretching across the screen.
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/** A family that exists on the design canvas and nowhere else yet. */
@Composable
private fun ComingRow(family: WidgetFamily) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(min = 44.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.extras.disabled))
        Column(Modifier.weight(1f)) {
            Text(
                family.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                family.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape) {
            Text(
                "Not yet",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun PlacedRow(config: WidgetConfig, snapshot: WidgetSnapshot, now: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .heightIn(min = 44.dp),
    ) {
        faceOf(config.design, snapshot, now)?.let { face ->
            WidgetPreview(face, snapshot.colourFor(config.design), Modifier.weight(0.6f))
        }
        Column(Modifier.weight(1f)) {
            Text(config.design.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                config.pinned?.let { "${it.label} · ${it.cells}" } ?: "Any size",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How many of a family are shown before the heading offers the rest. */
private const val SHOWN = 4
