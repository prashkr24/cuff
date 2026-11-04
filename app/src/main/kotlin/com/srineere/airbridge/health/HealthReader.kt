package com.srineere.airbridge.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** The four stages this app recognises, however many a writer distinguishes. */
enum class SleepStage { Deep, Light, Rem, Awake }

/** One stretch of one stage, which is what a hypnogram is drawn from. */
data class SleepSegment(val stage: SleepStage, val start: Instant, val end: Instant) {
    val length: Duration get() = Duration.between(start, end)
}

/** One night's sleep, with its stages when the writer supplied them. */
data class Night(
    val day: Long,
    val start: Instant,
    val end: Instant,
    val asleep: Duration,
    val awake: Duration,
    val light: Duration,
    val deep: Duration,
    val rem: Duration,
    /** In order, empty for a night written as one block. */
    val segments: List<SleepSegment> = emptyList(),
) {
    /** Asleep against time in bed. Null when the session has no length. */
    val efficiency: Double?
        get() {
            val inBed = Duration.between(start, end).seconds
            return if (inBed <= 0) null else asleep.seconds.toDouble() / inBed
        }

    /** True when the writer broke the night down; false when it is one block. */
    val hasStages: Boolean
        get() = (light + deep + rem).seconds > 0

    /**
     * Times you woke in the middle, not counting settling at either end.
     *
     * A wake at the very start or the very end of the session is going to
     * bed and getting up; counting those would give everyone two.
     */
    val wakes: Int
        get() = segments.filterIndexed { index, segment ->
            segment.stage == SleepStage.Awake &&
                index != 0 && index != segments.lastIndex &&
                segment.length >= Duration.ofMinutes(1)
        }.size

    /** The middle of the night, which is what consistency is measured on. */
    val midpoint: Instant
        get() = start.plusSeconds(Duration.between(start, end).seconds / 2)
}

/** One workout, with whatever Health Connect actually held for it. */
data class Session(
    val start: Instant,
    val end: Instant,
    val title: String?,
    val exerciseType: Int,
)

/**
 * One thing eaten, as whatever app wrote it recorded it.
 *
 * Every field but the time is optional, because every field but the time is
 * optional in Health Connect: an entry can be a name with no calories, or
 * calories with no name, and both are real entries someone made.
 */
data class FoodEntry(
    /** Health Connect's own id, which is what deleting it needs. */
    val id: String,
    val at: Instant,
    val day: Long,
    val name: String?,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    /** Which meal it was filed under, in Health Connect's numbering. */
    val mealType: Int = 0,
)

/** What the Health tab draws. Any field may be absent; none is faked. */
data class HealthSnapshot(
    val stepsToday: Long? = null,
    val steps: List<Reading> = emptyList(),
    val restingHeartRate: List<Reading> = emptyList(),
    val hrv: List<Reading> = emptyList(),
    val respiratoryRate: List<Reading> = emptyList(),
    val skinTemperature: List<Reading> = emptyList(),
    val oxygenSaturation: List<Reading> = emptyList(),
    val nights: List<Night> = emptyList(),
    val sessions: List<Session> = emptyList(),
    /** Today's heart rate samples, for the zones. */
    val heartToday: List<HeartSample> = emptyList(),
    /** Today's and yesterday's, which is what last night needs. */
    val heartRecent: List<HeartSample> = emptyList(),
    val burnedDaily: List<Reading> = emptyList(),
    val eatenDaily: List<Reading> = emptyList(),
    val weight: List<Reading> = emptyList(),
    /** Today, hour by hour: 24 figures, from midnight. */
    val burnedHourly: List<Double> = emptyList(),
    val stepsHourly: List<Double> = emptyList(),
    val burnedToday: Double? = null,
    val eatenToday: Double? = null,
    val proteinDaily: List<Reading> = emptyList(),
    val carbsDaily: List<Reading> = emptyList(),
    val fatDaily: List<Reading> = emptyList(),
    /** Separate food entries a day, which is how complete a log can be judged. */
    val mealsDaily: Map<Long, Int> = emptyMap(),
    /** Every food entry in the window, oldest first. */
    val food: List<FoodEntry> = emptyList(),
    val proteinToday: Double? = null,
    val carbsToday: Double? = null,
    val fatToday: Double? = null,
    /** When Google Health last wrote anything we read, so screens can age it. */
    val writtenAt: Instant? = null,
    val missingPermissions: Set<String> = emptySet(),
)

