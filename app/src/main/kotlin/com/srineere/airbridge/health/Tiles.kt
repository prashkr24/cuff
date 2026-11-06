package com.srineere.airbridge.health

/**
 * The Health tab is a grid of six columns.
 *
 * Six is the point: it is the smallest number that lets one, two and three
 * tiles share a row on the same gutters, so any mix of sizes stays aligned
 * and no size needs a special case.
 */
const val TILE_COLUMNS = 6

enum class TileSize(val span: Int, val rows: Int, val label: String) {
    S(2, 1, "S"),
    M(3, 1, "M"),
    // Two thirds, and the reason it exists: a small tile is a third, so
    // small-beside-medium left a column of dead air that no arrangement
    // could fill. Small beside wide is a whole row.
    W(4, 1, "W"),
    L(6, 1, "L"),
    XL(6, 2, "XL"),
}

/**
 * A tile's colour is chrome only -- its kicker, its chip, its single-series
 * chart. It never reaches a chart where colour carries meaning, so sleep
 * stages, heart zones and deficit against surplus keep their own palettes
 * whatever is chosen here.
 */
enum class TileColour { Heart, Sleep, Steps, Hrv, Activity, Energy, Slate, Accent }

/**
 * What a tile can show.
 *
 * [sizes] is the honest part: a tile offers only the sizes it can fill. A
 * workout list at XL would be a list, HRV at XL would be one number
 * enlarged, weight at XL would be whitespace. Each refusal is a decision,
 * not an omission.
 */
enum class TileKind(
    val title: String,
    val sizes: Set<TileSize>,
    /** Where tapping it goes. The screen is the tile's whole story. */
    val route: String,
    /** The colour the tile teaches, until someone chooses another. */
    val defaultColour: TileColour,
    val optional: Boolean = false,
    /**
     * Whether the Health tab offers it at all.
     *
     * False makes a kind a widget face and nothing else. The home screen
     * and the tab ask different questions -- a home screen shows one card
     * all day, a tab shows thirteen at once -- so a reading worth its own
     * cell there can be a repetition here.
     */
    val onTab: Boolean = true,
) {
    // Named for its own number, and opening the screen about that number.
    // It used to open today's zones, which is a different measurement with
    // the same organ behind it.
    Heart(
        "Resting heart rate", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthtrend", TileColour.Heart,
    ),
    // Today's beats, which is a different question from the resting figure
    // above it: one is where your heart settled last night, the other is
    // what it has been doing since you got up. They shared a tile for a
    // while and the tile could only answer one of them.
    Zones(
        "Heart rate", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthheart", TileColour.Heart,
    ),
    Sleep(
        "Sleep", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthsleep", TileColour.Sleep,
    ),
    Steps(
        "Steps", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthsteps", TileColour.Steps,
    ),
    Energy(
        "Energy", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthenergy", TileColour.Energy,
    ),
    Activity(
        "Activity", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthactivity", TileColour.Activity,
    ),
    Vitals(
        "Vitals", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthvitals", TileColour.Slate,
    ),
    Hrv(
        "HRV", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthhrv", TileColour.Hrv,
    ),
    Nutrition(
        "Nutrition", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthnutrition", TileColour.Energy, optional = true,
    ),
    Weight(
        "Weight", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthweight", TileColour.Slate, optional = true,
    ),
    // The two composites. Both are figures this app works out rather than
    // reads, so both carry their own refusal to show one: readiness is
    // blank until sixty nights, fitness age until a VO2 max arrives.
    Readiness(
        "Readiness", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "readiness", TileColour.Hrv, optional = true,
    ),
    FitnessAge(
        "Fitness age", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "fitnessage", TileColour.Activity, optional = true,
    ),
    // Two more readings of the night, and the only two kinds the tab does
    // not offer. The Sleep tile already covers the night there, and a tab
    // carrying all three says one thing three times. A home screen is a
    // different question -- one card, chosen, seen all day -- so both are
    // worth having there. Both take XL and both have something to put in
    // it: the stages get their key, the bank the count of nights each way.
    Stages(
        "Last night by stage",
        setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthsleep", TileColour.Sleep, optional = true, onTab = false,
    ),
    SleepBank(
        "Sleep bank",
        setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L, TileSize.XL),
        "healthsleepbank", TileColour.Sleep, optional = true, onTab = false,
    ),
    // The other two vitals. Each has a screen of its own, so each can be a
    // tile. Blood oxygen was a third until it was taken out: the band
    // measures it and Google Health will not pass it on, so the tile could
    // only ever have been empty.
    Breathing(
        "Breathing rate", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthbreathing", TileColour.Activity, optional = true,
    ),
    Skin(
        "Skin temperature", setOf(TileSize.S, TileSize.M, TileSize.W, TileSize.L),
        "healthskin", TileColour.Energy, optional = true,
    ),
    // Two running totals climbing against each other through the day --
    // the one reading here that is a shape rather than a figure, which is
    // why it refuses the two narrow sizes: a day's two curves in two
    // columns is a smudge, and the number on its own is Energy's tile.
    Balance(
        "Burned against eaten", setOf(TileSize.W, TileSize.L, TileSize.XL),
        "healthbalance", TileColour.Activity, optional = true,
    ),
    ;

    /**
     * The name at S, where the full one does not fit.
     *
     * Chopping at the first space gave "Skin" for skin temperature, which
     * names an organ rather than a reading. The ones that break get written
     * out.
     */
    val short: String
        get() = when (this) {
            Stages -> "Stages"
            SleepBank -> "Bank"
            Zones -> "Heart"
            Skin -> "Skin temp"
            Breathing -> "Breathing"
            Nutrition -> "Food"
            Balance -> "Balance"
            else -> title.substringBefore(' ')
        }

    /** The nearest size this tile can honestly fill. */
    fun clamp(size: TileSize): TileSize =
        if (size in sizes) size else sizes.maxByOrNull { it.span } ?: TileSize.M
}

