package com.srineere.airbridge.widget

import android.content.Context

/**
 * What one placed widget was told to be: the design, and nothing else.
 *
 * It used to carry two more answers. A pinned face held one size whatever
 * cell the widget sat in, and a flag sent a tap to the Health tab instead
 * of to the reading's own screen. Both were questions asked at the moment
 * somebody was trying to place a widget, and both had a right answer the
 * app could work out for itself: the face follows the cell, so resizing on
 * the home screen redraws it, and a tap opens the screen about the thing
 * on the card.
 */
data class WidgetConfig(val designs: List<WidgetDesign>) {
    constructor(design: WidgetDesign) : this(listOf(design))

    init {
        require(designs.isNotEmpty()) { "a widget with no design is not a widget" }
    }

    /**
     * What a single-card widget draws, and the first card of a stack.
     *
     * A stack is the same row with more than one id in it rather than a
     * store of its own: a stack holds the same designs as everything else,
     * and two stores would be two answers to what a widget id was told to
     * show.
     */
    val design: WidgetDesign get() = designs.first()

    /** The face to draw in a cell of this size. */
    fun face(cell: WidgetSize): WidgetSize = faceSizeOf(design, cell)

    companion object {
        /** Kept so a list of designs reads the same at every call site. */
        fun of(designs: List<WidgetDesign>): WidgetConfig = WidgetConfig(designs)

        /**
         * A design that refuses the cell falls back to the nearest size it
         * can honestly fill, never to an empty card. Nearest in both
         * directions: falling back to the largest would draw a four-cell
         * face in a strip, which is the worse of the two mistakes.
         */
        fun faceSizeOf(design: WidgetDesign, cell: WidgetSize): WidgetSize {
            if (design.fits(cell)) return cell
            return design.sizes.minByOrNull { kotlin.math.abs(it.ordinal - cell.ordinal) } ?: cell
        }
    }
}

/**
 * Where each placed widget's choice lives.
 *
 * In `air_health` beside the tile layout rather than a file of its own,
 * because a widget's colour *is* its tile's colour -- two stores could
 * disagree about that, and one cannot.
 */
object WidgetStore {
    private const val PREFS = "air_health"
    private const val PREFIX = "widget:"
    private const val FIELD = '\u001F'

    /** Between a design and the face it was pinned to. */
    private const val SIZE = '@'

    fun read(context: Context, appWidgetId: Int): WidgetConfig? =
        decode(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREFIX + appWidgetId, null)
        )

    fun write(context: Context, appWidgetId: Int, config: WidgetConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFIX + appWidgetId, encode(config))
            .apply()
    }

    /** Called when the launcher reports widgets deleted, so ids are not leaked. */
    fun remove(context: Context, appWidgetIds: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { edit.remove(PREFIX + it) }
        edit.apply()
    }

    /** Every widget this app believes is placed, newest id first. */
    fun placed(context: Context): List<Pair<Int, WidgetConfig>> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .mapNotNull { (key, value) ->
                if (!key.startsWith(PREFIX)) return@mapNotNull null
                val id = key.removePrefix(PREFIX).toIntOrNull() ?: return@mapNotNull null
                val config = decode(value as? String) ?: return@mapNotNull null
                id to config
            }
            .sortedByDescending { it.first }

    /**
     * Drops anything the launcher no longer has.
     *
     * A widget removed while this app was not running leaves its row behind,
     * and that row is what the Your widgets screen would otherwise list as
     * placed. Reconciled against the launcher rather than trusted.
     */
    fun prune(context: Context, live: Set<Int>) {
        val stale = placed(context).map { it.first }.filterNot { it in live }
        if (stale.isEmpty()) return
        remove(context, stale.toIntArray())
    }

    internal fun encode(config: WidgetConfig): String =
        config.designs.joinToString(FIELD.toString()) { it.id }

    /**
     * Null for anything this build cannot make sense of.
     *
     * A design dropped in a later version, a row written by a newer one, a
     * truncated string: all the same answer, and the widget then asks to be
     * set up again rather than drawing a guess.
     *
     * Rows written before the pinned face and the tap target were dropped
     * carry both after a separator. They are read for their design and the
     * rest is let go, so an upgrade costs a widget its pin rather than its
     * existence.
     */
    internal fun decode(raw: String?): WidgetConfig? {
        if (raw.isNullOrEmpty()) return null
        // Anything after the separator is let go: a build in between
        // stored a pinned face per card, and every card follows the cell
        // now, so those rows are read for their designs alone.
        val designs = raw.split(FIELD).mapNotNull { WidgetCatalogue.byId(it.substringBefore(SIZE)) }
        return if (designs.isEmpty()) null else WidgetConfig(designs)
    }
}