/**
 * Reads Health Connect into a [HealthSnapshot].
 *
 * A week here always means the last seven days, counted back from today, and
 * never Monday to Sunday. Rolling windows are what a trend needs: a calendar
 * week is two days long on a Tuesday, so "this week" would mean something
 * different every morning and a Monday would always look like a collapse.
 * The only place a calendar boundary is allowed is a month view, where the
 * month is the thing being named.
 *
 * Two rules run through all of it. Every read is guarded by its own
 * permission, because a person may grant steps and refuse sleep and the tab
 * has to come back with the half it is allowed. And every read is wrapped:
 * Health Connect throws for rate limits and for a provider that went away
 * mid-call, and a tab that crashes on a missing night is worse than a tab
 * with a gap in it.
 */
class HealthReader(private val zone: ZoneId = ZoneId.systemDefault()) {

    suspend fun read(context: Context, days: Int = 30): HealthSnapshot {
        val client = HealthConnect.clientOrNull(context) ?: return HealthSnapshot(
            missingPermissions = HealthConnect.readPermissions,
        )
        val granted = HealthConnect.granted(client)
        val today = LocalDate.now(zone)
        val from = today.minusDays(days.toLong() - 1)

        fun allowed(type: KClass<out Record>): Boolean =
            HealthPermission.getReadPermission(type) in granted

        val steps = if (allowed(StepsRecord::class)) {
            dailyTotals(client, StepsRecord.COUNT_TOTAL, from, today) { it.toDouble() }
        } else {
            emptyList()
        }

        val resting = if (allowed(RestingHeartRateRecord::class)) {
            // BPM_AVG is a Long: Health Connect keeps resting rate whole.
            dailyTotals(client, RestingHeartRateRecord.BPM_AVG, from, today) { it.toDouble() }
        } else {
            emptyList()
        }

        val hrv = if (allowed(HeartRateVariabilityRmssdRecord::class)) {
            // No aggregate exists for RMSSD, so the records are read and
            // folded to one figure a night -- the last of the night, which is
            // the one a tracker means by "your HRV".
            records(client, HeartRateVariabilityRmssdRecord::class, from, today)
                .groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
                .map { (day, rows) -> Reading(day, rows.last().heartRateVariabilityMillis) }
                .sortedBy { it.day }
        } else {
            emptyList()
        }

        val breathing = if (allowed(RespiratoryRateRecord::class)) {
            records(client, RespiratoryRateRecord::class, from, today)
                .groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
                .map { (day, rows) -> Reading(day, rows.last().rate) }
                .sortedBy { it.day }
        } else {
            emptyList()
        }

        // Skin temperature is written as a change from your own baseline
        // rather than an absolute, so the aggregate is the honest read.
        val skin = if (allowed(SkinTemperatureRecord::class)) {
            dailyTotals(client, SkinTemperatureRecord.TEMPERATURE_DELTA_AVG, from, today) {
                it.inCelsius
            }
        } else {
            emptyList()
        }

        val oxygen = if (allowed(OxygenSaturationRecord::class)) {
            records(client, OxygenSaturationRecord::class, from, today)
                .groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
                .map { (day, rows) -> Reading(day, rows.last().percentage.value) }
                .sortedBy { it.day }
        } else {
            emptyList()
        }

        val nights = if (allowed(SleepSessionRecord::class)) {
            records(client, SleepSessionRecord::class, from, today).map(::toNight)
        } else {
            emptyList()
        }

        // Which types are allowed but empty is the question behind half the
        // dashes on this tab, and it cannot be answered from the screen: a
        // metric nobody granted and a metric nobody wrote look identical.
        Log.i(
            "AirBridge",
            "health: missing=" + (HealthConnect.readPermissions - granted)
                .map { it.substringAfterLast('.') } +
                " distanceAllowed=" + allowed(DistanceRecord::class) +
                " counts: resting=${resting.size} hrv=${hrv.size}" +
                " breathing=${breathing.size} skin=${skin.size} oxygen=${oxygen.size}",
        )

        val sessions = if (allowed(ExerciseSessionRecord::class)) {
            records(client, ExerciseSessionRecord::class, today.minusDays(6), today)
                .map { Session(it.startTime, it.endTime, it.title, it.exerciseType) }
                .sortedByDescending { it.start }
        } else {
            emptyList()
        }

        // Yesterday as well as today, in one read rather than two: last
        // night started yesterday, and the sleeping heart rate is taken from
        // the samples inside it.
        val heartRecent = if (allowed(HeartRateRecord::class)) {
            records(client, HeartRateRecord::class, today.minusDays(1), today)
                .flatMap { record -> record.samples }
                .map { HeartSample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) }
                .sortedBy { it.atMillis }
        } else {
            emptyList()
        }
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val heartToday = heartRecent.filter { it.atMillis >= startOfToday }

