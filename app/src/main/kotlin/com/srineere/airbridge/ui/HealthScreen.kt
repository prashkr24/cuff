package com.srineere.airbridge.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.srineere.airbridge.health.FitnessProfileStore
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.Readiness
import com.srineere.airbridge.health.fitnessAgeOf
import com.srineere.airbridge.health.readinessToday
import com.srineere.airbridge.health.HealthConnect
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.TILE_COLUMNS
import com.srineere.airbridge.health.Tile
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.health.TileSize
import com.srineere.airbridge.health.Trend
import com.srineere.airbridge.health.packTiles
import com.srineere.airbridge.ui.theme.AirExtraColors
import com.srineere.airbridge.ui.theme.extras
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Health tab.
 *
 * Trends are the body of it, which is why there is no range control at the
 * top: the ring is today, the bars are seven days, the hero is thirty, so a
 * single control up here would be wrong for most of the screen. Each tile
 * says its own window instead, and the choosing lives on the detail screens.
 *
 * Nothing here is laid out by hand. The grid is whatever the stored list of
 * tiles says it is, packed into six columns, which is what makes Edit tiles
 * a real screen rather than a settings page that rearranges nothing.
 */
@Composable
internal fun HealthScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val data by healthData(context)
    val state = LocalHealthState.current
    val scope = rememberCoroutineScope()
    // Health Connect owns the permission sheet; coming back from it is
    // the one moment the tab must re-read rather than trust its cache.
    val ask = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { state.refresh(context, force = true) }

    when (data.stage) {
        HealthStage.Loading -> HealthLoading()
        HealthStage.NotConnected ->
            HealthNotConnected(onConnect = { ask.launch(HealthConnect.allPermissions) })
        HealthStage.NeedsUpdate ->
            HealthNotConnected(onConnect = {}, needsUpdate = true)
        HealthStage.Granted -> HealthGrid(data, onOpen)
    }
}

@Composable
private fun HealthLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
}

@Composable
private fun HealthGrid(data: HealthData, onOpen: (String) -> Unit) {
    // One holder for the tab, not one per tile. Each holder gives the band
    // back when it leaves composition, so a second one would stop the first
    // one's reading.
    val measure = rememberMeasure()
    val context = LocalContext.current
    val state = LocalHealthState.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        state.loadTiles(context)
        state.loadGoals(context)
    }
    val goals = state.goals
    val axes = rememberAxes()
    // Resolved here, where the theme is, so the route handler can hand the
    // screen a colour rather than a tile.
    // Remembered: rebuilt every pass it was a new Map each time, so the
    // onOpen lambda below captured a different object on every
    // recomposition and no tile could skip. The grid recomposes once a
    // second for the length of a live heart rate reading.
    val extras = MaterialTheme.extras
    val scheme = MaterialTheme.colorScheme
    val tileColours = remember(state.tiles, extras, scheme) {
        state.tiles.associate { it.kind to tileColour(it, extras, scheme) }
    }

    // The button re-reads and says why that may change nothing. It used to
    // leave for Google Health, which does pull the band -- but a control
    // that throws you into another app is a worse answer than a sentence,
    // and the sentence is the thing that was actually missing.
    val (askSync, syncDialog) = explained(Explain.Sync) { state.refresh(context, force = true) }
    syncDialog()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HealthHeader(
            writtenAt = data.snapshot.writtenAt,
            loading = state.loading,
            onRefresh = askSync,
        )

        var placed = 0
        packTiles(state.tiles).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                row.tiles.forEach { tile ->
                    HealthTile(
                        index = placed++,
                        tile = tile,
                        data = data,
                        goals = goals,
                        axes = axes,
                        measure = measure,
                        onOpen = { route ->
                            state.openedWith = tileColours[tile.kind]?.let { route to it }
                            onOpen(route)
                        },
                        modifier = Modifier.weight(tile.size.span.toFloat()),
                    )
                }
                // A row the person left half full stays half full. Stretching
                // the last tile to close the gap would make the size chip on
                // the edit screen a suggestion rather than a fact.
                val spare = TILE_COLUMNS - row.tiles.sumOf { it.size.span }
                if (spare > 0) Spacer(Modifier.weight(spare.toFloat()))
            }
        }

        // In the order you come back to them: the journal is answered most
        // days, the goals are revisited now and then, and the tab's own
        // arrangement is a thing you settle once. All three sit under the
        // tab rather than taking a corner of the header away from the day's
        // readings.
        SettingsRow(
            title = "Journal",
            subtitle = "What you did before a night, and what it turned out to mean",
            onClick = { onOpen("journal") },
        )
        SettingsRow(
            title = "Goals",
            subtitle = "The targets every goal line here is drawn from",
            onClick = { onOpen("healthgoals") },
        )
        SettingsRow(
            title = "Edit tiles",
            subtitle = "Reorder, resize and recolour what this tab shows",
            onClick = { onOpen("healthtiles") },
        )

        Text(
            freshnessLine(data),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
        Box(Modifier.padding(bottom = 20.dp))
    }
}

/**
 * The tab's title, how old its figures are, and the one control that makes
 * them newer.
 *
 * The button re-reads, and the first press says what that can and cannot
 * do. Re-reading is all there is: Health Connect holds only what Google
 * Health last wrote, and nothing here can make it write. Press it when
 * nothing has synced and it finds the same thing, which is how this was
 * reported -- as the sync not working.
 *
 * It used to open Google Health instead, which does pull the band on
 * launch. That worked and nobody liked it: a control that throws you into
 * somebody else's app to do its job is a worse answer than one sentence
 * saying where readings come from. See [Explain].
 *
 * The age beside it is still the honest answer to "is this current": the
 * newest thing anything has filed, not the last time we asked.
 *
 * The age ticks on its own. Left to redraw only when something else changed,
 * a tab held open kept saying "now" an hour later.
 */
