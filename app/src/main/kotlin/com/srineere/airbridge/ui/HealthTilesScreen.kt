package com.srineere.airbridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.srineere.airbridge.health.Tile
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.TILE_COLUMNS
import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.health.TileSize
import com.srineere.airbridge.health.defaultTiles
import com.srineere.airbridge.health.moveTile
import com.srineere.airbridge.health.packTiles
import com.srineere.airbridge.health.unusedKinds
import com.srineere.airbridge.ui.theme.extras

/**
 * Edit tiles.
 *
 * The grid here is the same packer the tab uses, drawn at the same column
 * widths, so what you arrange is what you get. It is deliberately not a list
 * of switches: the thing being chosen is a shape, and a shape has to be seen
 * at its own proportions to be chosen.
 *
 * Every change writes through [HealthState], which means the tab behind this
 * screen is already rearranged by the time you go back. There is no Save,
 * because there is nothing a Save could do that leaving has not done.
 */
@Composable
internal fun HealthTilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = LocalHealthState.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        state.loadTiles(context)
        state.loadGoals(context)
    }
    val tiles = state.tiles
    // Made-up readings, not yours.
    //
    // The question this screen asks is what a size can hold. Real data
    // answers it badly: an empty tile might mean the size is too small or
    // might mean you did not wear the band yesterday, and nothing on screen
    // says which. The sample person slept, walked and ate, so every tile is
    // full and the only thing varying is the shape you are choosing.
    val data = remember { sampleHealthData() }
    val goals = sampleGoals
    // Which tile has its colours open. An index, because two tiles of the
    // same kind cannot both be on the tab and the index is what every edit
    // here is phrased in.
    var picking by remember { mutableStateOf(-1) }

    fun edit(next: List<Tile>) = state.saveTiles(context, next)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "Edit tiles",
            subtitle = "Tap a tile to resize, recolour or remove it",
            onBack = onBack,
        )

        var index = 0
        packTiles(tiles).forEach { row ->
            val first = index
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                row.tiles.forEach { tile ->
                    val at = index++
                    EditTile(
                        tile = tile,
                        data = data,
                        goals = goals,
                        colour = tileColour(tile),
                        picking = picking == at,
                        onPick = { picking = if (picking == at) -1 else at },
                        onMove = { by ->
                            picking = -1
                            edit(moveTile(tiles, at, by))
                        },
                        modifier = Modifier.weight(tile.size.span.toFloat()),
                    )
                }
                val spare = TILE_COLUMNS - row.tiles.sumOf { it.size.span }
                if (spare > 0) Spacer(Modifier.weight(spare.toFloat()))
            }
            // The picker opens under the row its tile is in, so the tile it
            // changes is still on screen while you change it.
            if (picking in first until index) {
                val tile = tiles[picking]
                val at = picking
                TilePanel(
                    title = tile.kind.title,
                    chosen = tile.colour,
                    size = tile.size,
                    sizes = tile.kind.sizes,
                    onSize = { chosen ->
                        edit(tiles.toMutableList().also { list ->
                            list[at] = tile.copy(size = chosen)
                        })
                    },
                    canRemove = tiles.size > 1,
                    onChoose = { choice ->
                        edit(tiles.toMutableList().also { list ->
                            list[at] = tile.copy(colour = choice)
                        })
                    },
                    onRemove = {
                        picking = -1
                        edit(tiles.filterIndexed { index, _ -> index != at })
                    },
                )
            }
        }

        val unused = unusedKinds(tiles)
        if (unused.isNotEmpty()) {
            SectionHeader("Not on your tab")
            unused.forEach { kind ->
                AddRow(kind) {
                    picking = -1
                    edit(tiles + Tile(kind, kind.clamp(TileSize.S)))
                }
            }
        }

        // The one explanation of what a chip means, kept here rather than on
        // every tile: a sentence repeated eight times is eight times the
        // clutter and no clearer the second time.
        SectionHeader("Trends")
        SettingsRow(
            title = "Show trends",
            subtitle = "The arrow and figure beside each tile's name",
            icon = Icons.Outlined.TrendingUp,
            onClick = { state.saveShowTrends(context, !state.showTrends) },
            trailing = {
                RowSwitch(
                    checked = state.showTrends,
                    onCheckedChange = { state.saveShowTrends(context, it) },
                )
            },
        )
        TrendNote(
            "Each tile compares its newest readings with its oldest, across the " +
                "window beside its name.",
        )

        // Three things that act on the whole tab at once. All the same
        // weight, because they are the same kind of thing: a settings row
        // with an icon next to a plain text button made one of them look
        // like a place to go rather than a thing to do.
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp)) {
            SweepButton("Use the app colour on every tile") {
                picking = -1
                edit(tiles.map { it.copy(colour = TileColour.Accent) })
            }
            if (tiles.any { it.colour != null }) {
                SweepButton("Give each tile its own colour back") {
                    picking = -1
                    edit(tiles.map { it.copy(colour = null) })
                }
            }
            SweepButton("Reset to the default layout") {
                picking = -1
                edit(defaultTiles)
            }
        }

        Text(
            "Sleep stages, heart zones and deficit against surplus keep their own " +
                "colours, because theirs mean something.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** One paragraph of the only place this tab explains its own arithmetic. */
@Composable
private fun TrendNote(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
)

/** One of the whole-tab actions at the foot of the screen. */
@Composable
private fun SweepButton(label: String, onClick: () -> Unit) = TextButton(
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
}

/**
 * A tile as it appears while being arranged: a dashed outline at its real
 * proportions, its name, and the controls that change it.
 *
 * The outline is dashed rather than solid so this screen never looks like the
 * tab with buttons stuck on it — you can tell at a glance that nothing here
 * is showing you a reading.
 */
@Composable
private fun EditTile(
    tile: Tile,
    data: HealthData,
    goals: Goals,
    colour: Color,
    picking: Boolean,
    onPick: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            if (picking) 2.dp else 1.dp,
            if (picking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(tileHeight(tile.kind, tile.size) + controlsHeight(tile.size)),
    ) {
        Column {
            // Tapping the tile opens its panel. Removing used to live only
            // behind the colour swatch, which looks like a colour and
            // nothing else, so the way off the tab was effectively hidden.
            // The real tile, drawn from the real data. A name and a sentence
            // could not answer the one question this screen is for: what
            // does that look like at that size, in that colour.
            Box(
                Modifier.height(tileHeight(tile.kind, tile.size))
                    .pressFeedback(onClick = onPick),
            ) {
                TilePreview(tile, data, goals, colour)
            }
            // Only the two that reorder. Size and colour moved into the
            // panel, which is a place with room to show the choices rather
            // than a chip you tap five times to get back where you were --
            // and four targets never did fit across two columns anyway.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().height(CONTROLS),
            ) {
                SmallIcon(Icons.Outlined.ChevronLeft, "Move earlier") { onMove(-1) }
                SmallIcon(Icons.Outlined.ChevronRight, "Move later") { onMove(1) }
            }
        }
    }
}

