package com.srineere.airbridge.widget

import android.content.Context
import com.srineere.airbridge.health.FitnessProfile
import com.srineere.airbridge.health.FitnessProfileStore
import com.srineere.airbridge.health.FoodEntry
import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthGoals
import com.srineere.airbridge.health.HealthSettings
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.HeartSample
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.RecordedBy
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.readinessToday
import com.srineere.airbridge.ui.healthDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The last read, kept on disk, so a widget drawn by a process that has
 * never read anything still draws figures rather than dashes.
 *
 * [WidgetSnapshotCache] used to hold its one snapshot in memory and nowhere
 * else. That is right for the question it was built to answer -- four
 * widgets must cost one Health Connect read -- and wrong for every cold
 * start: a reboot, an update, or Android reclaiming a process that nothing
 * has been in front of for an hour. The next draw then had no figures at
 * all, and the home screen came back as a field of empty cards while a
 * fresh read went off to Health Connect.
 *
 * So the read is also written here, and a cold process draws what it kept
 * before it reads anything. The figures are a little old and they say so --
 * see [withoutToday], which is the whole of what "a little old" is allowed
 * to mean.
 *
 * **This duplicates a cache the app side is growing in `health/`.** The two
 * were written at the same time by two people who could not see each
 * other's work; neither is wrong, and one of them should go. This one is
 * deliberately self-contained inside `widget/` so that removing it is a
 * matter of deleting a file and pointing [restoreWidgetSnapshot] at the
 * other.
 */
internal data class KeptSnapshot(
    val health: HealthSnapshot,
    /**
     * When the read behind this actually happened.
     *
     * Never moved forward on restore, and that is the point of it. Half
     * this app's drawing is age-aware -- a battery dimmed after a day, a
     * night that counts as last night only if it ended this morning -- and
     * a restored snapshot that claimed to be new would turn every one of
     * those into a lie told confidently.
     */
    val readAt: Long,
    /** How far back the read went, so a wider need can notice a short one. */
    val days: Int,
)

/**
 * The format, and the only version this build will read.
 *
 * Exactly this number, not a range. The entry is a cache and there is
 * nothing in it that cannot be read again in half a minute, so a row from
 * a build that meant something else by the same key is thrown away whole
 * rather than migrated -- which is the cheap end of the "fail closed"
 * invariant, and the one place it costs nothing to obey.
 */
private const val KEPT_VERSION = 2

/**
 * Every series is an array of arrays, never an array of objects.
 *
 * A month of readings is about five hundred rows across the nine series
 * below, and a named object per row costs four times what a pair costs.
 * The price of that is a rigid format: a field added anywhere in the
 * middle changes the meaning of every position after it, which is what
 * [KEPT_VERSION] is for. Positions are documented at each encoder.
 */
private fun readingsOf(list: List<Reading>): JSONArray =
    JSONArray().apply { list.forEach { put(JSONArray().put(it.day).put(it.value)) } }

private fun readings(array: JSONArray): List<Reading> =
    (0 until array.length()).map { index ->
        val row = array.getJSONArray(index)
        Reading(row.getLong(0), row.getDouble(1))
    }

private fun doublesOf(list: List<Double>): JSONArray =
    JSONArray().apply { list.forEach { put(it) } }

private fun doubles(array: JSONArray): List<Double> =
    (0 until array.length()).map { array.getDouble(it) }

/**
 * `[day, start, end, asleep, awake, light, deep, rem, inBed, naps,
 * napAsleep]` -- every duration in seconds, the two instants in millis.
 */
private fun nightsOf(list: List<Night>): JSONArray = JSONArray().apply {
    list.forEach { night ->
        put(
            JSONArray()
                .put(night.day)
                .put(night.start.toEpochMilli())
                .put(night.end.toEpochMilli())
                .put(night.asleep.seconds)
                .put(night.awake.seconds)
                .put(night.light.seconds)
                .put(night.deep.seconds)
                .put(night.rem.seconds)
                // Time in bed is kept because the two sleep faces draw
                // efficiency from it, and once a day's naps are folded in
                // it is no longer the span from start to end.
                .put(night.inBed.seconds)
                .put(night.naps)
                .put(night.napAsleep.seconds)
        )
    }
}

