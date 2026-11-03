package com.srineere.airbridge.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.srineere.airbridge.RelayAppSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One installed app, as the Apps screen needs it. */
@Immutable
internal data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val canCall: Boolean,
    val namesCallers: Boolean,
)

/**
 * Every launchable app, with its icon, read once per process.
 *
 * Building this is expensive: a query for every launcher entry, a binder call
 * and an icon to rasterise per app. It used to hang off [RelayState], which is
 * replaced on every write to the rules -- so toggling one switch threw the
 * whole list away and built it again, on the frame the switch was drawn. That
 * is what the Apps screen was lagging on.
 *
 * It lives here instead, where a toggled switch cannot reach it. What is
 * installed changes when something is installed, and [invalidate] is told
 * about exactly that.
 */
internal object InstalledApps {
    /** Null until it has been read; the UI watches this to warm it off the main thread. */
    private val cache = MutableStateFlow<List<AppEntry>?>(null)

    val current: StateFlow<List<AppEntry>?> = cache.asStateFlow()

    /**
     * Synchronous, and slow only the first time. Synchronised so a screen that
     * asks before the warm-up has finished waits for it rather than building a
     * second copy alongside it.
     */
    @Synchronized
    fun load(context: Context): List<AppEntry> =
        cache.value ?: build(context.applicationContext).also { cache.value = it }

    fun invalidate() {
        cache.value = null
    }

    @Suppress("DEPRECATION")
    private fun build(context: Context): List<AppEntry> {
        val manager = context.packageManager
        // Asked once. Each of these used to be asked inside the loop below,
        // and the dialer is a binder call -- so a hundred apps meant a hundred
        // round trips to the telecom service for one unchanging answer.
        val dialer = RelayAppSelection.dialer(context)
        val names = RelayAppSelection.namesCallers(dialer) + RelayAppSelection.seenNaming(context)
        val rang = RelayAppSelection.seenCalling(context)

        val labels = manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .associate { it.packageName to it.loadLabel(manager).toString() }
            .toMutableMap()
        // Keep saved selections visible even after an app is uninstalled.
        for (name in RelayAppSelection.selected(context)) {
            labels.putIfAbsent(name, RelayAppSelection.labelFor(name))
        }

        return labels.entries.sortedBy { it.value.lowercase() }.map { (packageName, label) ->
            AppEntry(
                packageName = packageName,
                label = label,
                // Straight to a bitmap. This used to be compressed to a PNG
                // and immediately decoded again, which made sense across a
                // Flutter channel and makes none here: nothing between this
                // and the Image() that draws it leaves the heap.
                icon = runCatching {
                    manager.getApplicationIcon(packageName)
                        .toBitmap(ICON_PX, ICON_PX)
                        .asImageBitmap()
                }.getOrNull(),
                canCall = packageName in rang ||
                    RelayAppSelection.canReceiveCalls(context, packageName),
                namesCallers = packageName in names,
            )
        }
    }

    /** What the icons are rasterised at; the rows draw them at 26dp. */
    private const val ICON_PX = 48
}