        // Today by the hour, for the two running totals and for the hourly
        // step pattern. Twenty-four buckets from one call each, which is
        // cheaper than it looks: these are aggregates, not records.
        val burnedHourly = if (allowed(TotalCaloriesBurnedRecord::class)) {
            hourlyTotals(client, TotalCaloriesBurnedRecord.ENERGY_TOTAL, today) {
                it.inKilocalories
            }
        } else {
            emptyList()
        }
        val stepsHourly = if (allowed(StepsRecord::class)) {
            hourlyTotals(client, StepsRecord.COUNT_TOTAL, today) { it.toDouble() }
        } else {
            emptyList()
        }

        val burnedDaily = if (allowed(TotalCaloriesBurnedRecord::class)) {
            dailyTotals(client, TotalCaloriesBurnedRecord.ENERGY_TOTAL, from, today) {
                it.inKilocalories
            }
        } else {
            emptyList()
        }

        val eatenDaily = if (allowed(NutritionRecord::class)) {
            dailyTotals(client, NutritionRecord.ENERGY_TOTAL, from, today) { it.inKilocalories }
        } else {
            emptyList()
        }

        // Daily weight is noisy, so the screen draws the trend rather than
        // the last figure; the reader just hands over what was written.
        val weight = if (allowed(WeightRecord::class)) {
            records(client, WeightRecord::class, from, today)
                .groupBy { it.time.atZone(zone).toLocalDate().toEpochDay() }
                .map { (day, rows) -> Reading(day, rows.last().weight.inKilograms) }
                .sortedBy { it.day }
        } else {
            emptyList()
        }

        val burned = if (allowed(TotalCaloriesBurnedRecord::class)) {
            total(client, TotalCaloriesBurnedRecord.ENERGY_TOTAL, today)?.inKilocalories
        } else {
            null
        }

        val eaten = if (allowed(NutritionRecord::class)) {
            total(client, NutritionRecord.ENERGY_TOTAL, today)?.inKilocalories
        } else {
            null
        }
        // Macros a day as well as today's, for the split over a fortnight.
        // Three more aggregates, which is the cost of showing whether the
        // protein line moved rather than only where it is now.
        val proteinDaily = if (allowed(NutritionRecord::class)) {
            dailyTotals(client, NutritionRecord.PROTEIN_TOTAL, from, today) { it.inGrams }
        } else {
            emptyList()
        }
        val carbsDaily = if (allowed(NutritionRecord::class)) {
            dailyTotals(client, NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL, from, today) {
                it.inGrams
            }
        } else {
            emptyList()
        }
        val fatDaily = if (allowed(NutritionRecord::class)) {
            dailyTotals(client, NutritionRecord.TOTAL_FAT_TOTAL, from, today) { it.inGrams }
        } else {
            emptyList()
        }