@Composable
private fun HealthHeader(writtenAt: Instant?, loading: Boolean, onRefresh: () -> Unit) {
    val now = nowTicking()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text("Health", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.weight(1f))
        Text(
            // The date orients, the age answers "is this current". Both, and
            // in that order, because the tab is always about today and the
            // age is the thing that changes while you look at it.
            //
            // No age when nothing read carries a write time: only aggregates
            // were granted, and "never" would be a lie -- the steps are
            // right there on the tile.
            today(now) + (writtenAt?.let { " · " + ago(it.toEpochMilli(), now) } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extras.disabled,
        )
        if (loading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp),
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Sync with Google Health",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Tue 4 Nov" here, and whatever that is wherever the phone is set.
 *
 * [now] is the header's own minute hand, from [nowTicking], and it is the
 * whole reason this takes an argument. The date used to be remembered on
 * the pattern alone and then kept for as long as the tab was composed, so a
 * tab left open overnight read "Mon 10 Nov · 3 min ago" on a Tuesday
 * morning -- a frozen date beside an age that had gone on ticking all night
 * next to it. The formatting still only runs when the day actually turns
 * over; what the minute buys is the chance to notice that it has.
 */
@Composable
private fun today(now: Long): String {
    val locale = Locale.getDefault()
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEdMMM") }
    val day = remember(now) { LocalDate.now() }
    return remember(pattern, day) {
        day.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

// ------------------------------------------------------------------ tiles

/**
 * How tall each size draws.
 *
 * Fixed rather than wrapped, because two tiles sharing a row must agree on a
 * height or the grid goes ragged, and a tile whose height follows its content
 * changes size when a number arrives. The two that carry a chart with a scale
 * get a little more, because a gutter and a footer are not free.
 *
 * Fixed in dp, though, is not fixed on the screen: the text inside a tile
 * grows with the system font size and the tile did not. At Largest the
 * heading and the figure ate the whole 112 dp of an S, the chart under them
 * is a `weight(1f)` and was squeezed to nothing, and the tile quietly became
 * a number with no shape behind it. Nothing crashed -- every line is
 * `maxLines = 1` -- which is why it took a font setting to find.
 */
@Composable
internal fun tileHeight(kind: TileKind, size: TileSize): Dp {
    val base = when (size) {
        // S, M and W all stand one row high, and they have to: a wide tile
        // exists to sit beside a small one, and two heights in a row is a
        // ragged grid.
        TileSize.S, TileSize.M, TileSize.W -> 112.dp
        // A chart with a scale needs the room its gutter and footer take, so
        // a tile that carries one is a little taller than one that does not.
        TileSize.L -> if (kind == TileKind.Vitals || kind == TileKind.Activity) 120.dp else 132.dp
        TileSize.XL -> 216.dp
    }
    return base * tileGrowth()
}

/**
 * How much taller a tile stands when the text in it is bigger.
 *
 * Straight proportion, clamped at both ends, and the clamp is the whole
 * decision here.
 *
 * Nothing below 1: a tile set to small text has no more chart to draw than
 * one at normal size, and shrinking the grid on a setting that was asking
 * for smaller *letters* would be answering a question nobody put.
 *
 * Nothing above 1.5 either. Android 14's non-linear font scaling reports up
 * to 2, and at 2 the XL hero would stand 432 dp -- half a phone for one
 * tile, and a tab you scroll a screen at a time to see three readings. 1.5
 * covers the largest setting in Display > Font size, which is where the
 * squeezed chart was actually seen, and leaves the grid a grid above it: a
 * tile a little tight is a better answer than a tab that is a list.
 */
@Composable
private fun tileGrowth(): Float =
    androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.5f)

/**
 * One tile, at whatever size it was given.
 *
 * Size is not a scale factor. A tile at S says one number because that is all
 * two columns can say honestly; the same tile at XL earns its chart, its band
 * and, for steps, the switch that acts on it. Each size is written out rather
 * than derived, because the alternative is a hero with a 9pt label in it.
 */
@Composable
private fun HealthTile(
    index: Int,
    tile: Tile,
    data: HealthData,
    goals: Goals,
    axes: Axes,
    measure: MeasureHolder,
    onOpen: (String) -> Unit,
    modifier: Modifier,
) {
    val colour = tileColour(tile)
    // While a reading is running the heart tile is not a door. It carries a
    // Stop button, and a card that both stops the thing and navigates away
    // is a card you cannot tap confidently.
    val measuring = tile.kind == TileKind.Zones && measure.active
    TileFrame(
        height = tileHeight(tile.kind, tile.size),
        onClick = if (measuring) null else ({ onOpen(tile.kind.route) }),
        modifier = modifier,
        tint = if (measuring) MaterialTheme.extras.heart else null,
        index = index,
    ) {
        TileBody(tile, data, goals, colour, measure = measure)
    }
}

/**
 * What a tile draws, apart from its frame.
 *
 * Split out so the edit screen can show the real thing rather than a sketch
 * of it: a preview that is not the tile is a preview that can be wrong.
 */
@Composable
internal fun ColumnScope.TileBody(
    tile: Tile,
    data: HealthData,
    goals: Goals,
    colour: Color,
    /** True on the edit screen, where nothing drawn should do anything. */
    preview: Boolean = false,
    /** Null on the edit screen, where no reading can be taken. */
    measure: MeasureHolder? = null,
) {
    val axes = rememberAxes()
    when (tile.kind) {
        TileKind.Heart -> HeartTile(tile.size, data, colour, axes)
        TileKind.Sleep -> SleepTile(tile.size, data, colour, goals, axes, tile.colour?.let { stageTones(colour) })
        TileKind.Steps -> StepsTile(tile.size, data, colour, goals, axes)
        TileKind.Energy -> EnergyTile(tile.size, data, colour, goals, axes)
        TileKind.Activity -> ActivityTile(tile.size, data, colour)
        TileKind.Vitals -> VitalsTile(tile.size, data, colour)
        TileKind.Hrv -> HrvTile(tile.size, data, colour)
        TileKind.Nutrition -> NutritionTile(tile.size, data, colour, goals)
        TileKind.Weight -> WeightTile(tile.size, data, colour)
        TileKind.Zones -> ZonesTile(tile.size, data, colour, axes, measure)
        TileKind.Breathing ->
            VitalTile(tile.size, tile.kind, data.breathingTrend, "br/min", colour, 1)
        TileKind.Skin ->
            VitalTile(tile.size, tile.kind, data.skinTrend, "°C", colour, 1, signed = true)
        TileKind.Balance -> BalanceTile(tile.size, data, colour, axes)
        TileKind.Readiness -> ReadinessTile(tile.size, data, colour)
        TileKind.FitnessAge -> FitnessAgeTile(tile.size, data, colour)
        TileKind.Stages -> StagesTile(tile.size, data, colour, axes, tile.colour?.let { stageTones(colour) })
        TileKind.SleepBank -> SleepBankTile(tile.size, data, colour, goals, axes)
    }
}

/**
 * Today's burn and today's food, climbing against each other.
 *
 * The same pair the Burned-against-eaten screen draws, at tile size. Every
 * other tile here is a figure with a chart under it; this one is the chart,
 * because the gap between two lines is the reading and a single number
 * cannot hold it -- that number is already the Energy tile.
 */
@Composable
private fun ColumnScope.BalanceTile(size: TileSize, data: HealthData, colour: Color, axes: Axes) {
    val extras = MaterialTheme.extras
    val snapshot = data.snapshot
    // Worked out when the reading changes, not every time this redraws. It
    // is an array, a filter over every meal of the day and two running
    // totals; on the common path -- a theme change, the header ticking over
    // a minute, a scroll that re-enters composition -- none of it has a new
    // answer to give.
    val day = remember(snapshot.food, snapshot.burnedHourly) {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone).toEpochDay()
        val hourNow = java.time.LocalTime.now(zone).hour
        // Food arrives as entries at a time rather than an hourly total, so
        // the eaten line is built from the entries, as the screen builds it.
        val eatenByHour = DoubleArray(24)
        snapshot.food.filter { it.day == today }.forEach { entry ->
            eatenByHour[entry.at.atZone(zone).hour] += entry.kcal ?: 0.0
        }
        val burnedByHour = snapshot.burnedHourly.takeIf { it.size == 24 } ?: List(24) { 0.0 }
        BalanceDay(
            burned = runningSoFar(burnedByHour, hourNow),
            eaten = runningSoFar(eatenByHour.toList(), hourNow),
            hourNow = hourNow,
        )
    }
    val burned = day.burned
    val eaten = day.eaten

    TileHead(TileKind.Balance, colour, size, window = "today")
    if (burned.lastOrNull().let { it == null || it <= 0.0 } &&
        eaten.lastOrNull().let { it == null || it <= 0.0 }
    ) {
        TileEmpty("Nothing burned or eaten has been recorded today yet.", size)
        return
    }
    val gap = (eaten.lastOrNull() ?: 0.0) - (burned.lastOrNull() ?: 0.0)
    TileValue(
        (if (gap < 0) "−" else "+") + count(abs(gap).roundToInt().toLong()),
        "kcal",
        size,
    )
    TileSub(if (gap < 0) "behind, so far today" else "ahead, so far today")
    RunningTotals(
        first = burned,
        second = eaten,
        firstColour = extras.activity,
        secondColour = extras.energy,
        modifier = Modifier.weight(1f).padding(top = 6.dp),
        height = if (size == TileSize.XL) 92.dp else 46.dp,
        axes = if (size == TileSize.W) null else axes,
        xTicks = if (size == TileSize.W) emptyList() else day.ticks,
    )
}

/** Today's two running totals, worked out once per read rather than per frame. */
private class BalanceDay(
    val burned: List<Double>,
    val eaten: List<Double>,
    hourNow: Int,
) {
    val ticks: List<Pair<Float, String>> = hourTicksOf(hourNow)
}

/** A running total up to the hour it is now, and no further. */
private fun runningSoFar(hours: List<Double>, until: Int): List<Double> {
    var sum = 0.0
    return (0..until.coerceIn(0, 23)).map { hour ->
        sum += hours.getOrElse(hour) { 0.0 }
        sum
    }
}

/** Every sixth hour the day has actually reached. */
private fun hourTicksOf(hourNow: Int): List<Pair<Float, String>> {
    val span = hourNow.coerceAtLeast(1).toFloat()
    return listOf(6, 12, 18).filter { it <= hourNow }
        .map { (it / span) to "%02d".format(it) }
}

/**
 * This morning against your own mornings.
 *
 * The figure and the dots together, never the figure alone: the number
 * means nothing without the run it came out of, which is the same reason
 * its own screen draws the band behind it rather than beside it.
 */
@Composable
private fun ColumnScope.ReadinessTile(size: TileSize, data: HealthData, colour: Color) {
    val context = LocalContext.current
    TileHead(TileKind.Readiness, colour, size, window = "this morning")
    val readiness = remember(data.snapshot) { readinessToday(context, data.snapshot) }
    val trend = readiness.trend
    if (trend == null) {
        TileEmpty("Nothing has written an HRV reading yet.", size)
        return
    }
    // Readiness has always known which morning it describes -- the field
    // and its comment have been there since it was built -- and nothing
    // ever checked it. Under a heading that reads "this morning", a
    // figure worked out from yesterday's HRV is wrong in exactly the way
    // the comment warned about.
    if (readiness.day != LocalDate.now().toEpochDay()) {
        TileEmpty(
            if (data.nightPending) "Measuring overnight."
            else "Waiting for this morning's reading to sync.",
            size,
        )
        return
    }
    TileValue(readiness.score?.toString() ?: "—", null, size, dim = readiness.calibrating)
    if (size != TileSize.S) {
        TileSub(
            if (readiness.calibrating) {
                "calibrating · ${readiness.days} of ${Readiness.NEEDED} nights"
            } else {
                readiness.word
            },
        )
    }
    // The variation rail, not a scatter of dots. A readiness figure is
    // one number against its own band, and the band is the half of it
    // worth drawing -- a month of dots is the HRV tile's picture, and
    // putting it here made two tiles look like the same reading.
    Box(Modifier.weight(1f).padding(top = 8.dp)) {
        BandRows(
            rails = listOf(trend.rail(colour)),
            track = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize(),
            rowHeight = if (size == TileSize.S) 8.dp else 10.dp,
        )
    }
}

/**
 * A VO₂ max in years.
 *
 * No chart. It moves in months, so a run of dots would be four points and
 * a lot of white -- and the two readings under it on its own screen are
 * context rather than inputs, which is not a thing a tile can say.
 */
@Composable
private fun ColumnScope.FitnessAgeTile(size: TileSize, data: HealthData, colour: Color) {
    val context = LocalContext.current
    TileHead(TileKind.FitnessAge, colour, size, window = "monthly")
    val profile = remember(context, resumeTicks()) { FitnessProfileStore.read(context) }
    val fitness = remember(data.snapshot, profile) { fitnessAgeOf(data.snapshot, profile) }
    if (fitness.years == null) {
        TileEmpty(
            if (fitness.vo2 == null) "Nothing has written a VO₂ max yet."
            else "Set your year of birth to read the table.",
            size,
        )
        return
    }
    TileValue(fitness.years.toString(), "years", size)
    if (size != TileSize.S) TileSub(fitness.word)
    fitness.vo2?.let {
        Box(Modifier.weight(1f)) {
            TileSub("VO₂ max ${"%.1f".format(it)}")
        }
    }
}

/**
 * Last night in the proportions it was made of.
 *
 * Not the Sleep tile with one night in it. That tile is about the week a
 * night sits in, which is why its chart is seven bars and a goal line;
 * this one is about the night, so it is one bar and the four stages are
 * the whole point of it.
 */
@Composable
private fun ColumnScope.StagesTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    axes: Axes,
    tones: StageTones? = null,
) {
    val extras = MaterialTheme.extras
    TileHead(TileKind.Stages, colour, size, window = "last night")
    val night = data.lastNight
    if (night == null) {
        // Two silences, told apart. In the small hours the night is one
        // you are still in; by the afternoon it is one that was never
        // written, and "measuring" then would be an excuse.
        TileEmpty(
            if (data.nightPending) "Measuring overnight."
            else "Google Health writes nights once your band syncs.",
            size,
        )
        return
    }
    TileValue(night.asleep.asHoursMinutes(), null, size)
    if (size != TileSize.S) {
        TileSub(
            if (night.hasStages) {
                "deep ${night.deep.asHoursMinutes()} · rem ${night.rem.asHoursMinutes()}"
            } else {
                "written as one block, no stages"
            },
        )
    }
    Box(Modifier.weight(1f).padding(top = 6.dp)) {
        StageBars(
            nights = listOf(night.fourStages()),
            deep = tones?.deep ?: extras.sleepDeep,
            light = tones?.light ?: extras.sleepLight,
            rem = tones?.rem ?: extras.sleepRem,
            awake = tones?.awake ?: extras.sleepAwake,
            modifier = Modifier.fillMaxSize(),
            fill = true,
            axes = if (size == TileSize.S) null else axes,
        )
    }
    // One bar of four colours says nothing without the key, and XL is the
    // only size with a line to spare for it.
    if (size == TileSize.XL) StageKey(tones)
}

/**
 * What a run of nights comes to, against the goal.
 *
 * The figure is the balance and the chart is the nights that made it, which
 * is the same division the Sleep bank screen uses: bars are the working,
 * the number is the answer.
 */
@Composable
private fun ColumnScope.SleepBankTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    goals: Goals,
    axes: Axes,
) {
    TileHead(TileKind.SleepBank, colour, size, window = "7 nights")
    val week = remember(data.nights) { data.nights.takeLast(7) }
    if (week.isEmpty() || goals.sleepMinutes <= 0) {
        TileEmpty("No nights recorded yet, so there is nothing to add up.", size)
        return
    }
    val balance = remember(week, goals.sleepMinutes) {
        week.sumOf { it.asleep.toMinutes() - goals.sleepMinutes }.toDouble()
    }
    val minutes = remember(week) { week.map { it.asleep.toMinutes().toDouble() } }
    TileValue(signedSpan(balance), null, size)
    if (size != TileSize.S) {
        TileSub(
            "${week.size} nights · goal ${goals.sleepMinutes / 60}h " +
                "${"%02d".format(goals.sleepMinutes % 60)}m",
        )
    }
    Box(Modifier.weight(1f).padding(top = 6.dp)) {
        GoalBars(
            values = minutes,
            target = goals.sleepMinutes.toDouble(),
            colour = colour,
            dim = colour.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize(),
            fill = true,
            axes = if (size == TileSize.S) null else axes,
            // Seven finished nights are seven equals; nothing here is
            // "today", so nothing is lit as though it were.
            highlightLast = false,
            yLabel = { "${(it / 60).roundToInt()}h" },
        )
    }
    if (size == TileSize.XL) {
        val over = week.count { it.asleep.toMinutes() >= goals.sleepMinutes }
        Row(Modifier.padding(top = 10.dp)) {
            Stat("Over", "$over", Modifier.weight(1f))
            Stat("Under", "${week.size - over}", Modifier.weight(1f))
            Stat(
                "Longest",
                week.maxOf { it.asleep.toMinutes() }.let { minutes ->
                    "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
                },
                Modifier.weight(1.4f),
            )
        }
    }
}

