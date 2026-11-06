package com.srineere.airbridge.ui

import android.content.Context

/**
 * What the band calls itself, kept so it is asked for once.
 *
 * The Device tab said "Fitbit Air" because that string was written into
 * the source, which is right until somebody pairs something else -- and
 * this app reads Health Connect, so somebody will. The band knows its own
 * name over GATT and About has always shown it; the heading never asked.
 *
 * Stored rather than read each visit because the answer costs a session
 * with the band, the band grants one at a time, and Google Health wants
 * that session to sync your steps. A name is also the one band fact that
 * does not go stale.
 */
object BandName {
    private const val PREFS = "air_health"
    private const val KEY = "band_name"

    fun read(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun write(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, name).apply()
    }
}