        // How many separate entries each day holds. A single 1,900 kcal
        // entry and six entries adding to 1,900 are different kinds of day,
        // and only the count can tell them apart: one day in a month with a
        // "deficit" of 2,000 is almost always a day nobody logged.
        val food = if (allowed(NutritionRecord::class)) {
            records(client, NutritionRecord::class, from, today).map { entry ->
                FoodEntry(
                    id = entry.metadata.id,
                    at = entry.startTime,
                    day = entry.startTime.atZone(zone).toLocalDate().toEpochDay(),
                    name = entry.name,
                    kcal = entry.energy?.inKilocalories,
                    protein = entry.protein?.inGrams,
                    carbs = entry.totalCarbohydrate?.inGrams,
                    fat = entry.totalFat?.inGrams,
                    mealType = entry.mealType,
                )
            }.sortedBy { it.at }
        } else {
            emptyList()
        }
        val mealsDaily = food.groupingBy { it.day }.eachCount()

        val protein = if (allowed(NutritionRecord::class)) {
            total(client, NutritionRecord.PROTEIN_TOTAL, today)?.inGrams
        } else {
            null
        }
        val carbs = if (allowed(NutritionRecord::class)) {
            total(client, NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL, today)?.inGrams
        } else {
            null
        }
        val fat = if (allowed(NutritionRecord::class)) {
            total(client, NutritionRecord.TOTAL_FAT_TOTAL, today)?.inGrams
        } else {
            null
        }

        // A trend needs three readings, so a tile with no chip and a tile
        // with nothing to compare against look identical from the outside.
        // These are the counts that tell them apart.
        Log.i(
            "AirBridge",
            "health: eaten=${eatenDaily.size} burned=${burnedDaily.size}" +
                " weight=${weight.size} sessions=${sessions.size}" +
                " sessionDays=${sessions.map { it.start.atZone(zone).toLocalDate() }.distinct().size}",
        )

