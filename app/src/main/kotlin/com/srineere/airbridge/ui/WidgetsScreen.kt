package com.srineere.airbridge.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
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
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import com.srineere.airbridge.widget.WidgetCatalogue
import com.srineere.airbridge.widget.WidgetConfigActivity
import com.srineere.airbridge.widget.WidgetConfig
import com.srineere.airbridge.widget.WidgetDesign
import com.srineere.airbridge.widget.WidgetFamily
import com.srineere.airbridge.widget.WidgetSnapshot
import com.srineere.airbridge.widget.PaperInks
import com.srineere.airbridge.widget.WidgetStore
import com.srineere.airbridge.widget.widgetColours
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
    // Re-read when the screen comes back, because editing one of these
    // leaves and returns: without it a row would still be drawn as the
    // widget was before you changed it.
    val returned = resumeTicks()
    val placed by produceState(initialValue = emptyList<Pair<Int, WidgetConfig>>(), context, returned) {
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
    val inks = remember(context) { widgetColours(context).paperInks }
    val notBuilt = remember { WidgetCatalogue.notBuilt }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Widgets", onBack = onBack)

        if (placed.isNotEmpty()) {
            SectionHeader("On your home screen")
            placed.forEach { (id, config) ->
                PlacedRow(config, snapshot, now) {
                    // The widget's own configuration activity, which is the
                    // same screen the launcher opens when a widget is
                    // dropped. It needs nothing but the id: it reads the
                    // cell and the current choice from there, and writes
                    // and redraws on the way out.
                    context.startActivity(
                        Intent(context, WidgetConfigActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }

        WidgetGallery(snapshot, now, inks)

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Text(
                "This version of Android has no Edit on a widget's long-press menu, so " +
                    "tapping one above is the only way to change it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extras.disabled,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}


/**
 * Every design there is, by family, drawn.
 *
 * The same view in two places on purpose: here, where it is a catalogue to
 * look through, and in the add flow, where dropping a widget opens it to be
 * picked from. Those were two different screens once -- a page of pictures
 * in the app and a list of names in the add flow -- and the list was the
 * poorer of the two at the only moment it mattered, which is when somebody
 * is choosing.
 *
 * [fits] says which designs the cell in hand can take. Anything it refuses
 * stays on the page, dimmed, with the sizes it does take written underneath:
 * filtering it out instead answers "where is the sleep widget" with silence.
 *
 * [onPick] null makes the page a catalogue rather than a chooser. Placing a
 * widget belongs to the launcher, and this app does not offer a second way
 * to do it.
 */
@Composable
internal fun WidgetGallery(
    snapshot: WidgetSnapshot,
    now: Long,
    inks: PaperInks,
    fits: (WidgetDesign) -> Boolean = { true },
    onPick: ((WidgetDesign) -> Unit)? = null,
    /** Where this design sits in a stack being built, counting from one. */
    place: ((WidgetDesign) -> Int?)? = null,
) {
    val families = remember { WidgetCatalogue.families() }
    val expanded = remember { mutableStateMapOf<WidgetFamily, Boolean>() }
    families.forEach { (family, designs) ->
        // What fits comes first. Four previews of a family, three of them
        // greyed, says a family is unavailable when the one that fits was
        // fifth in the list and behind the All button.
        val ordered = designs.sortedBy { if (fits(it)) 0 else 1 }
        val open = expanded[family] == true
        val shown = if (open || ordered.size <= SHOWN) ordered else ordered.take(SHOWN)
        SectionHeader(
            family.label,
            action = when {
                ordered.size <= SHOWN -> null
                open -> "Fewer"
                else -> "All ${ordered.size}"
            },
            onAction = { expanded[family] = !open },
        )
        Text(
            family.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
        PreviewGrid(shown, snapshot, now, inks, fits, onPick, place)
    }
}

/** Two to a row, because one preview a row would be a very long screen. */
@Composable
private fun PreviewGrid(
    designs: List<WidgetDesign>,
    snapshot: WidgetSnapshot,
    now: Long,
    inks: PaperInks,
    fits: (WidgetDesign) -> Boolean = { true },
    onPick: ((WidgetDesign) -> Unit)? = null,
    place: ((WidgetDesign) -> Int?)? = null,
) {
    designs.chunked(2).forEach { pair ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            pair.forEach { design ->
                val available = fits(design)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (onPick != null && available) {
                                Modifier.pressFeedback { onPick(design) }
                            } else {
                                Modifier
                            },
                        )
                        // Dimmed whole, preview and name together: a bright
                        // title over a grey picture reads as a loading card
                        // rather than as one that will not go here.
                        .alpha(if (available) 1f else 0.38f)
                        .padding(4.dp),
                ) {
                    val face = faceOf(design, snapshot, now)
                    if (face == null) {
                        // A kind nothing writes still gets a card, because
                        // the catalogue is where someone looks for a reading
                        // they have not turned on yet.
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
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
                        Box(Modifier.fillMaxWidth()) {
                            WidgetPreview(
                                face,
                                snapshot.colourFor(design),
                                Modifier.fillMaxWidth(),
                                cardless = design.family == WidgetFamily.Type,
                                caps = design.family != WidgetFamily.Tiles,
                            )
                            // Its place in the stack, not a tick: the order
                            // cards were chosen in is the order they are
                            // swiped through, and a tick would not say so.
                            place?.invoke(design)?.let { at ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            "$at",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        design.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                    Text(
                        // What it is, or -- where it will not go in this
                        // cell -- what it would need instead.
                        if (available) design.summary else "Needs " + design.sizes
                            .sortedBy { it.ordinal }
                            .joinToString(" or ") { it.cells },
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
private fun PlacedRow(
    config: WidgetConfig,
    snapshot: WidgetSnapshot,
    now: Long,
    onEdit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .heightIn(min = 44.dp),
    ) {
        faceOf(config.design, snapshot, now)?.let { face ->
            WidgetPreview(
                face,
                snapshot.colourFor(config.design),
                Modifier.weight(0.6f),
                caps = config.design.family != WidgetFamily.Tiles,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (config.designs.size > 1) "A stack of ${config.designs.size}" else config.design.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (config.designs.size > 1) {
                    config.designs.joinToString(" · ") { it.title }
                } else {
                    config.design.summary
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How many of a family are shown before the heading offers the rest. */
private const val SHOWN = 4
