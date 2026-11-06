package com.srineere.airbridge.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/** One kilometre of a session, or the part-kilometre at the end of it. */
data class Split(val label: String, val distanceKm: Double, val time: Duration) {
    /** Seconds per kilometre, which is what a pace bar is drawn from. */
    val paceSeconds: Double
        get() = if (distanceKm <= 0.0) 0.0 else time.seconds / distanceKm
}

/**
 * Everything about one workout that the summary list does not carry.
 *
 * Every field is optional because every field is optional in the record: a
 * session logged by hand has a duration and nothing else, and this app will
 * not estimate a pace from a distance it was never given.
 */
data class SessionDetail(
    val distanceKm: Double? = null,
    val kcal: Double? = null,
    val samples: List<HeartSample> = emptyList(),
    val splits: List<Split> = emptyList(),
    /** Steps taken during it, from whichever source the tab counts. */
    val steps: Long? = null,
)


/**
 * The four figures a session's row carries, with none of its samples.
 *
 * Separate from [SessionDetail] on purpose. A list of six workouts that each
 * read their own per-second heart rate is tens of thousands of records to
 * print four numbers; these come from one aggregate call per session, which
 * is what an aggregate is for.
 */
data class SessionSummary(
    val distanceKm: Double? = null,
    val kcal: Double? = null,
    /** Steps taken during it, from whichever source the tab counts. */
    val steps: Long? = null,
    val averageBpm: Int? = null,
    val peakBpm: Int? = null,
    /**
     * The session's shape, as about a dozen averaged buckets.
     *
     * Enough to tell a steady walk from intervals, and nowhere near the
     * per-second detail the session's own screen reads. A row does not need
     * to know the exact beat at 14 minutes; it needs to show that there
     * were three pushes.
     */
    val trace: List<Double> = emptyList(),
    /** False when nothing may be read, which is not the same as none written. */
    val distanceAllowed: Boolean = true,
)

/**
 * Reads one session on demand.
 *
 * Deliberately not part of the snapshot: per-minute heart rate for every
 * workout in a week is thousands of samples that nothing on the tab draws.
 * This is the read that happens when you open a session and no sooner.
 */
class SessionReader {

