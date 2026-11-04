package com.srineere.airbridge.widget

import android.content.Context

/**
 * What one placed widget was told to be.
 *
 * [pinned] null means Auto: the widget draws whatever face the cell it is
 * currently in allows, and changes when it is resized. A pinned size keeps
 * one face at every cell, which is the answer to a launcher that reports a
 * cell a little smaller than it looks.
 */
data class WidgetConfig(
    val design: WidgetDesign,
    val pinned: WidgetSize? = null,
    /** True to land on the Health tab rather than on this reading's screen. */
    val opensTab: Boolean = false,
) {
    /** The face to draw in a cell of this size. */
    fun face(cell: WidgetSize): WidgetSize {
        val wanted = pinned ?: cell
        if (design.fits(wanted)) return wanted
        // A kind that refuses the wanted size falls back to the nearest it
        // can honestly fill, never to an empty card. Nearest in both
        // directions: falling back to the largest would draw a four-cell
        // face in a strip, which is the worse of the two mistakes.
        return design.sizes.minByOrNull { kotlin.math.abs(it.ordinal - wanted.ordinal) } ?: cell
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

    internal fun encode(config: WidgetConfig): String = listOf(
        config.design.id,
        config.pinned?.name.orEmpty(),
        if (config.opensTab) "1" else "0",
    ).joinToString(FIELD.toString())

    /**
     * Null for anything this build cannot make sense of.
     *
     * A design dropped in a later version, a row written by a newer one, a
     * truncated string: all the same answer, and the widget then asks to be
     * set up again rather than drawing a guess.
     */
    internal fun decode(raw: String?): WidgetConfig? {
        if (raw.isNullOrEmpty()) return null
        val parts = raw.split(FIELD)
        val design = WidgetCatalogue.byId(parts.getOrNull(0)) ?: return null
        return WidgetConfig(
            design = design,
            pinned = WidgetSize.named(parts.getOrNull(1)?.takeIf { it.isNotEmpty() }),
            opensTab = parts.getOrNull(2) == "1",
        )
    }
}