/** A balance in hours and minutes, signed, as the Sleep bank screen writes it. */
private fun signedSpan(minutes: Double): String {
    val total = minutes.roundToInt()
    val size = abs(total)
    val span = if (size < 60) "${size}m" else "${size / 60}h ${"%02d".format(size % 60)}m"
    return (if (total < 0) "−" else "+") + span
}

/**
 * One nightly reading, for the vitals that are a number and a band.
 *
 * Shared because breathing rate and skin temperature differ only in their
 * units: two near-identical tiles would be two places for the same layout
 * to drift.
 */
@Composable
private fun ColumnScope.VitalTile(
    size: TileSize,
    kind: TileKind,
    trend: Trend?,
    unit: String,
    colour: Color,
    decimals: Int,
    signed: Boolean = false,
) {
    TileHead(kind, colour, size, window = "30 nights") {
        trend?.let { ChangeChip(it.change, unit, colour, size, decimals) }
    }
    if (trend == null) {
        // Two columns cannot hold the long version, and a sentence clipped
        // mid-word tells you less than a short one that finishes.
        TileEmpty(
            if (size == TileSize.S) "Nothing writes this yet."
            else "Nothing writes this to Health Connect yet.",
            size,
        )
        return
    }
    val shown = figure(trend.latest, decimals, signed = false)
    // "14.6 br/min" does not fit two columns and came out as "14.6 br/".
    // The heading already says Breathing, so at S the unit goes rather than
    // being cut in half. The short ones stay.
    TileValue(
        if (signed && trend.latest > 0) "+$shown" else shown,
        unit.takeUnless { size == TileSize.S && it.length > 3 },
        size,
    )
    // Skin temperature is a delta, so its band runs either side of zero, and
    // a dash between two signed numbers reads as a subtraction: "-0.2–0.4".
    // Where a bound is negative the band is spelled out instead.
    val from = figure(trend.usual.low, decimals, signed)
    val to = figure(trend.usual.high, decimals, signed)
    val join = if (trend.usual.low < 0 || trend.usual.high < 0) " to " else "–"
    // The word costs six characters that S does not have, and under a big
    // number a range reads as a range without being introduced.
    TileSub(if (size == TileSize.S) "$from$join$to" else "usual $from$join$to")
    // At every size, including S: HRV draws its fortnight in two columns and
    // these three were leaving the same space blank.
    MiniScatter(
        trend.readings.takeLast(14),
        trend.usual,
        colour,
        Modifier.padding(top = 5.dp),
        when (size) {
            TileSize.XL -> 56.dp
            TileSize.S -> 20.dp
            else -> 26.dp
        },
    )
}

