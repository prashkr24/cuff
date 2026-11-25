package com.srineere.airbridge.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day's heart-rate-reserve zone summary, reconstructed from Fitbit Air's
 * continuous [HeartRateRecord] samples in Health Connect.
 *
 * Health Connect has no Active Zone Minutes record type. Fitbit defines AZM
 * via Heart Rate Reserve (Karvonen):
 *   - Moderate ("Fat Burn"): 40–59% HRR -> 1x AZM per minute
 *   - Cardio:                60–84% HRR -> 2x AZM per minute
 *   - Peak:                  >= 85% HRR -> 2x AZM per minute
 *
 * [vigorousCardioMin] is [cardioMin] + [peakMin], which maps to the paper's
 * volitional `cardio_minutes` action while [azm] maps to the weekly AZM domain.
 */
data class DailyZoneSummary(
    val day: Long,
    val moderateMin: Double,
    val cardioMin: Double,
    val peakMin: Double,
    val sampleCount: Int,
    val ageUsed: Int? = null,
    val rhrUsed: Int? = null,
) {
    /** Fitbit Active Zone Minutes: 1x Moderate + 2x (Cardio + Peak). */
    val azm: Double get() = moderateMin + 2.0 * (cardioMin + peakMin)

    /** Vigorous cardio-intensity minutes (Cardio + Peak, >= 60% HRR). */
    val vigorousCardioMin: Double get() = cardioMin + peakMin
}

/**
 * Computes Fitbit-style Heart Rate Reserve zone minutes from a single day's
 * heart-rate samples.
 *
 * Credits each inter-sample interval to the earlier reading and drops gaps
 * wider than [maxGap] (5 minutes), matching [timeInZones] so an off-wrist gap
 * never invents exercise minutes.
 */
fun activeZonesFromSamples(
    day: Long,
    samples: List<HeartSample>,
    age: Int?,
    restingBpm: Double?,
    maxGap: Duration = Duration.ofMinutes(5),
): DailyZoneSummary {
    val maxHr = age?.let { (220 - it).coerceIn(120, 210) } ?: ASSUMED_MAX_HEART_RATE
    val rhr = (restingBpm ?: DEFAULT_RESTING_BPM).coerceIn(35.0, 110.0)
    if (samples.size < 2) {
        return DailyZoneSummary(
            day = day,
            moderateMin = 0.0,
            cardioMin = 0.0,
            peakMin = 0.0,
            sampleCount = samples.size,
            ageUsed = age,
            rhrUsed = rhr.toInt(),
        )
    }

    val hrr = (maxHr - rhr).coerceAtLeast(20.0)
    val moderateLow = rhr + 0.40 * hrr
    val cardioLow = rhr + 0.60 * hrr
    val peakLow = rhr + 0.85 * hrr

    val ordered = samples.sortedBy { it.atMillis }
    val maxGapMs = maxGap.toMillis()
    var moderate = 0.0
    var cardio = 0.0
    var peak = 0.0

    for (i in 0 until ordered.lastIndex) {
        val gapMs = ordered[i + 1].atMillis - ordered[i].atMillis
        if (gapMs <= 0L || gapMs > maxGapMs) continue
        val minutes = gapMs / 60_000.0
        val bpm = ordered[i].bpm.toDouble()
        when {
            bpm >= peakLow -> peak += minutes
            bpm >= cardioLow -> cardio += minutes
            bpm >= moderateLow -> moderate += minutes
        }
    }

    return DailyZoneSummary(
        day = day,
        moderateMin = moderate,
        cardioMin = cardio,
        peakMin = peak,
        sampleCount = ordered.size,
        ageUsed = age,
        rhrUsed = rhr.toInt(),
    )
}

private const val DEFAULT_RESTING_BPM = 65.0

/**
 * Local cache + incremental Health Connect indexer for historical daily Active
 * Zone Minutes.
 *
 * A single day of Fitbit Air heart rate is ~18,000 samples, so reading 90–168
 * days of raw samples on every screen open would be slow and hit Health Connect
 * rate limits. Instead, completed past days are computed in 7-day chunks once
 * and cached as compact numbers in `air_health`. Today and yesterday are always
 * computed live from the in-memory snapshot with zero extra Health Connect calls.
 */
object ActiveZoneStore {
    private const val PREFS = "air_health"
    private const val KEY_CACHE = "gage_daily_zones_v1"
    private const val KEY_CACHE_AGE = "gage_daily_zones_age"
    private const val CHUNK_DAYS = 7L
    private const val TAG = "AirBridge"

    fun read(context: Context, expectedAge: Int?): Map<Long, DailyZoneSummary> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedAge = prefs.getInt(KEY_CACHE_AGE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        if (cachedAge != expectedAge) return emptyMap()
        val raw = prefs.getString(KEY_CACHE, null) ?: return emptyMap()
        return decode(raw)
    }