/** Room under a tile for a row of controls you can actually hit. */
private val CONTROLS = 44.dp

private fun controlsHeight(size: TileSize) = CONTROLS

/**
 * The tile as the tab will draw it, inside the edit screen.
 *
 * Shares no code path with the tab by accident: it calls the same tile
 * body, so a size that looks right here cannot look different there.
 */
@Composable
private fun TilePreview(tile: Tile, data: HealthData, goals: Goals, colour: Color) {
    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // `preview` makes the one live control inside a tile inert. This
        // used to be done by consuming every touch over the preview, which
        // also ate the scroll: a drag that began on a chart left the list
        // sitting still.
        Column { TileBody(tile, data, goals, colour, preview = true) }
    }
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) = IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
    Icon(
        icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

// Both of these used to be about sixteen dots across, which is half what a
// finger needs and a quarter of what one needs on a moving bus.
/**
 * What can be done to one tile beyond moving and resizing it: eight colours
 * — the six the app already uses for a domain, a neutral, and the app's own
 * accent for anyone who wants the tab to be one colour — and the way off the
 * tab.
 *
 * Removing lives here rather than on the tile itself: a close button on
 * every tile is five controls competing for two columns, and the one that
 * throws work away should not be the easiest to hit by accident.
 */
@Composable
private fun TilePanel(
    title: String,
    chosen: TileColour?,
    size: TileSize,
    sizes: Set<TileSize>,
    onSize: (TileSize) -> Unit,
    canRemove: Boolean,
    onChoose: (TileColour?) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (chosen != null) {
                    TextButton(onClick = { onChoose(null) }) { Text("Its own") }
                }
                if (canRemove) {
                    // In the colour that means "this throws something away",
                    // because next to "Its own" it otherwise reads as one
                    // more way to change how the tile looks.
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            PanelLabel("Size")
            // Every size laid out at once, with the ones this tile cannot
            // fill left out. It was a chip that cycled, which meant five
            // taps to undo one and no way to see what was on offer.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                TileSize.entries.filter { it in sizes }.forEach { option ->
                    val on = option == size
                    Surface(
                        color = if (on) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).pressFeedback { onSize(option) },
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }

            // The last swatch is the app's own accent, including a custom
            // one, so a tab can be a single colour. It is a circle like the
            // other seven and nothing said which it was.
            PanelLabel("Colour — ${colourName(chosen)}", top = 10.dp)
            // Shared out rather than fixed at 38dp: eight of those plus the
            // gaps are wider than the panel, and the eighth was being cut in
            // half by the edge of the screen.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TileColour.entries.forEach { option ->
                    val tint = swatchColour(option)
                    Surface(
                        color = tint,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(
                                if (option == chosen) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        RoundedCornerShape(50),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .pressFeedback { onChoose(option) },
                    ) {}
                }
            }
        }
    }
}

/** The one-word heading over a row of choices in the panel. */
@Composable
private fun PanelLabel(text: String, top: Dp = 6.dp) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.extras.disabled,
    modifier = Modifier.padding(top = top),
)

/** Which of the eight is in use, named so the accent one can be found. */
private fun colourName(chosen: TileColour?): String = when (chosen) {
    null -> "its own"
    TileColour.Heart -> "heart red"
    TileColour.Sleep -> "sleep violet"
    TileColour.Steps -> "steps green"
    TileColour.Hrv -> "HRV amber"
    TileColour.Activity -> "activity blue"
    TileColour.Energy -> "energy orange"
    TileColour.Slate -> "slate"
    TileColour.Accent -> "the app's accent"
}

@Composable
private fun swatchColour(option: TileColour): Color {
    val extras = MaterialTheme.extras
    return when (option) {
        TileColour.Heart -> extras.heart
        TileColour.Sleep -> extras.sleepRem
        TileColour.Steps -> extras.steps
        TileColour.Hrv -> extras.hrv
        TileColour.Activity -> extras.activity
        TileColour.Energy -> extras.energy
        TileColour.Slate -> MaterialTheme.colorScheme.onSurfaceVariant
        TileColour.Accent -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun AddRow(kind: TileKind, onAdd: () -> Unit) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().pressFeedback(Motion.PRESS_SCALE_ROW, onClick = onAdd)
        .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
) {
    Text(kind.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    IconButton(onClick = onAdd) {
        Icon(Icons.Outlined.Add, contentDescription = "Add ${kind.title}")
    }
}