/**
 * One bound of a band, at the decimals that metric is worth.
 *
 * With the typographic minus the rest of the tab uses, not the hyphen the
 * formatter reaches for: "-0.1" beside "+0.2" is two different dashes.
 */
private fun figure(value: Double, decimals: Int, signed: Boolean): String {
    val shown = (if (decimals == 0) value.roundToInt().toString() else formatOneDecimal(value))
        .replace('-', '−')
    return if (signed && value > 0) "+$shown" else shown
}

/**
 * Today's beats.
 *
 * Its own tile rather than a second face of the resting one. Resting heart
 * rate is a single figure a day, read while you slept, and it belongs to a
 * thirty-day trend; this is a live trace that means nothing a week later.
 * One tile cannot be both, and when it tried, tapping it opened a screen
 * about the other.
 */
@Composable
private fun ColumnScope.ZonesTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    axes: Axes,
    measure: MeasureHolder? = null,
) {
    // A reading in progress takes the tile over. Same card, same place: what
    // changed is the question it is answering, so the thirty-second trace
    // stands where today's would have been rather than opening a screen.
    if (measure != null && measure.active && size == TileSize.XL) {
        MeasuringTile(measure, data, colour, axes)
        return
    }
    val samples = data.snapshot.heartToday
    TileHead(TileKind.Zones, colour, size, window = "today")
    if (samples.isEmpty()) {
        TileEmpty("Nothing has written beats today yet.", size)
        return
    }
    // Remembered like every other tile's series. This one was missed, and
    // it is the longest list on the tab: four passes over a whole day of
    // beats, re-run on any recomposition of the grid.
    val (latest, low, high) = remember(samples) {
        Triple(samples.maxByOrNull { it.atMillis }?.bpm ?: 0, samples.minOf { it.bpm }, samples.maxOf { it.bpm })
    }
    val ticks = remember(samples) { hourTicks(samples) }
    val line = @Composable { height: Dp, modifier: Modifier, withAxes: Axes? ->
        DayHeartLine(
            samples = samples,
            colour = colour,
            modifier = modifier,
            height = height,
            axes = withAxes,
            xTicks = if (withAxes == null) emptyList() else ticks,
        )
    }
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileValue(latest.toString(), "bpm", size)
            TileSub("$low–$high today")
            line(22.dp, Modifier.padding(top = 4.dp), null)
        }
        TileSize.L -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.width(92.dp)) {
                TileValue(latest.toString(), "bpm", size)
                TileSub("$low–$high today")
            }
            line(54.dp, Modifier.padding(start = 6.dp), axes)
        }
        TileSize.XL -> {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    TileValue(latest.toString(), "bpm", size)
                    TileSub("$low–$high today")
                }
                // The one reading on this tab the band can answer now, asked
                // for where the live trace will appear.
                //
                // Explained here too. There are three Measure buttons in
                // this app and wiring one of them is wiring none: this is
                // the one on the tab, which is the one most people press.
                if (measure == null) {
                    OutlinedButton(onClick = {}) { Text("Measure") }
                } else {
                    val (ask, dialog) = explained(Explain.MeasureHeart) { measure.start() }
                    dialog()
                    OutlinedButton(onClick = ask) { Text("Measure") }
                }
            }
            Box(Modifier.weight(1f).padding(top = 6.dp)) {
                line(70.dp, Modifier.fillMaxSize(), axes)
            }
        }
    }
}

/**
 * The heart tile while the band is actually sending.
 *
 * Nothing here is read from Health Connect. The band is held open for as
 * long as this is on screen, which is why the card says so and why the
 * countdown is as prominent as the number: this is the one thing on the tab
 * that costs something while you look at it.
 */
@Composable
private fun ColumnScope.MeasuringTile(
    measure: MeasureHolder,
    data: HealthData,
    colour: Color,
    axes: Axes,
) {
    val heart = MaterialTheme.extras.heart
    val failed = measure.state as? Measure.Failed
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (failed == null) {
            Box(
                Modifier.size(8.dp)
                    .background(heart, RoundedCornerShape(50))
                    .padding(end = 6.dp),
            )
            Spacer(Modifier.size(7.dp))
        }
        Text(
            if (failed == null) "MEASURING" else "COULDN'T MEASURE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = heart,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (failed == null && measure.secondsLeft > 0) {
            Text(
                "stops in ${measure.secondsLeft / 60}:${"%02d".format(measure.secondsLeft % 60)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extras.disabled,
                maxLines = 1,
            )
        }
    }
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            when {
                failed != null -> TileSub(failed.message)
                measure.state is Measure.Live -> {
                    val bpm = (measure.state as Measure.Live).bpm
                    TileValue(bpm.toString(), "bpm", TileSize.XL)
                    TileSub(liveLine(bpm, measure, data))
                }
                else -> {
                    TileValue("—", null, TileSize.XL, dim = true)
                    TileSub("Opening the band. The first reading takes a few seconds.")
                }
            }
        }
        if (failed == null) {
            Button(
                onClick = measure.stop,
                modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
            ) { Text("Stop") }
        } else {
            val (askAgain, againDialog) = explained(Explain.MeasureHeart) { measure.start() }
            againDialog()
            OutlinedButton(
                onClick = askAgain,
                modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
            ) { Text("Again") }
        }
    }
    // The last minute of it, which is as much as a tile can show and more
    // than enough to watch a reading settle.
    Box(Modifier.weight(1f).padding(top = 6.dp)) {
        if (measure.samples.size >= 2) {
            // No time ticks: "a minute ago / now" is what a live trace
            // means, and the two labels were taking a line the caution
            // underneath needed more.
            DayHeartLine(
                samples = measure.samples.takeLast(60),
                colour = heart,
                modifier = Modifier.fillMaxSize(),
                height = 56.dp,
                axes = axes,
            )
        }
    }
    if (measure.running) {
        Text(
            "The band is held while this runs, so alerts queue behind it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extras.hrv,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** What the live figure means, against the figure you already had. */
@Composable
private fun liveLine(bpm: Int, measure: MeasureHolder, data: HealthData): String {
    val resting = data.restingTrend?.latest?.roundToInt()
    val gap = resting?.let { bpm - it }
    // Only once it is worth saying. A beat arrives about every second, so
    // "updated 0s ago" was permanent furniture that pushed the sentence it
    // shared a line with off the end of the tile. Silence means fresh; the
    // clause appearing at all means the band has gone quiet.
    val stale = measure.samples.lastOrNull()
        ?.let { (System.currentTimeMillis() - it.atMillis) / 1000 }
        ?.takeIf { it >= 3 }
    return listOfNotNull(
        when {
            gap == null -> null
            gap > 0 -> "$gap above resting"
            gap < 0 -> "${-gap} below resting"
            else -> "at your resting rate"
        },
        stale?.let { "last beat ${it}s ago" },
    ).joinToString(" · ")
}

@Composable
private fun ColumnScope.HeartTile(size: TileSize, data: HealthData, colour: Color, axes: Axes) {
    val trend = data.restingTrend
    TileHead(TileKind.Heart, colour, size, window = "30 days") {
        // Two columns leave the chip room for its arrow and nothing else,
        // and an arrow without its figure says only "something changed".
        if (trend != null) ChangeChip(trend.change, "bpm", colour, size)
    }
    if (trend == null) {
        TileEmpty("Three nights gives a trend; a month gives a useful one.", size)
        return
    }
    val usual = "usual ${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}"
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileValue(trend.latest.roundToInt().toString(), "bpm", size)
            MiniScatter(trend.readings, trend.usual, colour, Modifier.padding(top = 4.dp), 24.dp)
        }
        TileSize.L -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.width(92.dp)) {
                TileValue(trend.latest.roundToInt().toString(), "bpm", size)
                TileSub(usual)
            }
            ScatterTrend(
                trend = trend,
                colour = colour,
                modifier = Modifier.padding(start = 6.dp),
                height = 52.dp,
                axes = axes,
            )
        }
        TileSize.XL -> {
            // Measure used to live here, which put the band's live answer
            // on the tile about a figure read while you slept. It belongs on
            // the heart rate tile, where the live trace is.
            TileValue(trend.latest.roundToInt().toString(), "bpm", size)
            TileSub(usual)
            // The hero is the one tile with room for a scale, and a scale is
            // what turns "it went down" into "it went down two beats".
            ScatterTrend(
                trend = trend,
                colour = colour,
                modifier = Modifier.weight(1f).padding(top = 6.dp),
                fill = true,
                axes = axes,
                xTicks = dayTicks(trend.readings),
            )
        }
    }
}