/**
 * The stages are not kept, and that is deliberate.
 *
 * `Night.segments` is the hypnogram, and no widget draws one: the two
 * sleep faces ask a night for its durations, its efficiency and whether it
 * [Night.hasStages] at all -- and `hasStages` is answered by light, deep
 * and rem, which are kept, rather than by the segments. Thirty nights of
 * segments would be the better part of two thousand rows for a picture
 * nothing on the home screen draws.
 *
 * The one thing that degrades is `Night.wakes`, which counts segments and
 * would read zero. Nothing in `widget/` asks it. Anything that starts to
 * must keep the segments too.
 */
private fun nights(array: JSONArray): List<Night> =
    (0 until array.length()).map { index ->
        val row = array.getJSONArray(index)
        Night(
            day = row.getLong(0),
            start = Instant.ofEpochMilli(row.getLong(1)),
            end = Instant.ofEpochMilli(row.getLong(2)),
            asleep = Duration.ofSeconds(row.getLong(3)),
            awake = Duration.ofSeconds(row.getLong(4)),
            light = Duration.ofSeconds(row.getLong(5)),
            deep = Duration.ofSeconds(row.getLong(6)),
            rem = Duration.ofSeconds(row.getLong(7)),
            inBed = Duration.ofSeconds(row.getLong(8)),
            naps = row.getInt(9),
            napAsleep = Duration.ofSeconds(row.getLong(10)),
        )
    }

/** `[start, end, title, exerciseType, deviceType, manufacturer, model]`. */
private fun sessionsOf(list: List<Session>): JSONArray = JSONArray().apply {
    list.forEach { session ->
        put(
            JSONArray()
                .put(session.start.toEpochMilli())
                .put(session.end.toEpochMilli())
                .put(session.title ?: JSONObject.NULL)
                .put(session.exerciseType)
                .put(session.device?.type ?: JSONObject.NULL)
                .put(session.device?.manufacturer ?: JSONObject.NULL)
                .put(session.device?.model ?: JSONObject.NULL)
        )
    }
}

private fun sessions(array: JSONArray): List<Session> =
    (0 until array.length()).map { index ->
        val row = array.getJSONArray(index)
        Session(
            start = Instant.ofEpochMilli(row.getLong(0)),
            end = Instant.ofEpochMilli(row.getLong(1)),
            title = row.stringOrNull(2),
            exerciseType = row.getInt(3),
            // A record with no device is a different fact from a device
            // this row forgot, so the whole of it is kept or none of it.
            device = if (row.isNull(4)) null else RecordedBy(
                type = row.getInt(4),
                manufacturer = row.stringOrNull(5),
                model = row.stringOrNull(6),
            ),
        )
    }

/** `[id, at, day, name, kcal, protein, carbs, fat, mealType, source]`. */
private fun foodOf(list: List<FoodEntry>): JSONArray = JSONArray().apply {
    list.forEach { entry ->
        put(
            JSONArray()
                .put(entry.id)
                .put(entry.at.toEpochMilli())
                .put(entry.day)
                .put(entry.name ?: JSONObject.NULL)
                .put(entry.kcal ?: JSONObject.NULL)
                .put(entry.protein ?: JSONObject.NULL)
                .put(entry.carbs ?: JSONObject.NULL)
                .put(entry.fat ?: JSONObject.NULL)
                .put(entry.mealType)
                .put(entry.source ?: JSONObject.NULL)
        )
    }
}

