package com.srineere.airbridge.ui

import android.appwidget.AppWidgetManager
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.theme.extras
import com.srineere.airbridge.widget.WidgetConfig
import com.srineere.airbridge.widget.WidgetDesign
import com.srineere.airbridge.widget.WidgetSize
import com.srineere.airbridge.widget.WidgetStore
import com.srineere.airbridge.widget.liveIds
import com.srineere.airbridge.widget.offerWidget

/**
 * What is actually on the home screen.
 *
 * Not a gallery of what could be: the launcher's own picker is where widgets
 * are added, and this app has no business duplicating it. This screen exists
 * for the thing the launcher cannot do on every phone -- change a widget
 * after it has been placed. `WIDGET_FEATURE_RECONFIGURABLE` puts an Edit on
 * the long-press menu from API 31; on 28 to 30 there is no such affordance
 * at all, and without this list the only way to change a widget would be to
 * remove it and put it back.
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Widgets", onBack = onBack)

        if (placed.isEmpty()) {
            Text(
                "Nothing on your home screen yet.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        } else {
            SectionHeader("On your home screen")
            placed.forEach { (_, config) -> PlacedRow(config) }
        }

        SectionHeader("Adding one")
        val canPin = remember(context) {
            AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
        }
        Text(
            if (canPin) {
                "Pick a size and your launcher will offer to place it. It arrives asking what to " +
                    "show; tap it to choose."
            } else {
                "Long-press your home screen, choose Widgets, then Air Bridge. There are five " +
                    "sizes; dropping one opens this app, where you pick what it shows."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            WidgetSize.entries.forEach { size ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .let {
                            if (canPin) {
                                it.clip(RoundedCornerShape(10.dp))
                                    .clickable { offerWidget(context, size) }
                            } else {
                                it
                            }
                        },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            size.cells,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (canPin) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "You can also long-press a widget and choose Edit to change it in place."
            } else {
                "This version of Android has no Edit on a widget's long-press menu, so this list " +
                    "is the only way to change one without taking it off and putting it back."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        Text(
            "Widgets refresh about every half hour and whenever you leave the app. None of them " +
                "ever asks the Air for a fresh charge — the band answers one connection at a " +
                "time, and taking it would stop Google Health syncing your steps and sleep.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PlacedRow(config: WidgetConfig) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .heightIn(min = 44.dp),
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(swatchFor(config.design)))
        Column(Modifier.weight(1f)) {
            Text(config.design.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    config.pinned?.cells ?: "any size",
                    config.design.summary,
                ).joinToString(" · "),
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