@Composable
private fun ColumnScope.SleepTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    goals: Goals,
    axes: Axes,
    /** Set only when this tile has been given a colour of its own. */
    tones: StageTones? = null,
) {
    val extras = MaterialTheme.extras
    val last = data.lastNight
    // The score is last night's; the change chip in the heading is the
    // fortnight's drift. Two chips side by side read as one pair of related
    // numbers, which they are not, so the score sits under the chart with
    // the stage key instead -- where there was room for it to be legible.
    val score = if (size == TileSize.XL) data.sleepScore?.total else null
    TileHead(TileKind.Sleep, colour, size, window = "${data.nights.size} nights") {
        data.sleepTrend?.let { ChangeChip(it.change, "min", colour, size) }
    }
    if (last == null) {
        TileEmpty(
            if (data.nightPending) "Measuring overnight."
            else "Google Health writes nights once your band syncs.",
            size,
        )
        return
    }
    val week = remember(data.nights) { data.nights.takeLast(7) }
    // The stages and the weekday ticks are the same seven nights however
    // many times this lambda is invoked, and it is invoked once per size
    // branch per composition.
    val stages = remember(week) { week.map { it.fourStages() } }
    val sleepTicks = remember(week) { weekdayTicks(week.map { it.day }) }
    val bars = @Composable { height: Dp, modifier: Modifier, fill: Boolean, withAxes: Axes? ->
        StageBars(
            nights = stages,
            deep = tones?.deep ?: extras.sleepDeep,
            light = tones?.light ?: extras.sleepLight,
            rem = tones?.rem ?: extras.sleepRem,
            awake = tones?.awake ?: extras.sleepAwake,
            modifier = modifier,
            height = height,
            fill = fill,
            axes = withAxes,
            xTicks = if (withAxes == null) emptyList() else sleepTicks,
            goalMinutes = goals.sleepMinutes.toDouble(),
            goalColour = colour,
            goalLabel = "${goals.sleepMinutes / 60}h",
        )
    }
    val efficiency = last.efficiency?.let { "${(it * 100).roundToInt()}% efficient" }
    val sleptEnough = goals.sleepMinutes > 0 && last.asleep.toMinutes() >= goals.sleepMinutes
    val sleepMark = colour.takeIf { sleptEnough }
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileValue(last.asleep.asHoursMinutes(), null, size, met = sleepMark)
            // W is two thirds of a row and was leaving the bottom third of
            // itself empty: the width buys a line of text and taller bars.
            if (size != TileSize.S) efficiency?.let { TileSub(it) }
            bars(
                if (size == TileSize.W) 32.dp else 24.dp,
                Modifier.padding(top = 4.dp),
                false,
                null,
            )
        }
        TileSize.L -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.width(92.dp)) {
                TileValue(last.asleep.asHoursMinutes(), null, size, met = sleepMark)
                efficiency?.let { TileSub(it) }
            }
            bars(54.dp, Modifier.padding(start = 6.dp), false, axes)
        }
        TileSize.XL -> {
            // The score reads against the night it scores, so it sits at the
            // far end of the line the hours are on, big enough to be the
            // second thing the tile says.
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    TileValue(last.asleep.asHoursMinutes(), null, size, met = sleepMark)
                    efficiency?.let { TileSub(it) }
                }
                if (score != null) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 10.dp, bottom = 2.dp),
                    ) {
                        Text(
                            score.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colour,
                            maxLines = 1,
                        )
                        Text(
                            "score",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).padding(top = 6.dp)) {
                bars(70.dp, Modifier.fillMaxSize(), true, axes)
            }
            StageKey(tones)
        }
    }
}

@Composable
private fun ColumnScope.StepsTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    goals: Goals,
    axes: Axes,
) {
    val week = remember(data.snapshot.steps) { data.snapshot.steps.takeLast(7) }
    val values = remember(week) { week.map { it.value } }
    val stepTicks = remember(week) { weekdayTicks(week.map { it.day }) }
    val average = remember(values) { values.average().takeIf { values.isNotEmpty() } }
    val met = remember(week, goals.steps) { week.count { it.value >= goals.steps } }
    val percent = (data.stepProgress(goals.steps) * 100).roundToInt()
    // stepsToGo coerces at zero, so a goal of zero would read as met.
    val metToday = goals.steps > 0 && data.stepsToday >= goals.steps
    val mark = colour.takeIf { metToday }
    val summary = buildString {
        average?.let { append("avg ${count(it.roundToInt().toLong())}") }
        if (average != null) append(" · ")
        append(
            when (met) {
                0 -> "goal not met"
                1 -> "goal met once"
                else -> "goal met $met days"
            },
        )
    }
    val bars = @Composable { height: Dp, modifier: Modifier, withAxes: Axes? ->
        GoalBars(
            values = values,
            target = goals.steps.toDouble(),
            colour = colour,
            dim = colour.copy(alpha = 0.35f),
            modifier = modifier,
            height = height,
            fill = height == 0.dp,
            axes = withAxes,
            xTicks = if (withAxes == null) emptyList() else stepTicks,
            goalLabel = shortNumber(goals.steps.toDouble()),
        )
    }
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileHead(TileKind.Steps, colour, size, window = "7 days") {
                stepShare(data)?.let { ChangeChip(it, "%", colour, size) }
            }
            TileValue(count(data.stepsToday), null, size, met = mark)
            TileSub(if (metToday) "goal met today" else "$percent% of ${count(goals.steps.toLong())}")
            bars(22.dp, Modifier.padding(top = 4.dp), null)
        }
        // XL is twice the height, and the L layout simply sat in the top
        // half of it. It gets its own: the ring and the figure on one line,
        // then the week given all the room that is left.
        TileSize.XL -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(
                    progress = data.stepProgress(goals.steps),
                    colour = colour,
                    track = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(72.dp),
                ) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    TileHead(TileKind.Steps, colour, size, window = "7 days") {
                        stepShare(data)?.let { ChangeChip(it, "%", colour, size) }
                    }
                    TileValue(count(data.stepsToday), "steps", size, met = mark)
                    TileSub(summary)
                }
            }
            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                bars(0.dp, Modifier.fillMaxSize(), axes)
            }
        }
        TileSize.L -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(
                    progress = data.stepProgress(goals.steps),
                    colour = colour,
                    track = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(64.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            count(data.stepsToday),
                            style = MaterialTheme.typography.labelSmall,
                            color = colour,
                            maxLines = 1,
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    TileHead(TileKind.Steps, colour, size, window = "7 days") {
                        stepShare(data)?.let { ChangeChip(it, "%", colour, size) }
                    }
                    bars(56.dp, Modifier.padding(top = 4.dp), axes)
                    TileSub(summary)
                }
            }
        }
    }
}

/**
 * The one trend shown as a percentage.
 *
 * "318 fewer steps" means nothing without knowing what a usual day is, so
 * the drift is given as a share of the window average. Written once because
 * three sizes ask for it and three copies drifted apart the first time.
 */
