package com.srineere.airbridge.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

/**
 * Who wrote the readings this tab draws.
 *
 * Health Connect is a shared store, not a feed from one band: every record
 * carries the package that wrote it, and a read with no origin filter returns
 * **all of them**. That is mostly what this app wants — it is why a second
 * device, or somebody else's food logger, shows up on the tab without a line
 * of code. For steps it is wrong, and silently so.
 *
 * Since a Health Connect update in June 2026 the phone itself writes steps,
 * under a device-specific *synthetic package name* rather than under
 * `"android"`. Those steps are real, but they are not the band's, and
 * aggregating both together means the day's figure counts the same walk twice
 * on a phone carried in a pocket. The step goal buzzes at a number that was
 * reached early, and nothing on screen says why.
 *
 * So steps are filtered, and this is what they are filtered by.
 */
data class HealthSource(
    val packageName: String,
    /** The app's own name where one can be resolved, else the package. */
    val label: String,
    /** Whether this is the phone counting its own steps rather than a device. */
    val isPhone: Boolean,
)

/**
 * Which writers a metric is counted from.
 *
 * Three answers rather than two, because "every source" and "every source but
 * this phone" are both reasonable and neither is a special case of the other.
 */
sealed interface SourceChoice {
    /** Every writer but the phone's own pedometer. The default. */
    data object Wearables : SourceChoice

    /** Every writer, the phone included. What the app did before. */
    data object Everything : SourceChoice

    /** One writer, named. */
    data class Only(val packageName: String) : SourceChoice

    companion object {
        /** Stored as one string, because a sealed type is not a preference. */
        fun decode(stored: String?): SourceChoice = when {
            stored.isNullOrEmpty() -> Wearables
            stored == EVERYTHING -> Everything
            stored == WEARABLES -> Wearables
            else -> Only(stored)
        }

        fun encode(choice: SourceChoice): String = when (choice) {
            Wearables -> WEARABLES
            Everything -> EVERYTHING
            is Only -> choice.packageName
        }

        // Neither is a legal package name, so neither can collide with one.
        private const val EVERYTHING = "*"
        private const val WEARABLES = "*wearables"
    }
}

/**
 * The packages a read should be restricted to.
 *
 * `null` means do not filter at all. An **empty set** means filter to nothing
 * — which is not the same thing, and is the trap this function exists to make
 * explicit: Health Connect reads an empty `dataOriginFilter` as *every*
 * origin, so an empty set handed straight to it would do the opposite of what
 * it says. Callers short-circuit on it instead; see [HealthReader].
 */
fun resolveSources(choice: SourceChoice, present: List<HealthSource>): Set<String>? =
    when (choice) {
        SourceChoice.Everything -> null
        is SourceChoice.Only -> setOf(choice.packageName)
        SourceChoice.Wearables -> {
            val phones = present.count { it.isPhone }
            // Nothing to exclude, so do not pay for a filter. This also keeps
            // the common single-device phone-free case on the same code path
            // it has always been on.
            if (phones == 0) null else present.filterNot { it.isPhone }.map { it.packageName }.toSet()
        }
    }

/**
 * Enumerating who writes what, and remembering it for a while.
 *
 * The enumeration costs a Health Connect read, and the service asks for steps
 * every fifteen minutes, so the answer is cached for the process. A new
 * device does not appear mid-morning; when one does, the picker screen clears
 * this by hand.
 */
object HealthSources {

    private const val TAG = "AirBridge"

    /** The platform's own package, and the documented half of the phone case. */
    private const val PLATFORM = "android"

    /** How far back to look for a writer before deciding there isn't one. */
    private val WINDOW = Duration.ofDays(14)

    private const val CACHE_MS = 6 * 60 * 60 * 1000L

    @Volatile
    private var cached: List<HealthSource>? = null

    @Volatile
    private var cachedAt = 0L

    /** Called when a source may have changed under us — the picker does this. */
    fun forget() {
        cached = null
        cachedAt = 0L
    }

    /**
     * Who has written steps lately.
     *
     * Empty when the permission is missing or nothing has been written, which
     * the caller must tell apart from "one source, and it is the phone" —
     * [resolveSources] does, and so does the picker.
     */
    suspend fun stepWriters(context: Context, client: HealthConnectClient): List<HealthSource> {
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < CACHE_MS) return it }

        if (HealthPermission.getReadPermission(StepsRecord::class) !in HealthConnect.granted(client)) {
            return emptyList()
        }

        // An aggregate reports which origins contributed to it, which is this
        // question asked directly: one call, no paging, and no record is
        // converted on the way.
        //
        // Reading the records instead was the obvious first attempt and it
        // does not work on a real phone. Health Connect stores step records
        // whose start equals their end, and `StepsRecord`'s own constructor
        // rejects those -- so `readRecords` throws from inside the library's
        // converter, on data the platform was happy to keep, and there is
        // nothing a caller can do about it. The aggregate never builds a
        // record, so it never hits that.
        val packages = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(Instant.now().minus(WINDOW), Instant.now()),
                ),
            ).dataOrigins.map { it.packageName }
        }.onFailure {
            Log.w(TAG, "could not enumerate step writers", it)
        }.getOrDefault(emptyList())

        val sources = packages.map { describe(context, it) }
        // Who writes steps is the question behind every wrong step figure,
        // and it cannot be answered from the screen: one source and three
        // look identical once they are added together. Logged for the same
        // reason HealthReader logs the types nobody granted.
        Log.i(
            TAG,
            "step writers: " + if (sources.isEmpty()) {
                "none in the last ${WINDOW.toDays()} days"
            } else {
                sources.joinToString { "${it.packageName}${if (it.isPhone) " (phone)" else ""}" }
            },
        )
        cached = sources
        cachedAt = now
        return sources
    }

    /**
     * What to call a package, and whether it is the phone.
     *
     * The phone's own pedometer writes under a **synthetic package name** that
     * is specific to the device and matches no installed app, so it cannot be
     * recognised by name. `getCurrentDeviceDataSource()` would name it, but it
     * is a framework API that is not in the Jetpack library this app is pinned
     * to. What is true of every synthetic name, and of no real writer, is that
     * `PackageManager` has never heard of it — so that is the test, with the
     * documented `"android"` case alongside it.
     *
     * The cost of being wrong is small in one direction and large in the
     * other: mistaking a real app for the phone would drop a band's steps
     * silently, so an unresolvable package is labelled plainly as "this phone"
     * and the picker lets anybody say otherwise.
     */
    private fun describe(context: Context, packageName: String): HealthSource {
        val manager = context.packageManager
        val label = runCatching {
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
        return HealthSource(
            packageName = packageName,
            label = label ?: if (packageName == PLATFORM) "This phone" else "This phone (built-in counter)",
            isPhone = packageName == PLATFORM || label == null,
        )
    }

    /** Resolves [packageName] to an app label, for naming a source in prose. */
    fun labelOf(context: Context, packageName: String): String =
        runCatching {
            val manager = context.packageManager
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
}