        return HealthSnapshot(
            stepsToday = steps.lastOrNull { it.day == today.toEpochDay() }?.value?.toLong(),
            steps = steps,
            restingHeartRate = resting,
            hrv = hrv,
            respiratoryRate = breathing,
            skinTemperature = skin,
            oxygenSaturation = oxygen,
            nights = nights,
            sessions = sessions,
            heartToday = heartToday,
            heartRecent = heartRecent,
            burnedDaily = burnedDaily,
            eatenDaily = eatenDaily,
            proteinDaily = proteinDaily,
            carbsDaily = carbsDaily,
            fatDaily = fatDaily,
            mealsDaily = mealsDaily,
            food = food,
            burnedHourly = burnedHourly,
            stepsHourly = stepsHourly,
            weight = weight,
            burnedToday = burned,
            eatenToday = eaten,
            proteinToday = protein,
            carbsToday = carbs,
            fatToday = fat,
            writtenAt = Instant.now(),
            missingPermissions = HealthConnect.readPermissions - granted,
        )
    }

    /**
     * Today's steps, and the steps in the hour now running.
     *
     * The goal poll runs every quarter of an hour, so it reads these two
     * figures rather than the whole tab: Health Connect rate-limits callers,
     * and a background job that pulls thirty days of sleep to decide whether
     * to buzz would be the first thing to get throttled.
     */
    suspend fun stepsNow(context: Context): Pair<Long, Long>? {
        val client = HealthConnect.clientOrNull(context) ?: return null
        if (HealthPermission.getReadPermission(StepsRecord::class) !in HealthConnect.granted(client)) {
            return null
        }
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val hourStart = now.atZone(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant()

        val day = guard("steps today") {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                ),
            )[StepsRecord.COUNT_TOTAL]
        } ?: 0L

        val hour = guard("steps this hour") {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(hourStart, now),
                ),
            )[StepsRecord.COUNT_TOTAL]
        } ?: 0L

        return day to hour
    }

    /**
     * One figure a day. Period buckets refuse an Instant range -- they take
     * LocalDateTime, and handing them the wrong one throws rather than
     * returning nothing, which is why the conversion lives here and not at
     * each call site.
     */
    private suspend fun <T : Any> dailyTotals(
        client: HealthConnectClient,
        metric: AggregateMetric<T>,
        from: LocalDate,
        to: LocalDate,
        asDouble: (T) -> Double,
    ): List<Reading> = guard("daily aggregate") {
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(
                    from.atStartOfDay(),
                    to.plusDays(1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).mapNotNull { bucket ->
            bucket.result[metric]?.let { value ->
                Reading(bucket.startTime.toLocalDate().toEpochDay(), asDouble(value))
            }
        }
    }.orEmpty()

    /** One figure for one day. */
    /**
     * One figure an hour, across one day, from midnight.
     *
     * Always twenty-four entries. An hour Health Connect has nothing for is
     * a zero rather than a missing bucket, because the chart these feed is a
     * running total and a gap in the middle of one is meaningless.
     */
    private suspend fun <T : Any> hourlyTotals(
        client: HealthConnectClient,
        metric: AggregateMetric<T>,
        day: LocalDate,
        convert: (T) -> Double,
    ): List<Double> = guard("read hourly ${metric.metricKey}") {
        val start = day.atStartOfDay(zone).toInstant()
        val buckets = client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(start, start.plus(Duration.ofDays(1))),
                timeRangeSlicer = Duration.ofHours(1),
            ),
        )
        val hours = DoubleArray(24)
        buckets.forEach { bucket ->
            val value = bucket.result[metric] ?: return@forEach
            val hour = Duration.between(start, bucket.startTime).toHours().toInt()
            if (hour in 0..23) hours[hour] = convert(value)
        }
        hours.toList()
    } ?: List(24) { 0.0 }

    private suspend fun <T : Any> total(
        client: HealthConnectClient,
        metric: AggregateMetric<T>,
        day: LocalDate,
    ): T? = guard("day total") {
        client.aggregate(
            AggregateRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(
                    day.atStartOfDay(zone).toInstant(),
                    day.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )[metric]
    }

    private suspend fun <T : androidx.health.connect.client.records.Record> records(
        client: HealthConnectClient,
        type: KClass<T>,
        from: LocalDate,
        to: LocalDate,
    ): List<T> = guard("read ${type.simpleName}") {
        val rows = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(zone).toInstant(),
                        to.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                    pageToken = token,
                ),
            )
            rows += page.records
            token = page.pageToken
            // Some providers hand back "" instead of null when the pages run
            // out, and checking only for null spins forever.
        } while (!token.isNullOrEmpty())
        rows
    }.orEmpty()

    private fun toNight(record: SleepSessionRecord): Night {
        var awake = Duration.ZERO
        var light = Duration.ZERO
        var deep = Duration.ZERO
        var rem = Duration.ZERO
        val segments = mutableListOf<SleepSegment>()
        record.stages.forEach { stage ->
            val length = Duration.between(stage.startTime, stage.endTime)
            val named = when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
                -> SleepStage.Awake.also { awake += length }
                SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.Deep.also { deep += length }
                SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.Rem.also { rem += length }
                SleepSessionRecord.STAGE_TYPE_LIGHT,
                SleepSessionRecord.STAGE_TYPE_SLEEPING,
                -> SleepStage.Light.also { light += length }
                // UNKNOWN is a stage the writer could not name. It keeps its
                // place in the night rather than being guessed at as light.
                else -> null
            }
            if (named != null) segments += SleepSegment(named, stage.startTime, stage.endTime)
        }
        val staged = light + deep + rem
        // A night written without stages still has a length; use it rather
        // than reporting zero sleep for a night that plainly happened.
        val asleep = if (staged.isZero) {
            Duration.between(record.startTime, record.endTime)
        } else {
            staged
        }
        return Night(
            // A night belongs to the morning it ends on, which is how every
            // other app files it and how anyone talks about "last night".
            day = record.endTime.atZone(zone).toLocalDate().toEpochDay(),
            start = record.startTime,
            end = record.endTime,
            asleep = asleep,
            awake = awake,
            light = light,
            deep = deep,
            rem = rem,
            segments = segments,
        )
    }

    private inline fun <T> guard(what: String, block: () -> T): T? =
        try {
            block()
        } catch (error: Exception) {
            // Rate limits arrive as IllegalStateException, a provider that
            // went away as RemoteException, a revoked grant as
            // SecurityException. None is worth losing the rest of the tab.
            Log.w(TAG, "Health Connect: $what failed", error)
            null
        }

    private companion object {
        const val TAG = "AirBridge"
    }
}