private fun stepShare(data: HealthData): Double? = data.stepsTrend
    ?.let { if (it.average > 0) it.change / it.average * 100 else 0.0 }

@Composable
private fun ColumnScope.EnergyTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    goals: Goals,
    axes: Axes,
) {
    val balance = data.energyBalance
    // The tile is today's balance; the chip is which way the balance has
    // been going, which is the only part of it a single day cannot show.
    TileHead(TileKind.Energy, colour, size, window = "today") {
        data.energyTrend?.let { ChangeChip(it.change, "kcal", colour, size) }
    }
    if (balance == null) {
        TileValue("—", null, size, dim = true)
        TileSub("no food logged")
        return
    }
    // Today, split into what went in and what went out -- not the week it
    // sits in. The figure above is a single day's balance, and a seven-day
    // chart under it was answering a different question from the number it
    // belonged to. The widgets drew the split first; this is that drawing.
    val extras = MaterialTheme.extras
    val eaten = data.snapshot.eatenToday ?: 0.0
    val burned = data.snapshot.burnedToday ?: 0.0
    val split = listOf(eaten to extras.hrv, burned to extras.energy)
    val inAndOut = "%s in · %s out".format(
        count(eaten.roundToInt().toLong()),
        count(burned.roundToInt().toLong()),
    )
    // Two columns cannot hold "−1,418" at this size, and a clipped number
    // is a wrong number: at S and M the figure is abbreviated instead.
    val full = (if (balance < 0) "−" else "+") + count(abs(balance).roundToInt().toLong())
    val short = if (abs(balance) >= 1000) {
        (if (balance < 0) "−" else "+") + "%.1fk".format(abs(balance) / 1000)
    } else {
        full
    }
    val word = if (balance < 0) "Deficit" else "Surplus"
    @Suppress("UNUSED_EXPRESSION") axes
    @Suppress("UNUSED_EXPRESSION") goals
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileValue(short, null, size)
            TileChip(word, colour)
            SplitBar(split, Modifier.padding(top = 7.dp), height = 10.dp)
        }
        TileSize.L -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.width(92.dp)) {
                TileValue(full, "kcal", size)
                TileChip(word, colour)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                SplitBar(split, height = 12.dp)
                Box(Modifier.padding(top = 6.dp)) { TileSub(inAndOut) }
            }
        }
        TileSize.XL -> {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    TileValue(full, "kcal", size)
                    TileChip(word, colour)
                }
            }
            // Centred in what is left rather than pinned under the figure:
            // a 16dp bar at the top of a 216dp card is a card with a hole
            // in it.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column {
                    SplitBar(split, height = 16.dp)
                    Box(Modifier.padding(top = 8.dp)) { TileSub(inAndOut) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ActivityTile(size: TileSize, data: HealthData, colour: Color) {
    val sessions = data.snapshot.sessions
    val minutes = sessions.sumOf { Duration.between(it.start, it.end).toMinutes() }
    TileHead(TileKind.Activity, colour, size, window = "7 days") {
        data.activityTrend?.let { ChangeChip(it.change, "min", colour, size) }
    }
    if (sessions.isEmpty()) {
        TileValue("—", null, size, dim = true)
        TileSub("no workouts")
        return
    }
    // One bar per workout, which is not the same list as the total above.
    val perSession = remember(sessions) {
        sessions.map { Duration.between(it.start, it.end).toMinutes().toDouble() }
    }
    val bars = @Composable { height: Dp, modifier: Modifier, fill: Boolean ->
        GoalBars(
            values = perSession,
            target = null,
            colour = colour,
            dim = colour.copy(alpha = 0.45f),
            modifier = modifier,
            height = height,
            fill = fill,
        )
    }
    val count = if (sessions.size == 1) "1 session" else "${sessions.size} sessions"
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileValue(minutes.toString(), "min", size)
            TileChip(count, colour)
            bars(26.dp, Modifier.padding(top = 5.dp), false)
        }
        else -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.width(96.dp)) {
                TileValue(minutes.toString(), "min", size)
                TileSub(count)
            }
            bars(42.dp, Modifier.padding(start = 12.dp), false)
        }
    }
}

@Composable
private fun ColumnScope.VitalsTile(size: TileSize, data: HealthData, colour: Color) {
    val extras = MaterialTheme.extras
    val vitals = data.vitals
    TileHead(TileKind.Vitals, colour, size)
    if (vitals.isEmpty()) {
        TileValue("—", null, size, dim = true)
        TileSub("nothing yet")
        return
    }
    val inRange = vitals.count { it.latestIsUsual }
    // Four rails, in the order the Vitals screen lists them, so a metric
    // nothing writes keeps its empty place rather than shifting the rest up.
    val palette = listOf(
        data.restingTrend to extras.heart,
        data.hrvTrend to extras.hrv,
        data.breathingTrend to extras.activity,
        data.skinTrend to extras.energy,
    )
    val railsOf = remember(palette) { palette.map { (trend, tint) -> trend?.rail(tint) } }
    val rails = @Composable { modifier: Modifier, rowHeight: Dp ->
        BandRows(
            rails = railsOf,
            track = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier,
            rowHeight = rowHeight,
            gap = rowHeight,
        )
    }
    // The number and the rails read as one sentence -- "three of four, and
    // here is where each sits" -- so they share a line rather than stacking
    // with the right half of the tile left empty.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Column(Modifier.width(if (size == TileSize.S) 52.dp else 76.dp)) {
            TileValue(inRange.toString(), "of ${vitals.size}", size)
            TileSub("in range")
        }
        rails(Modifier.weight(1f).padding(start = 6.dp), if (size == TileSize.S) 4.dp else 5.dp)
    }
}

@Composable
private fun ColumnScope.HrvTile(size: TileSize, data: HealthData, colour: Color) {
    val trend = data.hrvTrend
    TileHead(TileKind.Hrv, colour, size, window = "14 nights") {
        if (trend != null) ChangeChip(trend.change, "ms", colour, size)
    }
    if (trend == null) {
        TileEmpty("Your band writes this overnight.", size)
        return
    }
    // A fortnight, not the month: thirty dots in two columns is a smudge,
    // and the tile's job is to say roughly where you are and offer the screen
    // that can actually hold thirty.
    val recent = remember(trend) { trend.readings.takeLast(14) }
    when (size) {
        TileSize.S, TileSize.M, TileSize.W -> {
            TileValue(trend.latest.roundToInt().toString(), "ms", size)
            MiniScatter(recent, trend.usual, colour, Modifier.padding(top = 5.dp), 24.dp)
        }
        else -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.width(92.dp)) {
                TileValue(trend.latest.roundToInt().toString(), "ms", size)
                TileSub("usual ${trend.usual.low.roundToInt()}–${trend.usual.high.roundToInt()}")
            }
            MiniScatter(recent, trend.usual, colour, Modifier.padding(start = 6.dp), 52.dp)
        }
    }
}

