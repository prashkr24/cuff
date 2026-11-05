package com.srineere.airbridge.widget

import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.health.TileSize

/**
 * The five cells the launcher offers.
 *
 * Five providers, not forty-two. The launcher's picker is someone else's
 * list -- no sections, no search, no room for a sentence -- and every design
 * in it would bury the other apps on the phone. So the picker gets one entry
 * per size and the choosing happens in [WidgetConfigActivity], where there is
 * a screen for it.
 *
 * [tile] is the face a Health tile draws at this cell. It is the existing
 * [TileSize] rather than a second scale, so a kind that refuses a size on the
 * tab refuses it here for the same reason and without a second list to keep.
 */
enum class WidgetSize(
    val label: String,
    val cells: String,
    val tile: TileSize,
    /** Portrait dp for `minWidth`/`minHeight`, which is what 28-30 reads. */
    val minWidth: Int,
    val minHeight: Int,
    val cellsWide: Int,
    val cellsHigh: Int,
) {
    Strip("Small", "2 × 1", TileSize.S, 150, 66, 2, 1),
    Square("Square", "2 × 2", TileSize.M, 150, 150, 2, 2),
    Row("Row", "4 × 1", TileSize.W, 310, 66, 4, 1),
    Wide("Wide", "4 × 2", TileSize.L, 310, 150, 4, 2),
    Tall("Tall", "4 × 4", TileSize.XL, 310, 300, 4, 4),
    ;

    companion object {
        fun named(name: String?): WidgetSize? = entries.firstOrNull { it.name == name }

        /**
         * The size a cell of this many dp is closest to.
         *
         * Launchers do not agree on what a cell is, so the reported width and
         * height are matched to the nearest declared size rather than
         * assumed. Used when a widget is resized after it was placed.
         */
        fun nearest(widthDp: Int, heightDp: Int): WidgetSize =
            entries.minByOrNull { size ->
                val dw = (size.minWidth - widthDp).toLong()
                val dh = (size.minHeight - heightDp).toLong()
                dw * dw + dh * dh
            } ?: Wide
    }
}

/** How the catalogue is grouped, and in the order it lists them. */
enum class WidgetFamily(val label: String, val note: String) {
    Tiles("Your tiles", "The same cards as the Health tab"),
    Band("The band", "What no health app can show"),
    Rings("Rings", "Three arcs that close"),
    Dials("Dials", "Big scores on black"),
    Paper("Paper", "Pale, for a light wallpaper"),
    Type("Type alone", "No card, straight on the wallpaper"),
}

/**
 * One thing a widget can be.
 *
 * [id] is stored against an `appWidgetId` and must outlive a rename, so it is
 * a string built from an enum name rather than an ordinal: inserting a kind
 * must not silently turn every placed Sleep widget into an Energy one.
 */
sealed interface WidgetDesign {
    val id: String
    val title: String
    val summary: String
    val family: WidgetFamily
    val sizes: Set<WidgetSize>

    fun fits(size: WidgetSize): Boolean = size in sizes

    /** A tile from the Health tab, drawn at the face the cell allows. */
    data class OfTile(val kind: TileKind) : WidgetDesign {
        override val id: String get() = "$TILE_PREFIX${kind.name}"
        override val title: String get() = kind.title
        override val summary: String get() = tileSummary(kind)
        override val family: WidgetFamily get() = WidgetFamily.Tiles
        override val sizes: Set<WidgetSize>
            get() = WidgetSize.entries.filter { it.tile in kind.sizes }.toSet()
    }

    /** Something only this app knows: the relay, the Air, the last buzz. */
    data class OfBand(val face: BandFace) : WidgetDesign {
        override val id: String get() = "$BAND_PREFIX${face.name}"
        override val title: String get() = face.title
        override val summary: String get() = face.summary
        override val family: WidgetFamily get() = WidgetFamily.Band
        override val sizes: Set<WidgetSize> get() = face.sizes
    }

    /** Apple's grammar: arcs that close, a list, a row of arrows. */
    data class OfRings(val face: RingFace) : WidgetDesign {
        override val id: String get() = "$RINGS_PREFIX${face.name}"
        override val title: String get() = face.title
        override val summary: String get() = face.summary
        override val family: WidgetFamily get() = WidgetFamily.Rings
        override val sizes: Set<WidgetSize> get() = face.sizes
    }

    /** Whoop's grammar: one thick arc with its figure in the middle. */
    data class OfDials(val face: DialFace) : WidgetDesign {
        override val id: String get() = "$DIALS_PREFIX${face.name}"
        override val title: String get() = face.title
        override val summary: String get() = face.summary
        override val family: WidgetFamily get() = WidgetFamily.Dials
        override val sizes: Set<WidgetSize> get() = face.sizes
    }

    /** The same readings with the card taken away. */
    data class OfType(val face: TypeFace) : WidgetDesign {
        override val id: String get() = "$TYPE_PREFIX${face.name}"
        override val title: String get() = face.title
        override val summary: String get() = face.summary
        override val family: WidgetFamily get() = WidgetFamily.Type
        override val sizes: Set<WidgetSize> get() = face.sizes
    }

    companion object {
        const val TILE_PREFIX = "tile:"
        const val BAND_PREFIX = "band:"
        const val RINGS_PREFIX = "rings:"
        const val DIALS_PREFIX = "dials:"
        const val TYPE_PREFIX = "type:"
    }
}

/**
 * The band faces that read something already stored.
 *
 * Stop, Buzz, Find and Pause are designed and deliberately absent here: each
 * takes the band's one session, and a button is a different kind of widget
 * from a reading. They arrive with their own slice rather than half-built
 * inside this one.
 */