private fun food(array: JSONArray): List<FoodEntry> =
    (0 until array.length()).map { index ->
        val row = array.getJSONArray(index)
        FoodEntry(
            id = row.getString(0),
            at = Instant.ofEpochMilli(row.getLong(1)),
            day = row.getLong(2),
            name = row.stringOrNull(3),
            kcal = row.doubleOrNull(4),
            protein = row.doubleOrNull(5),
            carbs = row.doubleOrNull(6),
            fat = row.doubleOrNull(7),
            mealType = row.getInt(8),
            source = row.stringOrNull(9),
        )
    }

/** `[atMillis, bpm]`. */
private fun heartOf(list: List<HeartSample>): JSONArray =
    JSONArray().apply { list.forEach { put(JSONArray().put(it.atMillis).put(it.bpm)) } }

private fun heart(array: JSONArray): List<HeartSample> =
    (0 until array.length()).map { index ->
        val row = array.getJSONArray(index)
        HeartSample(row.getLong(0), row.getInt(1))
    }

/**
 * Today's beats, kept at one every five minutes rather than all of them.
 *
 * This is the only series here with a real size to it: a band writing
 * continuously puts several thousand samples into a day, and the kept
 * entry is a preferences file that is rewritten whole every half hour.
 *
 * The highest of each bucket, not the first and not the mean, because the
 * one thing any widget draws from these is the highest sample in each
 * *hour* -- `byHour` in WidgetTiles. Five minutes divides an hour exactly,
 * so the hourly maximum of the thinned series is the hourly maximum of the
 * whole one, and the restored chart is the chart that was there before.
 * Every sample kept is a real sample at its real time; none is invented.
 *
 * Anything that starts drawing these as a line rather than as hourly bars
 * wants the whole series, and should say so here.
 */
internal fun thinHeart(
    samples: List<HeartSample>,
    bucketMillis: Long = FIVE_MINUTES,
): List<HeartSample> =
    samples.groupBy { Math.floorDiv(it.atMillis, bucketMillis) }
        .mapNotNull { (_, run) -> run.maxByOrNull { it.bpm } }
        .sortedBy { it.atMillis }

private const val FIVE_MINUTES = 5L * 60 * 1000

private fun JSONArray.stringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun JSONArray.doubleOrNull(index: Int): Double? =
    if (isNull(index)) null else getDouble(index)

private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)

/**
 * What was read, as one line of JSON.
 *
 * `day` is the local day the read happened on, and it is not the same
 * question as `readAt`: the figures below that mean "today" are only true
 * on the day they were taken, and a clock reading cannot say which day a
 * phone that has crossed a time zone is on. See [withoutToday].
 */
internal fun encodeKept(kept: KeptSnapshot, zone: ZoneId = ZoneId.systemDefault()): String {
    val health = kept.health
    return JSONObject()
        .put("version", KEPT_VERSION)
        .put("readAt", kept.readAt)
        .put("day", Instant.ofEpochMilli(kept.readAt).atZone(zone).toLocalDate().toEpochDay())
        .put("days", kept.days)
        .put("steps", readingsOf(health.steps))
        .put("resting", readingsOf(health.restingHeartRate))
        .put("hrv", readingsOf(health.hrv))
        .put("breathing", readingsOf(health.respiratoryRate))
        .put("skin", readingsOf(health.skinTemperature))
        .put("vo2", readingsOf(health.vo2Max))
        .put("weight", readingsOf(health.weight))
        .put("burnedDaily", readingsOf(health.burnedDaily))
        .put("eatenDaily", readingsOf(health.eatenDaily))
        .put("nights", nightsOf(health.nights))
        .put("sessions", sessionsOf(health.sessions))
        .put("food", foodOf(health.food))
        .put("heart", heartOf(thinHeart(health.heartToday)))
        .put("burnedHourly", doublesOf(health.burnedHourly))
        .put("stepsHourly", doublesOf(health.stepsHourly))
        .put("stepsToday", health.stepsToday ?: JSONObject.NULL)
        .put("burnedToday", health.burnedToday ?: JSONObject.NULL)
        .put("eatenToday", health.eatenToday ?: JSONObject.NULL)
        .put("proteinToday", health.proteinToday ?: JSONObject.NULL)
        .put("carbsToday", health.carbsToday ?: JSONObject.NULL)
        .put("fatToday", health.fatToday ?: JSONObject.NULL)
        .toString()
}