data class Tile(
    val kind: TileKind,
    val size: TileSize,
    /** Null means the kind's own colour, which is the default and the teacher. */
    val colour: TileColour? = null,
)

/** One row of the grid, already packed. */
data class TileRow(val tiles: List<Tile>)

/**
 * The layout everyone starts with.
 *
 * Today's heart rate leads, because it is the only reading on the tab that
 * answers "how am I right now" rather than "how has this month gone". Then
 * the day as it is being spent -- steps, and what has gone in against what
 * has gone out -- before the night that preceded it.
 *
 * Every row fills exactly: W beside S, M beside M, and the full-width ones
 * alone. No gaps to explain on the first run, and every size in use, which
 * is also the quickest way to learn that the sizes exist.
 *
 * Lifted from a tab somebody had actually arranged and then lived with,
 * rather than guessed at, and taken again once readiness existed. Readiness
 * takes the small slot beside the Vitals strip, where a single figure
 * against its own band is all the room needs. Workouts move up to the full
 * width and close the tab, because a list of two lines in a third of a row
 * is a list of one. Burned against eaten comes off, since the Energy tile
 * two rows above is the same arithmetic; HRV comes off for the same reason
 * resting heart rate did, which is that the Vitals strip already carries it.
 *
 * No colours are set, so each tile teaches its own.
 */
val defaultTiles: List<Tile> = listOf(
    Tile(TileKind.Zones, TileSize.XL),
    Tile(TileKind.Steps, TileSize.XL),
    Tile(TileKind.Energy, TileSize.M),
    Tile(TileKind.Nutrition, TileSize.M),
    Tile(TileKind.Sleep, TileSize.XL),
    Tile(TileKind.Vitals, TileSize.W),
    Tile(TileKind.Readiness, TileSize.S),
    Tile(TileKind.Activity, TileSize.L),
)

/**
 * Packs tiles into rows of six columns, in order.
 *
 * A tile that does not fit the row being filled starts the next one rather
 * than being reordered around it: the order is the person's, and silently
 * rearranging it to save a gap would make the edit screen lie.
 */
fun packTiles(tiles: List<Tile>): List<TileRow> {
    val rows = mutableListOf<TileRow>()
    var current = mutableListOf<Tile>()
    var used = 0
    tiles.forEach { tile ->
        val span = tile.size.span.coerceAtMost(TILE_COLUMNS)
        if (used + span > TILE_COLUMNS && current.isNotEmpty()) {
            rows += TileRow(current)
            current = mutableListOf()
            used = 0
        }
        current += tile
        used += span
    }
    if (current.isNotEmpty()) rows += TileRow(current)
    return rows
}

/** Compact, order-preserving, and readable in `adb shell cat`. */
fun encodeTiles(tiles: List<Tile>): String =
    tiles.joinToString(",") { "${it.kind.name}:${it.size.name}:${it.colour?.name ?: ""}" }

/**
 * Decodes a stored layout, dropping anything it no longer understands.
 *
 * Fail soft, not closed: a tile kind removed in a later version should cost
 * that one tile, not the whole tab. An empty or unreadable string falls back
 * to the default layout rather than an empty screen.
 */
fun decodeTiles(stored: String?): List<Tile> {
    if (stored.isNullOrBlank()) return defaultTiles
    val tiles = stored.split(",").mapNotNull { part ->
        val bits = part.split(":")
        if (bits.size < 2) return@mapNotNull null
        val kind = TileKind.entries.firstOrNull { it.name == bits[0] } ?: return@mapNotNull null
        val size = TileSize.entries.firstOrNull { it.name == bits[1] } ?: return@mapNotNull null
        val colour = bits.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { name -> TileColour.entries.firstOrNull { it.name == name } }
        Tile(kind, kind.clamp(size), colour)
    }
    return tiles.ifEmpty { defaultTiles }
}

/** The kinds not currently on the tab, for the edit screen's "add" list. */
/**
 * What the tab can still be given, which is not everything that exists.
 *
 * A kind with [TileKind.onTab] false is a widget face and nothing else:
 * drawn by the catalogue, offered on a home screen, never listed here.
 */
fun unusedKinds(tiles: List<Tile>): List<TileKind> {
    val shown = tiles.map { it.kind }.toSet()
    return TileKind.entries.filter { it.onTab }.filterNot { it in shown }
}

/** Moves a tile one place, or returns the list unchanged at either end. */
fun moveTile(tiles: List<Tile>, from: Int, by: Int): List<Tile> {
    val to = from + by
    if (from !in tiles.indices || to !in tiles.indices) return tiles
    val next = tiles.toMutableList()
    next.add(to, next.removeAt(from))
    return next
}