enum class BandFace(
    val title: String,
    val summary: String,
    val sizes: Set<WidgetSize>,
) {
    Everything(
        "The band",
        "State, charge and who rang last",
        setOf(WidgetSize.Wide, WidgetSize.Tall),
    ),
    Battery(
        "Band battery",
        "The last charge read, and when",
        setOf(WidgetSize.Strip, WidgetSize.Square, WidgetSize.Row),
    ),
    LastBuzz(
        "Last buzz",
        "Who, and how long ago",
        setOf(WidgetSize.Strip, WidgetSize.Square, WidgetSize.Row),
    ),
    Relay(
        "Relaying",
        "Whether your phone is passing alerts on",
        setOf(WidgetSize.Strip, WidgetSize.Square, WidgetSize.Row),
    ),
    Quiet(
        "Quiet hours",
        "When the band stops buzzing, and starts again",
        setOf(WidgetSize.Strip, WidgetSize.Row),
    ),
    Buzzes(
        "Buzzes today",
        "How many got through since midnight",
        setOf(WidgetSize.Strip, WidgetSize.Square),
    ),
}

private fun tileSummary(kind: TileKind): String = when (kind) {
    TileKind.Heart -> "Where it settled overnight, against your fortnight"
    TileKind.Zones -> "Today's beats, by the hour"
    TileKind.Sleep -> "Last night and the week behind it"
    TileKind.Steps -> "Today against your goal, and the week"
    TileKind.Energy -> "In against out, today"
    TileKind.Activity -> "Minutes that counted, and what they were"
    TileKind.Vitals -> "Breathing, skin temperature and blood oxygen"
    TileKind.Hrv -> "One figure a night, against its usual band"
    TileKind.Nutrition -> "What you logged, split three ways"
    TileKind.Weight -> "A fortnight of readings, and the drift"
    TileKind.Breathing -> "Breaths a minute while you slept"
    TileKind.Skin -> "How far last night sat from your baseline"
    TileKind.Oxygen -> "Overnight saturation"
}

/**
 * Everything on offer, in the order the catalogue lists it.
 *
 * The optional tiles are listed whether or not they are on the tab. A home
 * screen is often where someone goes looking for a reading they have not
 * turned on yet, and a widget that says why it is empty is more use than a
 * kind that is simply missing.
 */
object WidgetCatalogue {

    val all: List<WidgetDesign> =
        TileKind.entries.map { WidgetDesign.OfTile(it) } +
            BandFace.entries.map { WidgetDesign.OfBand(it) } +
            RingFace.entries.map { WidgetDesign.OfRings(it) } +
            DialFace.entries.map { WidgetDesign.OfDials(it) } +
            TypeFace.entries.map { WidgetDesign.OfType(it) }

    fun forSize(size: WidgetSize): List<WidgetDesign> = all.filter { it.fits(size) }

    /** Everything, grouped, for the catalogue under Device. */
    fun families(): List<Pair<WidgetFamily, List<WidgetDesign>>> =
        WidgetFamily.entries
            .map { family -> family to all.filter { it.family == family } }
            .filter { it.second.isNotEmpty() }

    /**
     * The families that exist on the design canvas and nowhere else.
     *
     * Derived rather than listed, so a family leaves this the moment one of
     * its designs is written -- a hand-kept "coming soon" list is a list
     * that goes on promising something that shipped a month ago.
     */
    val notBuilt: List<WidgetFamily>
        get() = WidgetFamily.entries.filter { family -> all.none { it.family == family } }

    fun families(size: WidgetSize): List<Pair<WidgetFamily, List<WidgetDesign>>> =
        WidgetFamily.entries
            .map { family -> family to forSize(size).filter { it.family == family } }
            .filter { it.second.isNotEmpty() }

    /** Null for an id this build no longer knows, which the widget says out loud. */
    fun byId(id: String?): WidgetDesign? {
        if (id == null) return null
        return when {
            id.startsWith(WidgetDesign.TILE_PREFIX) ->
                TileKind.entries
                    .firstOrNull { it.name == id.removePrefix(WidgetDesign.TILE_PREFIX) }
                    ?.let { WidgetDesign.OfTile(it) }
            id.startsWith(WidgetDesign.BAND_PREFIX) ->
                BandFace.entries
                    .firstOrNull { it.name == id.removePrefix(WidgetDesign.BAND_PREFIX) }
                    ?.let { WidgetDesign.OfBand(it) }
            id.startsWith(WidgetDesign.RINGS_PREFIX) ->
                RingFace.entries
                    .firstOrNull { it.name == id.removePrefix(WidgetDesign.RINGS_PREFIX) }
                    ?.let { WidgetDesign.OfRings(it) }
            id.startsWith(WidgetDesign.DIALS_PREFIX) ->
                DialFace.entries
                    .firstOrNull { it.name == id.removePrefix(WidgetDesign.DIALS_PREFIX) }
                    ?.let { WidgetDesign.OfDials(it) }
            id.startsWith(WidgetDesign.TYPE_PREFIX) ->
                TypeFace.entries
                    .firstOrNull { it.name == id.removePrefix(WidgetDesign.TYPE_PREFIX) }
                    ?.let { WidgetDesign.OfType(it) }
            else -> null
        }
    }

    /**
     * What a freshly dropped widget of this size shows before anyone chooses.
     *
     * Only used where the catalogue could not run -- a restore, or a launcher
     * that skipped the configuration activity. Steps at every size it has,
     * the band at the two it does not.
     */
    fun default(size: WidgetSize): WidgetDesign =
        WidgetDesign.OfTile(TileKind.Steps).takeIf { it.fits(size) }
            ?: forSize(size).first()
}