/**
 * Nothing at all, for anything this build cannot make complete sense of.
 *
 * Every series is read with `getJSONArray`, which throws for a key that is
 * missing, so a truncated or hand-edited row decodes to null rather than
 * to the half of it that parsed. That is the invariant: a widget drawing a
 * figure it believes is yesterday's steps, when the row that held them was
 * cut off mid-write, is worse than a widget drawing a dash.
 *
 * [today] is the local epoch day now, passed in rather than asked for, so
 * the day rule below can be tested without moving a clock.
 */
internal fun decodeKept(raw: String?, today: Long): KeptSnapshot? {
    if (raw.isNullOrEmpty()) return null
    return runCatching {
        val root = JSONObject(raw)
        require(root.getInt("version") == KEPT_VERSION)
        val health = HealthSnapshot(
            stepsToday = root.longOrNull("stepsToday"),
            steps = readings(root.getJSONArray("steps")),
            restingHeartRate = readings(root.getJSONArray("resting")),
            hrv = readings(root.getJSONArray("hrv")),
            respiratoryRate = readings(root.getJSONArray("breathing")),
            skinTemperature = readings(root.getJSONArray("skin")),
            vo2Max = readings(root.getJSONArray("vo2")),
            weight = readings(root.getJSONArray("weight")),
            burnedDaily = readings(root.getJSONArray("burnedDaily")),
            eatenDaily = readings(root.getJSONArray("eatenDaily")),
            nights = nights(root.getJSONArray("nights")),
            sessions = sessions(root.getJSONArray("sessions")),
            food = food(root.getJSONArray("food")),
            heartToday = heart(root.getJSONArray("heart")),
            burnedHourly = doubles(root.getJSONArray("burnedHourly")),
            stepsHourly = doubles(root.getJSONArray("stepsHourly")),
            burnedToday = root.doubleOrNull("burnedToday"),
            eatenToday = root.doubleOrNull("eatenToday"),
            proteinToday = root.doubleOrNull("proteinToday"),
            carbsToday = root.doubleOrNull("carbsToday"),
            fatToday = root.doubleOrNull("fatToday"),
        )
        KeptSnapshot(
            health = if (root.getLong("day") == today) health else health.withoutToday(),
            readAt = root.getLong("readAt"),
            days = root.getInt("days"),
        )
    }.getOrNull()
}

/**
 * The same reading with everything that meant "today" taken out of it.
 *
 * A snapshot read at eleven last night and restored at two this morning
 * holds a step count, a burn, an hourly climb and a day's meals that were
 * all true when they were taken and are none of them today's. Drawing them
 * would put yesterday's eleven thousand steps on a widget headed with
 * today's date -- the one mistake a figure on a home screen cannot make,
 * because nobody checks a number that looks right.
 *
 * What is left is every series counted by day, and those stay true: the
 * last entry in `steps` was today's partial count when it was written and
 * is yesterday's whole one now, filed under the day it belongs to. Nights
 * stay for the same reason, and `lastNight` already refuses to call a
 * night last night unless it ended this morning.
 *
 * Dropped rather than left to the faces because `stepsToday` is a Long?
 * and `null` is the only way this file has of saying "not read", which is
 * exactly what it is: nobody has counted today yet.
 */
internal fun HealthSnapshot.withoutToday(): HealthSnapshot = copy(
    stepsToday = null,
    burnedToday = null,
    eatenToday = null,
    proteinToday = null,
    carbsToday = null,
    fatToday = null,
    burnedHourly = emptyList(),
    stepsHourly = emptyList(),
    heartToday = emptyList(),
    food = emptyList(),
)