    /**
     * One stretch of heart rate samples, for a night the snapshot does not
     * already carry.
     *
     * The tab reads two days of beats, which covers last night and no
     * further. Picking an older night off the chart asks a question about a
     * night nobody loaded, so it is read then and not before.
     */
    suspend fun heartSamples(context: Context, from: Instant, until: Instant): List<HeartSample> {
        val client = HealthConnect.clientOrNull(context) ?: return emptyList()
        if (!allowed(HealthConnect.granted(client), HeartRateRecord::class)) return emptyList()
        return records(client, HeartRateRecord::class, from, until)
            .flatMap { it.samples }
            .map { HeartSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) }
            .sortedBy { it.atMillis }
    }

    /**
     * Every workout in the last [days], newest first.
     *
     * The tab holds seven days because that is what its tile is about.
     * History is a different question and a heavier read, so it is asked
     * for only when someone opens the list that shows it.
     */
    suspend fun sessions(context: Context, days: Int): List<Session> {
        val client = HealthConnect.clientOrNull(context) ?: return emptyList()
        if (!allowed(HealthConnect.granted(client), ExerciseSessionRecord::class)) {
            return emptyList()
        }
        val until = Instant.now()
        val from = until.minus(Duration.ofDays(days.toLong()))
        return records(client, ExerciseSessionRecord::class, from, until)
            .map { Session(it.startTime, it.endTime, it.title, it.exerciseType) }
            .sortedByDescending { it.start }
    }

    /**
     * The one session that started at [startMillis], wherever it is.
     *
     * The tab's snapshot only reaches back a week, so a workout opened from
     * the history is one it has never heard of. Looked up by its start
     * rather than carried through the route, because a title and an
     * exercise type do not belong in a URL.
     */
    suspend fun session(context: Context, startMillis: Long): Session? {
        val client = HealthConnect.clientOrNull(context) ?: return null
        if (!allowed(HealthConnect.granted(client), ExerciseSessionRecord::class)) return null
        val at = Instant.ofEpochMilli(startMillis)
        // A day either side: long enough to contain any session that began
        // at that instant, short enough to be one small read.
        return records(
            client,
            ExerciseSessionRecord::class,
            at.minus(Duration.ofMinutes(1)),
            at.plus(Duration.ofDays(1)),
        )
            .firstOrNull { it.startTime.toEpochMilli() == startMillis }
            ?.let { Session(it.startTime, it.endTime, it.title, it.exerciseType) }
    }

    /** One aggregate call: what a row needs and nothing it does not. */
    suspend fun summary(context: Context, from: Instant, until: Instant): SessionSummary {
        val client = HealthConnect.clientOrNull(context) ?: return SessionSummary()
        val granted = HealthConnect.granted(client)
        val metrics = buildSet {
            if (allowed(granted, DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (allowed(granted, TotalCaloriesBurnedRecord::class)) {
                add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            }
            if (allowed(granted, HeartRateRecord::class)) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MAX)
            }
        }
        // Its own call, because steps are the one figure here that has to
        // be filtered by who wrote them, and that filter lives with the
        // reader that owns it rather than being copied to a second place.
        val steps = HealthReader().stepsIn(context, from, until)?.takeIf { it > 0 }
        if (metrics.isEmpty()) return SessionSummary(steps = steps)
        val result = guard("summarise session") {
            client.aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(from, until),
                ),
            )
        } ?: return SessionSummary(steps = steps)
        return SessionSummary(
            distanceKm = result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.takeIf { it > 0.0 },
            kcal = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
            steps = steps,
            averageBpm = result[HeartRateRecord.BPM_AVG]?.toInt(),
            peakBpm = result[HeartRateRecord.BPM_MAX]?.toInt(),
            trace = if (allowed(granted, HeartRateRecord::class)) trace(client, from, until)
            else emptyList(),
            distanceAllowed = allowed(granted, DistanceRecord::class),
        )
    }

    /**
     * A dozen or so averaged buckets across the session.
     *
     * The slice is the session's own length over twelve, floored at a
     * minute, so a twenty-minute walk and a two-hour ride both come back
     * with a line of about the same density.
     */
    private suspend fun trace(
        client: HealthConnectClient,
        from: Instant,
        until: Instant,
    ): List<Double> {
        val length = Duration.between(from, until)
        if (length < Duration.ofMinutes(4)) return emptyList()
        val slice = Duration.ofMinutes((length.toMinutes() / 12).coerceAtLeast(1))
        return guard("trace session") {
            client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(from, until),
                    timeRangeSlicer = slice,
                ),
            ).mapNotNull { it.result[HeartRateRecord.BPM_AVG]?.toDouble() }
        }.orEmpty()
    }

    suspend fun read(context: Context, from: Instant, until: Instant): SessionDetail {
        val client = HealthConnect.clientOrNull(context) ?: return SessionDetail()
        val granted = HealthConnect.granted(client)
        fun allowed(type: KClass<out Record>): Boolean =
            HealthPermission.getReadPermission(type) in granted

        val samples = if (allowed(HeartRateRecord::class)) {
            records(client, HeartRateRecord::class, from, until)
                .flatMap { it.samples }
                .map { HeartSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) }
                .sortedBy { it.atMillis }
        } else {
            emptyList()
        }

        val distanceRecords = if (allowed(DistanceRecord::class)) {
            records(client, DistanceRecord::class, from, until).sortedBy { it.startTime }
        } else {
            emptyList()
        }
        val distance = distanceRecords
            .sumOf { it.distance.inKilometers }
            .takeIf { it > 0.0 }

        val kcal = if (allowed(TotalCaloriesBurnedRecord::class)) {
            guard("session calories") {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(from, until),
                    ),
                )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
            }
        } else {
            null
        }

        return SessionDetail(
            distanceKm = distance,
            kcal = kcal,
            samples = samples,
            splits = splitsOf(distanceRecords, from),
            // Through the reader that owns the source filter, so a session
            // counts the same steps the day around it counts.
            steps = HealthReader().stepsIn(context, from, until)?.takeIf { it > 0 },
        )
    }

    /**
     * Kilometre splits, built by running the distance segments forward and
     * noting the time each whole kilometre was crossed.
     *
     * The crossing is interpolated inside the segment it happened in, which
     * assumes an even pace across that one segment only — a few seconds of
     * error, against segments that are usually under a minute long. If the
     * writer recorded the whole session as one distance there is nothing to
     * interpolate between, and there are no splits rather than fabricated ones.
     */
    private fun splitsOf(records: List<DistanceRecord>, from: Instant): List<Split> {
        if (records.size < 4) return emptyList()
        val splits = mutableListOf<Split>()
        var covered = 0.0
        var lastCrossing = from
        var nextKm = 1.0

        records.forEach { record ->
            val length = record.distance.inKilometers
            if (length <= 0.0) return@forEach
            val span = Duration.between(record.startTime, record.endTime)
            while (covered + length >= nextKm) {
                val into = (nextKm - covered) / length
                val at = record.startTime.plusMillis((span.toMillis() * into).toLong())
                splits += Split(
                    label = nextKm.toInt().toString(),
                    distanceKm = 1.0,
                    time = Duration.between(lastCrossing, at),
                )
                lastCrossing = at
                nextKm += 1.0
            }
            covered += length
        }

        val remainder = covered - (nextKm - 1.0)
        if (remainder > 0.05) {
            val last = records.last().endTime
            splits += Split(
                label = String.format(java.util.Locale.US, "%.1f", remainder),
                distanceKm = remainder,
                time = Duration.between(lastCrossing, last),
            )
        }
        return splits
    }

    private fun allowed(granted: Set<String>, type: KClass<out Record>): Boolean =
        HealthPermission.getReadPermission(type) in granted

    private suspend fun <T : Record> records(
        client: HealthConnectClient,
        type: KClass<T>,
        from: Instant,
        until: Instant,
    ): List<T> = guard("read ${type.simpleName}") {
        val rows = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from, until),
                    pageToken = token,
                ),
            )
            rows += page.records
            token = page.pageToken
        } while (!token.isNullOrEmpty())
        rows
    }.orEmpty()

    /**
     * Health Connect throws for rate limits and for a provider that went
     * away mid-call. A session screen with a gap in it beats one that
     * crashes on the way in.
     */
    private inline fun <T> guard(what: String, block: () -> T): T? =
        runCatching(block).onFailure { Log.w("AirBridge", "Could not $what", it) }.getOrNull()
}