    fun write(context: Context, entries: Map<Long, DailyZoneSummary>, age: Int?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHE, encode(entries))
            .putInt(KEY_CACHE_AGE, age ?: Int.MIN_VALUE)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_CACHE)
            .remove(KEY_CACHE_AGE)
            .apply()
    }

    /**
     * Merges live heart-rate samples already in [HealthSnapshot] (today and
     * yesterday) over the cached historical days.
     */
    fun overlayRecent(
        cached: Map<Long, DailyZoneSummary>,
        snapshot: HealthSnapshot,
        age: Int?,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Long, DailyZoneSummary> {
        val out = cached.toMutableMap()
        val rhrByDay = snapshot.restingHeartRate.associate { it.day to it.value }
        val fallbackRhr = snapshot.restingHeartRate.takeIf { it.isNotEmpty() }?.map { it.value }?.average()
        val grouped = snapshot.heartRecent.groupBy {
            Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate().toEpochDay()
        }
        val todayEpoch = today.toEpochDay()
        for (day in listOf(todayEpoch - 1, todayEpoch)) {
            val samples = grouped[day]
            if (!samples.isNullOrEmpty()) {
                out[day] = activeZonesFromSamples(
                    day = day,
                    samples = samples,
                    age = age,
                    restingBpm = rhrByDay[day] ?: fallbackRhr,
                )
            }
        }
        return out
    }

    /**
     * Incrementally indexes missing past days (up to [days] back) from Health
     * Connect in 7-day chunks, newest first, persisting after each chunk so
     * progress survives leaving the tab or rate limits.
     */
    suspend fun syncMissing(
        context: Context,
        snapshot: HealthSnapshot,
        days: Int,
        age: Int?,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        onChunk: (Map<Long, DailyZoneSummary>, doneDays: Int, totalDays: Int) -> Unit,
    ): Map<Long, DailyZoneSummary> {
        val cached = overlayRecent(read(context, age), snapshot, age, today, zone).toMutableMap()
        val client = HealthConnect.clientOrNull(context) ?: return cached
        val granted = HealthConnect.granted(client)
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return cached

        val rhrByDay = snapshot.restingHeartRate.associate { it.day to it.value }
        val fallbackRhr = snapshot.restingHeartRate.takeIf { it.isNotEmpty() }?.map { it.value }?.average()

        val oldest = today.minusDays(days.toLong() - 1).toEpochDay()
        // Only index completed past days; today & yesterday come from snapshot.heartRecent.
        val newestPast = today.minusDays(2).toEpochDay()
        if (newestPast < oldest) {
            onChunk(cached.toMap(), 0, 0)
            return cached
        }

        val missingDays = (oldest..newestPast).filter { it !in cached }.sortedDescending()
        val total = missingDays.size
        if (total == 0) {
            onChunk(cached.toMap(), 0, 0)
            return cached
        }

        var done = 0
        var cursor = missingDays.first()
        while (cursor >= oldest) {
            val chunkStart = maxOf(oldest, cursor - CHUNK_DAYS + 1)
            val neededInChunk = (chunkStart..cursor).filter { it !in cached }
            if (neededInChunk.isNotEmpty()) {
                val samplesByDay = readHeartChunk(
                    client = client,
                    from = LocalDate.ofEpochDay(chunkStart),
                    to = LocalDate.ofEpochDay(cursor),
                    zone = zone,
                )
                if (samplesByDay == null) {
                    // Stop cleanly on rate-limit / provider error; keep progress so far.
                    break
                }
                for (day in neededInChunk) {
                    val daySamples = samplesByDay[day].orEmpty()
                    cached[day] = activeZonesFromSamples(
                        day = day,
                        samples = daySamples,
                        age = age,
                        restingBpm = rhrByDay[day] ?: fallbackRhr,
                    )
                }
                done += neededInChunk.size
                // Persist only completed past days (< today - 1), and skip empty
                // entries within the last 5 days so delayed Fitbit Air syncs
                // are picked up on the next refresh instead of staying zeroed.
                val settledCutoff = today.minusDays(5).toEpochDay()
                write(
                    context,
                    cached.filter { (day, entry) ->
                        day <= newestPast && (entry.sampleCount > 0 || day <= settledCutoff)
                    },
                    age,
                )
                onChunk(cached.toMap(), done, total)
                delay(40)
            }
            cursor = chunkStart - 1
        }
        return cached
    }

    private suspend fun readHeartChunk(
        client: HealthConnectClient,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): Map<Long, List<HeartSample>>? = try {
        val byDay = linkedMapOf<Long, MutableList<HeartSample>>()
        var token: String? = null
        val startInstant = from.atStartOfDay(zone).toInstant()
        val endInstant = to.plusDays(1).atStartOfDay(zone).toInstant()
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    pageToken = token,
                ),
            )
            for (record in page.records) {
                for (sample in record.samples) {
                    val day = sample.time.atZone(zone).toLocalDate().toEpochDay()
                    byDay.getOrPut(day) { mutableListOf() }
                        .add(HeartSample(sample.time.toEpochMilli(), sample.beatsPerMinute.toInt()))
                }
            }
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        byDay
    } catch (e: Exception) {
        Log.w(TAG, "Health Connect: read HeartRateRecord chunk $from..$to failed", e)
        null
    }

    internal fun encode(entries: Map<Long, DailyZoneSummary>): String {
        val arr = JSONArray()
        entries.values.sortedBy { it.day }.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("d", entry.day)
                    put("m", entry.moderateMin)
                    put("c", entry.cardioMin)
                    put("p", entry.peakMin)
                    put("n", entry.sampleCount)
                    entry.ageUsed?.let { put("a", it) }
                    entry.rhrUsed?.let { put("r", it) }
                },
            )
        }
        return arr.toString()
    }

    internal fun decode(raw: String): Map<Long, DailyZoneSummary> = runCatching {
        val arr = JSONArray(raw)
        val out = linkedMapOf<Long, DailyZoneSummary>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val day = o.getLong("d")
            out[day] = DailyZoneSummary(
                day = day,
                moderateMin = o.getDouble("m"),
                cardioMin = o.getDouble("c"),
                peakMin = o.getDouble("p"),
                sampleCount = o.getInt("n"),
                ageUsed = if (o.has("a")) o.getInt("a") else null,
                rhrUsed = if (o.has("r")) o.getInt("r") else null,
            )
        }
        out
    }.getOrDefault(emptyMap())
}