/**
 * Where the kept read lives.
 *
 * A file of its own rather than `air_health` beside the widget rows, and
 * that is the one place this layer departs from "everything in one
 * preferences file". SharedPreferences rewrites the whole file on every
 * commit: a dozen kilobytes of readings sitting next to the tile layout
 * would rewrite the readings every time somebody recoloured a tile, and
 * rewrite the tile layout every half hour for ever.
 */
internal object WidgetSnapshotStore {
    private const val PREFS = "air_widget_cache"
    private const val KEY = "kept"

    fun read(context: Context, today: Long = LocalDate.now().toEpochDay()): KeptSnapshot? =
        runCatching { decodeKept(prefs(context).getString(KEY, null), today) }.getOrNull()

    /**
     * Keeps a read that has something in it, and throws away one that does
     * not.
     *
     * Only a granted read is kept, so a restored snapshot is always a
     * granted one -- which is what lets the stage go unstored, since
     * `healthDataOf` hands back a granted one. The other half matters
     * more: a read that comes back ungranted is Health Connect
     * saying the permission is gone, and the right answer to that is an
     * empty store rather than last week's figures drawn for ever by a
     * phone whose owner revoked them.
     */
    fun write(context: Context, snapshot: WidgetSnapshot) {
        if (!snapshot.granted) {
            clear(context)
            return
        }
        runCatching {
            val kept = KeptSnapshot(snapshot.health.snapshot, snapshot.readAt, snapshot.days)
            prefs(context).edit().putString(KEY, encodeKept(kept)).apply()
        }
    }

    fun clear(context: Context) {
        runCatching { prefs(context).edit().remove(KEY).apply() }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * The last read, built back into something a face can draw, without going
 * anywhere near Health Connect.
 *
 * [readWidgetSnapshot] with its one expensive line taken out. Everything
 * except the Health Connect half already lives in preferences and is read
 * live here rather than kept: the band's battery and its age, the goals,
 * the tile colours, the fitness profile. Keeping copies of those would be
 * a second answer to questions the app has already answered, and the first
 * thing to go wrong would be a widget drawing a step goal somebody changed
 * yesterday.
 *
 * Readiness is worked out rather than kept for the same reason -- it is
 * arithmetic over the snapshot plus `ReadinessStore`, which is itself a
 * preferences row, and its own rules already say what a figure dated
 * yesterday means.
 *
 * Nor are the trends kept. `HealthData` is a `HealthSnapshot` plus eleven
 * figures derived from it, so what is restored is run through the tab's
 * own [healthDataOf] -- the same reduction `loadHealthData` runs on a live
 * read. Keeping the derived half would be a second copy of every number on
 * the home screen, waiting for somebody to change how a trend is counted
 * and not know it existed; a second *copy of the arithmetic* here would be
 * the same bug one layer up. A restored widget and a live tile therefore
 * cannot disagree about a usual band.
 */
internal fun restoreWidgetSnapshot(context: Context): WidgetSnapshot? {
    val kept = WidgetSnapshotStore.read(context) ?: return null
    return WidgetSnapshot(
        health = healthDataOf(kept.health),
        band = runCatching { BandStore.read(context) }.getOrDefault(BandFacts()),
        goals = runCatching { HealthGoals.read(context) }.getOrDefault(Goals()),
        tiles = runCatching { HealthSettings.tiles(context) }.getOrDefault(emptyList()),
        profile = runCatching { FitnessProfileStore.read(context) }.getOrDefault(FitnessProfile()),
        readiness = runCatching { readinessToday(context, kept.health) }.getOrNull(),
        showTrends = runCatching { HealthSettings.showTrends(context) }.getOrDefault(true),
        days = kept.days,
        // The real one. A restored snapshot that claimed to be new would
        // be told to read again at once, which is the whole thing this is
        // here to avoid -- and every age-aware figure on it would lie.
        readAt = kept.readAt,
    )
}