@Composable
private fun ColumnScope.NutritionTile(
    size: TileSize,
    data: HealthData,
    colour: Color,
    goals: Goals,
) {
    val eaten = data.snapshot.eatenToday
    TileHead(TileKind.Nutrition, colour, size, window = "today") {
        data.nutritionTrend?.let { ChangeChip(it.change, "kcal", colour, size) }
    }
    if (eaten == null) {
        TileValue("—", null, size, dim = true)
        TileSub("nothing logged today")
        return
    }
    TileValue(count(eaten.roundToInt().toLong()), "kcal", size)
    val protein = data.snapshot.proteinToday
    // The goal on this tile belongs to the protein figure, not to the
    // calories above it, so the mark goes on the line that carries it.
    val proteinMet = goals.proteinGrams?.let { target ->
        protein != null && protein >= target
    } == true
    TileSub(
        met = colour.takeIf { proteinMet },
        text = when {
            protein == null -> "no macros logged"
            // The word costs eight characters S does not have, and the
            // heading already says this tile is about food.
            goals.proteinGrams != null && size == TileSize.S ->
                "${protein.roundToInt()} of ${goals.proteinGrams} g"
            goals.proteinGrams != null ->
                "${protein.roundToInt()} of ${goals.proteinGrams} g protein"
            // Three macros do not fit three columns, and a truncated
            // "P 18 · C 60 ..." says less than one figure said whole.
            size.span <= 3 -> "${protein.roundToInt()} g protein"
            else -> listOfNotNull(
                "P ${protein.roundToInt()}",
                data.snapshot.carbsToday?.let { "C ${it.roundToInt()}" },
                data.snapshot.fatToday?.let { "F ${it.roundToInt()}" },
            ).joinToString(" · ")
        },
    )
    // Today's plate, in its three macros -- the same split the widget
    // draws. A week of intake bars under a figure that says "410 kcal
    // today" was a chart about a different day from the number above it.
    val extras = MaterialTheme.extras
    val carbs = data.snapshot.carbsToday ?: 0.0
    val fat = data.snapshot.fatToday ?: 0.0
    val macros = listOf(
        (protein ?: 0.0) to extras.activity,
        carbs to extras.hrv,
        fat to extras.heart,
    )
    if (size == TileSize.S) return
    // Grams, not calories: the three are being compared with each other,
    // and a gram of fat against a gram of carbohydrate is the comparison
    // the plate is actually made of.
    if (macros.none { it.first > 0.0 }) {
        // Nothing to divide. The week is still true, so it stands in.
        GoalBars(
            values = data.snapshot.eatenDaily.takeLast(7).map { it.value },
            target = null,
            colour = colour,
            dim = colour.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 5.dp),
            height = if (size == TileSize.XL) 60.dp else 28.dp,
        )
        return
    }
    if (size == TileSize.XL) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column {
                SplitBar(macros, height = 16.dp)
                Box(Modifier.padding(top = 8.dp)) { TileSub(macroKey(protein, carbs, fat)) }
            }
        }
        return
    }
    SplitBar(
        macros,
        Modifier.padding(top = 7.dp),
        height = if (size == TileSize.L) 12.dp else 10.dp,
    )
}

/** The three macros written out, for the sizes with a line to spare. */
private fun macroKey(protein: Double?, carbs: Double, fat: Double): String = listOfNotNull(
    protein?.let { "P ${it.roundToInt()}" },
    carbs.takeIf { it > 0.0 }?.let { "C ${it.roundToInt()}" },
    fat.takeIf { it > 0.0 }?.let { "F ${it.roundToInt()}" },
).joinToString(" · ")

@Composable
private fun ColumnScope.WeightTile(size: TileSize, data: HealthData, colour: Color) {
    val trend = data.weightTrend
    TileHead(TileKind.Weight, colour, size, window = "30 days") {
        // A kilo of drift is a month of eating; rounding it to whole kilos
        // reports most real changes as Steady.
        trend?.let { ChangeChip(it.change, "kg", colour, size, decimals = 1) }
    }
    if (trend == null) {
        TileEmpty("Nothing has written a weight yet.", size)
        return
    }
    TileValue(formatOneDecimal(trend.latest), "kg", size)
    val band = "${formatOneDecimal(trend.usual.low)}–${formatOneDecimal(trend.usual.high)}"
    TileSub(if (size == TileSize.S) band else "usual $band")
    if (size != TileSize.S) {
        MiniScatter(
            trend.readings,
            trend.usual,
            colour,
            Modifier.padding(top = 5.dp),
            if (size == TileSize.XL) 60.dp else 26.dp,
        )
    }
}

// ----------------------------------------------------------------- pieces

