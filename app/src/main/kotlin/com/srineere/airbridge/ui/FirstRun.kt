package com.srineere.airbridge.ui

import android.content.Context

/**
 * Whether this app has ever been opened before.
 *
 * It opens on the Health tab, which on a fresh install is a page of empty
 * cards and a single "Allow Health Connect" row — and the relay is off,
 * silently, because nothing has been granted either. Somebody who has not
 * been told has no reason to go looking in Device for a Permissions
 * screen, and the app reads as broken rather than as unconfigured.
 *
 * So the first launch lands on Permissions instead, which says what each
 * grant buys and what refusing it costs. Once only: a permissions screen
 * on every launch is a nag, and after the first time the Device tab is
 * where somebody looks for it anyway.
 *
 * Recorded on arrival rather than on completion. Someone who opens the
 * screen and backs out has been shown it, and showing it again would be
 * arguing with them.
 */
object FirstRun {
    private const val PREFS = "air_health"
    private const val KEY = "seen_permissions"

    fun needsIntroducing(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun introduced(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
    }
}
