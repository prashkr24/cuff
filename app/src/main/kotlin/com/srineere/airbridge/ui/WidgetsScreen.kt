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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.widget.WidgetCatalogue
import com.srineere.airbridge.widget.WidgetConfig
import com.srineere.airbridge.widget.WidgetDesign
import com.srineere.airbridge.widget.WidgetFamily
import com.srineere.airbridge.widget.WidgetSize
import com.srineere.airbridge.widget.WidgetStore
import com.srineere.airbridge.widget.liveIds

/**
 * Everything this app can put on a home screen.
 *
 * A catalogue, not a placing tool. Android has exactly one way to put a
 * widget on a home screen and it belongs to the launcher; an app that offers
 * a second one is an app with two answers to the same question, and the one
 * it owns is the worse of them -- a pinned widget skips the configuration
 * activity, so it arrives knowing nothing about what it is meant to show.
 *
 * What this screen is for is the half the launcher's picker cannot do: show
 * the thirteen tiles and the band faces by name, say which cells each of
 * them fills, and say honestly which families are drawn on the canvas and
 * not yet built. The picker gets five entries, one per size, and no room for
 * any of that.
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
    val families = remember { WidgetCatalogue.families() }
    val notBuilt = remember { WidgetCatalogue.notBuilt }

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
            placed.forEach { (_, config) -> PlacedRow(config) }
        }

        families.forEach { (family, designs) ->
            SectionHeader(family.label)
            Text(
                family.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            designs.forEach { CatalogueRow(it) }
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
            "Widgets refresh about every half hour and whenever you leave the app. None of them " +
                "ever asks the Air for a fresh charge — the band answers one connection at a " +
                "time, and taking it would stop Google Health syncing your steps and sleep.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

/** One thing on offer: what it is, and the cells it fills. */
@Composable
private fun CatalogueRow(design: WidgetDesign) {
    Row(
        // Top, not centre: a row three lines tall centres its dot against
        // the summary, which makes the colour look like it belongs to the
        // sentence rather than to the reading it names.
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(min = 44.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(12.dp).clip(CircleShape).background(swatchFor(design)))
        Column(Modifier.weight(1f)) {
            Text(design.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                design.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                // The sizes it refuses are shown refused rather than left
                // out: "HRV stops at 4x2" is a fact about HRV, and a row
                // that simply lists three cells looks like an oversight.
                WidgetSize.entries.forEach { size -> SizeChip(size, design.fits(size)) }
            }
        }
    }
}

/** A family that exists on the canvas and nowhere else yet. */
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
private fun SizeChip(size: WidgetSize, offered: Boolean) = Surface(
    color = if (offered) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    },
    shape = RoundedCornerShape(7.dp),
) {
    Text(
        size.cells,
        style = MaterialTheme.typography.labelSmall,
        color = if (offered) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.extras.disabled
        },
        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun PlacedRow(config: WidgetConfig) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .heightIn(min = 44.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(swatchFor(config.design)))
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

@Composable
private fun swatchFor(design: WidgetDesign): Color {
    val extras = MaterialTheme.extras
    val colour = when (design) {
        is WidgetDesign.OfTile -> design.kind.defaultColour
        is WidgetDesign.OfBand -> TileColour.Accent
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