@Composable
private fun TileFrame(
    height: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    /** A colour the whole card wears while something live is happening in it. */
    tint: Color? = null,
    /** Its place on the tab, which is the order the grid fills in. */
    index: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = rememberPressSource()
    Surface(
        color = tint?.copy(alpha = 0.10f)?.compositeOver(MaterialTheme.colorScheme.surfaceContainer)
            ?: MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, tint?.copy(alpha = 0.45f) ?: MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .height(height)
            .then(if (onClick == null) Modifier else Modifier.pressBounce(interaction))
            .springIn(index),
    ) {
        Column(
            Modifier
                .then(
                    if (onClick == null) Modifier
                    else Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content,
        )
    }
}

/**
 * A tile's heading: its name, its window when it has one, and whatever the
 * tile wants on the right. At S there is no room for a window, so it goes
 * rather than being shortened into something nobody can read.
 */
@Composable
private fun TileHead(
    kind: TileKind,
    colour: Color,
    size: TileSize,
    window: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    // The name gives way, not the chip. Without the weight here the title
    // took whatever width it wanted and the chip was squeezed into a circle
    // with its figure crushed out of it -- an arrow pointing at nothing.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Text(
            // "Resting heart rate" is three words too many for anything
            // short of a full row, and the kind knows which to keep.
            if (size.span < TILE_COLUMNS) kind.short else kind.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The window goes before the name does: of name, window and chip it
        // is the one the screen behind the tile repeats in full.
        if (window != null && size.span >= TILE_COLUMNS) {
            Text(
                " · $window",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extras.disabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // Every trailing slot on this tab holds the tile's change chip, so one
    // switch turns the lot off.
    if (LocalHealthState.current.showTrends) trailing()
}

@Composable
private fun ColumnScope.TileValue(
    value: String,
    unit: String?,
    size: TileSize,
    dim: Boolean = false,
    /** The metric's colour when its goal is met today, and null when not. */
    met: Color? = null,
) = Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
    AnimatedFigure(value) { shown ->
        Text(
            shown,
            style = when {
                size == TileSize.XL -> MaterialTheme.typography.displaySmall
                // A long figure in two columns steps down rather than clipping.
                size == TileSize.S && shown.length > 5 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.headlineSmall
            },
            fontWeight = FontWeight.SemiBold,
            color = if (dim) MaterialTheme.extras.disabled else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
    if (unit != null) {
        Text(
            " $unit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(bottom = if (size == TileSize.XL) 6.dp else 2.dp),
        )
    }
    if (met != null) {
        Spacer(Modifier.width(6.dp))
        GoalMark(
            met,
            size = if (size == TileSize.XL) 18.dp else 15.dp,
            // The figure beside it is already read out; a second voice
            // saying "goal met" after every number is noise.
            description = null,
        )
    }
}

/**
 * A tile's second line. [met] marks the goal that line is about -- the
 * protein figure on the food tile, say, where the big number above it is
 * calories and has no goal of its own.
 */
@Composable
private fun TileSub(text: String, met: Color? = null) = if (met == null) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
} else {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GoalMark(met, size = 12.dp, description = null)
        Text(
            " $text",
            style = MaterialTheme.typography.labelSmall,
            color = met,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TileChip(text: String, colour: Color) = Surface(
    color = colour.copy(alpha = 0.14f),
    shape = RoundedCornerShape(50),
    modifier = Modifier.padding(top = 3.dp),
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = colour,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
private fun TileEmpty(text: String, size: TileSize) = Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = if (size == TileSize.S) 3 else 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.padding(top = 6.dp),
)

/** Which stage is which, for the one tile big enough to say so. */
@Composable
internal fun StageKey(tones: StageTones? = null) {
    val extras = MaterialTheme.extras
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        listOf(
            // The key has to match the bars, or recolouring the chart
            // turns the legend into a lie about which block is which.
            "Deep" to (tones?.deep ?: extras.sleepDeep),
            "Light" to (tones?.light ?: extras.sleepLight),
            "REM" to (tones?.rem ?: extras.sleepRem),
            "Awake" to (tones?.awake ?: extras.sleepAwake),
        ).forEach { (label, tint) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp)
                        .padding(end = 0.dp)
                        .then(Modifier),
                ) {
                    Surface(color = tint, shape = RoundedCornerShape(2.dp)) {
                        Box(Modifier.size(7.dp))
                    }
                }
                Text(
                    " $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The first screen on this tab for everyone, so it is a real screen and not
 * an error: it says what it would show and asks for the two things it needs.
 */
@Composable
private fun HealthNotConnected(onConnect: () -> Unit, needsUpdate: Boolean = false) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Health")
        Text(
            if (needsUpdate) {
                "Health Connect on this phone is too old to read from. Updating it " +
                    "from the Play Store is all this needs."
            } else {
                "Your Air already writes to Health Connect through Google Health. " +
                    "CUFF reads it back as trends — where each night sits " +
                    "against your own usual — and adds the one thing Google Health " +
                    "cannot do from here: buzz your wrist."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SettingsRow(
            title = if (needsUpdate) "Update Health Connect" else "Allow Health Connect",
            subtitle = "Steps, sleep, heart and workouts. Read only, apart from food you log here.",
            onClick = onConnect,
        )
        Text(
            "The relay works with none of this granted. Nothing read here leaves your phone.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extras.disabled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

// ---------------------------------------------------------------- shared

@Composable
internal fun Kicker(text: String, colour: Color) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp,
    color = colour,
)

@Composable
internal fun BigNumber(text: String) = Text(
    text,
    style = MaterialTheme.typography.displaySmall,
    fontWeight = FontWeight.SemiBold,
)

/**
 * A change, tinted with its own metric's colour and never with green or red.
 * Down is good for resting heart rate, up is good for steps, and this app is
 * not qualified to say which way HRV should go -- so the arrow carries the
 * direction and the colour carries nothing but which metric it belongs to.
 */
@Composable
internal fun ChangeChip(
    change: Double,
    unit: String,
    colour: Color,
    size: TileSize = TileSize.L,
    /** Weight and skin temperature move in tenths; rounding them hides it. */
    decimals: Int = 0,
) {
    val shown = if (decimals == 0) abs(change).roundToInt().toString()
    else formatOneDecimal(abs(change))
    val steady = shown.none { it in '1'..'9' }
    val up = change > 0
    // Two columns hold an arrow and nothing else. The figure is dropped
    // rather than shrunk, because the direction is the half that still means
    // something at a glance, and a 7pt number means nothing at any distance.
    val label = when {
        steady && size == TileSize.S -> "–"
        steady -> "Steady"
        size == TileSize.S -> if (up) "↑" else "↓"
        // At M the unit goes instead of the number: the heading says what is
        // being measured and the value below carries the unit, so "↓ 2" is
        // the only part that is not already on the tile.
        size == TileSize.M -> (if (up) "↑ " else "↓ ") + shown
        else -> (if (up) "↑ " else "↓ ") + shown +
            (if (unit.isEmpty()) "" else if (unit == "%") unit else " $unit")
    }
    val tight = size == TileSize.S || size == TileSize.M
    Surface(color = colour.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            style = if (tight) MaterialTheme.typography.labelMedium
            else MaterialTheme.typography.labelLarge,
            fontWeight = if (steady) FontWeight.SemiBold else FontWeight.Bold,
            color = colour,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = if (tight) 6.dp else 10.dp,
                vertical = if (tight) 2.dp else 3.dp,
            ),
        )
    }
}

// Built once rather than per call. Both of these are called from a dozen
// tiles on every composition of the tab, and each call was a locale lookup
// and a DecimalFormat pattern parse. NumberFormat is not thread-safe, but
// every caller here is a composable on the main thread.
private val integers by lazy { NumberFormat.getIntegerInstance() }
private val oneDecimal by lazy {
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
}

internal fun count(value: Long): String = integers.format(value)

internal fun formatOneDecimal(value: Double): String = oneDecimal.format(value)


/**
 * The first and last day of a series, as x labels.
 *
 * Two labels, not six: the question a date axis answers here is "since when",
 * and a month of tick marks under a 300-pixel chart answers nothing.
 */
internal fun dayTicks(readings: List<Reading>, endsToday: Boolean = true): List<Pair<Float, String>> {
    if (readings.size < 2) return emptyList()
    val last = if (endsToday) "Today" else dayLabel(readings.last().day)
    return listOf(0f to dayLabel(readings.first().day), 1f to last)
}

/** One letter a day, for a week of bars. */
internal fun weekdayTicks(days: List<Long>): List<Pair<Float, String>> {
    if (days.size < 2) return emptyList()
    val locale = java.util.Locale.getDefault()
    return days.mapIndexed { index, day ->
        val at = index / (days.size - 1).toFloat()
        at to LocalDate.ofEpochDay(day).dayOfWeek
            .getDisplayName(java.time.format.TextStyle.NARROW, locale)
    }
}

/** A night's four stages in minutes, which is what the bar chart wants. */
internal fun com.srineere.airbridge.health.Night.fourStages(): FourStages {
    // A night written without stages is one plain bar rather than an
    // invented breakdown.
    if (!hasStages) return FourStages(0.0, asleep.toMinutes().toDouble(), 0.0, 0.0)
    return FourStages(
        deep = deep.toMinutes().toDouble(),
        light = light.toMinutes().toDouble(),
        rem = rem.toMinutes().toDouble(),
        awake = awake.toMinutes().toDouble(),
    )
}

/**
 * Four tones of one colour, for a stacked chart somebody has recoloured.
 *
 * "Use the app colour on every tile" left the sleep bars purple, and the
 * reason was defensible rather than right: in a stage chart colour is the
 * data, so flattening deep, light, REM and awake into one rose would have
 * drawn four indistinguishable blocks.
 *
 * But a person who picks a colour has asked for something, and answering
 * "no, that one means something" is only correct if there is no third
 * option. There is. Four weights of the chosen colour stay as separable
 * as four hues while belonging to the colour that was asked for, and the
 * stage key beside them still says which is which.
 *
 * Only when a colour was picked deliberately. Left alone, a sleep tile
 * keeps the palette where deep really is a different colour from REM.
 */
internal data class StageTones(val deep: Color, val light: Color, val rem: Color, val awake: Color)

internal fun stageTones(base: Color) = StageTones(
    deep = base,
    light = base.copy(alpha = 0.66f),
    rem = base.copy(alpha = 0.44f),
    awake = base.copy(alpha = 0.24f),
)

/**
 * The four tones a sleep screen should draw in.
 *
 * [LocalTileColour] carries the colour of the tile you came in through,
 * already resolved -- so a Sleep tile nobody has touched arrives carrying
 * `sleepRem`, which is the Sleep kind's own colour. That is the test: the
 * kind's own colour means nothing was chosen, anything else means it was.
 *
 * Without that check, applying tones unconditionally would flatten every
 * default sleep chart into four alpha steps of one purple -- fixing the
 * recolour by breaking the thing it was meant to leave alone.
 */
@Composable
internal fun stagePalette(): StageTones {
    val extras = MaterialTheme.extras
    // The tile you came in through, where there was one. The sleep bank
    // and the morning report are about sleep and are reached from a row
    // rather than from the Sleep tile, so they ask that tile directly --
    // otherwise recolouring sleep leaves two sleep screens behind.
    val sleepTile = LocalHealthState.current.tiles.firstOrNull { it.kind == TileKind.Sleep }
    val chosen = LocalTileColour.current
        ?: sleepTile?.takeIf { it.colour != null }?.let { tileColour(it) }
    return if (chosen == null || chosen == extras.sleepRem) {
        StageTones(extras.sleepDeep, extras.sleepLight, extras.sleepRem, extras.sleepAwake)
    } else {
        stageTones(chosen)
    }
}

/** A tile's own colour, falling back to the one its kind teaches. */
@Composable
internal fun tileColour(tile: Tile): Color =
    tileColour(tile, MaterialTheme.extras, MaterialTheme.colorScheme)

/**
 * The same answer, without being a composable.
 *
 * The grid builds a map of these and has to do it inside `remember`, or the
 * map is a new object every pass and the lambda that captures it defeats
 * every tile's skipping.
 */
internal fun tileColour(
    tile: Tile,
    extras: AirExtraColors,
    scheme: androidx.compose.material3.ColorScheme,
): Color =
    when (tile.colour ?: tile.kind.defaultColour) {
        TileColour.Heart -> extras.heart
        TileColour.Sleep -> extras.sleepRem
        TileColour.Steps -> extras.steps
        TileColour.Hrv -> extras.hrv
        TileColour.Activity -> extras.activity
        TileColour.Energy -> extras.energy
        TileColour.Slate -> scheme.onSurfaceVariant
        TileColour.Accent -> scheme.primary
    }

/**
 * Health Connect holds what Google Health last wrote, which is a sync behind
 * the band. Saying when keeps an old number from reading as a wrong one.
 */
private fun freshnessLine(data: HealthData): String {
    val missing = data.snapshot.missingPermissions.size
    val base = "Read from Health Connect, written there by Google Health."
    return when (missing) {
        0 -> base
        1 -> "$base One data type is not allowed yet."
        else -> "$base $missing data types are not allowed yet."
    }
}
