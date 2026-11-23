package com.srineere.airbridge.health

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The last read of Health Connect, kept so the next cold start has
 * something to draw.
 *
 * Nothing on this tab survived process death. Every figure the app shows
 * is derived from one [HealthSnapshot], that snapshot lived in a field on
 * `HealthState`, and the field died with the process -- so opening the app
 * meant a spinner, then twenty Health Connect queries, then the same
 * numbers that were on screen an hour earlier. The readings had not
 * changed; only our memory of them had.
 *
 * What is kept is the **snapshot**, not the reduced `HealthData`. Trends,
 * bands, balances and scores are all worked out from a snapshot by one
 * function, and keeping the reduction as well would be keeping a second
 * copy of every figure -- one that could disagree with the first the day
 * somebody changes how a trend is counted. Restoring runs the same
 * reduction the live read runs, so a restored frame and the frame that
 * replaces it a second later differ in freshness and in nothing else.
 *
 * Follows [ReadinessStore] in shape and in reasoning: one entry, in
 * `air_health`, keyed by the day it was read, fail-closed on anything it
 * cannot parse, and served only where it is still true. Readiness keeps
 * its own entry rather than riding on this one, because that figure is a
 * statement about one morning and must not move when a later read
 * happens to be smaller.
 */
data class KeptHealth(
    /**
     * When the read happened, in epoch millis.
     *
     * Kept as a record and deliberately not used to decide anything. It is
     * tempting to let a cache written a minute ago satisfy the freshness
     * window and skip the read -- and that is exactly the case a cold
     * start is, since the process usually dies seconds after the last
     * refresh. A restored reading is *drawn*, not *fresh*. What the screen
     * says about age comes from `writtenAt`, which answers the question
     * anybody actually has: how old is this reading, not how long since we
     * asked for it.
     */
    val readAt: Long,
    /** The local day it happened on, which is what the day-only figures belong to. */
    val day: Long,
    /** How many days the kept window covers, for `HealthState.loadedDays`. */
    val days: Int,
    val snapshot: HealthSnapshot,
)

/**
 * The format number, checked on the way in.
 *
 * Bumped whenever a field is added, removed or re-encoded. A mismatch
 * throws the entry away rather than reading what it can of it: half a
 * snapshot drawn as a whole one is the one failure mode this cache must
 * not have, because every screen on the tab treats an absent reading as
 * "nothing was written" and says so in those words.
 */
internal const val CACHE_VERSION = 2

/**
 * One read's worth of state, in the preferences file the rest of the tab
 * already uses.
 */
object HealthCache {
    private const val PREFS = "air_health"
    private const val KEY = "health_snapshot"

    /**
     * How far back the cache keeps, whatever the read covered.
     *
     * The tab's own window, deliberately. Two screens widen to a hundred
     * and twenty days and keeping that would quadruple the entry for a
     * figure Readiness already keeps for itself; narrower than thirty and
     * the restored frame would compute every trend and usual band over
     * fewer readings than the live read does, so the numbers would visibly
     * change when the read landed. A band over seven nights and the same
     * band over thirty are two different claims, and showing one and then
     * the other is worse than showing the second a moment later.
     *
     * A month comes to roughly forty kilobytes of JSON on a phone that
     * records sleep stages and logs food -- the hypnograms and the food
     * entries are most of it, the twelve daily series about five. Large
     * beside the tile layout in the same file, small beside one frame of
     * the chart it saves you waiting for.
     */
    const val DAYS = 30

    /**
     * What was kept, with the day-only figures dropped if the day has
     * turned. Null when there is nothing, or nothing legible.
     */
    fun read(context: Context, today: Long = LocalDate.now().toEpochDay()): KeptHealth? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        val kept = decodeHealth(stored) ?: return null
        return kept.copy(snapshot = asOf(kept.snapshot, kept.day, today))
    }

    /**
     * Keeps this read, trimmed to [DAYS] and without the beat samples.
     *
     * [days] is the window the read actually covered; what is recorded is
     * the smaller of that and [DAYS], because that is what the entry can
     * honestly claim to hold.
     */
    fun write(
        context: Context,
        snapshot: HealthSnapshot,
        days: Int,
        readAt: Long = System.currentTimeMillis(),
        today: Long = LocalDate.now().toEpochDay(),
    ) {
        val kept = KeptHealth(
            readAt = readAt,
            day = today,
            days = minOf(days, DAYS),
            snapshot = keepable(snapshot, today - DAYS + 1),
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, encodeHealth(kept))
            .apply()
    }

    /**
     * Throws the entry away.
     *
     * For the moment the health half is switched off. A month of
     * somebody's nights has no business sitting in a preferences file for
     * a feature they have just turned off, and this is a cache rather
     * than a record -- the cost of being wrong about that is one read.
     *
     * Nothing written is touched by this, here or anywhere else. The food
     * log lives in Health Connect and the entries are the person's, not
     * ours to withdraw.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

/**
 * What of a read is worth keeping, and what cannot be.
 *
 * Everything day-keyed is cut to the window at [from]. Sessions are left
 * alone because the reader only ever asks for a week of them, so they are
 * already inside any window this keeps.
 *
 * **The beat samples are dropped**, and they are the only thing this
 * leaves out. Two reasons, and either on its own would be enough. They
 * are unbounded -- a day of continuous heart rate is tens of thousands of
 * readings, which is the whole of this entry several times over, in a
 * preferences file that is read into memory in one piece. And they are
 * the one part of a snapshot that is about *now* rather than about a day:
 * a cache restored four hours later would be handing a screen headed
 * "Today" a chart that stops at breakfast. The two screens that draw them
 * say they are reading rather than that nothing was recorded.
 */
internal fun keepable(snapshot: HealthSnapshot, from: Long): HealthSnapshot {
    val food = snapshot.food.filter { it.day >= from }
    return snapshot.copy(
        steps = snapshot.steps.filter { it.day >= from },
        restingHeartRate = snapshot.restingHeartRate.filter { it.day >= from },
        hrv = snapshot.hrv.filter { it.day >= from },
        respiratoryRate = snapshot.respiratoryRate.filter { it.day >= from },
        skinTemperature = snapshot.skinTemperature.filter { it.day >= from },
        vo2Max = snapshot.vo2Max.filter { it.day >= from },
        weight = snapshot.weight.filter { it.day >= from },
        burnedDaily = snapshot.burnedDaily.filter { it.day >= from },
        eatenDaily = snapshot.eatenDaily.filter { it.day >= from },
        proteinDaily = snapshot.proteinDaily.filter { it.day >= from },
        carbsDaily = snapshot.carbsDaily.filter { it.day >= from },
        fatDaily = snapshot.fatDaily.filter { it.day >= from },
        nights = snapshot.nights.filter { it.day >= from },
        food = food,
        mealsDaily = mealsOf(food),
        heartToday = emptyList(),
        heartRecent = emptyList(),
    )
}

/**
 * How many separate entries each day holds.
 *
 * Not stored, because it is the food list counted and nothing else --
 * `HealthReader` builds it from the same list in the same breath. A
 * stored copy would be a count that can disagree with the rows it counts,
 * which is the bug it would be kept to prevent.
 */
private fun mealsOf(food: List<FoodEntry>): Map<Long, Int> =
    food.groupingBy { it.day }.eachCount()

/**
 * A kept snapshot as it stands on [today].
 *
 * Everything in a snapshot is keyed by the day it belongs to except the
 * handful of figures that mean "so far today": the step count, the two
 * running calorie totals, the three macros and the two hourly series.
 * Those are true of the day they were read on and of no other day, so
 * once the day has turned they are dropped rather than redrawn -- a cache
 * restored at two in the morning would otherwise open the tab on
 * yesterday's nine thousand steps under today's date, which is the kind
 * of wrong that looks right.
 *
 * The day-keyed half is kept, because it is still true: thirty days of
 * nights and readings ending yesterday are thirty real days, and the
 * header's age is taken from `writtenAt`, an instant, so it goes on
 * telling the truth about how old they are without this having to help.
 */
internal fun asOf(snapshot: HealthSnapshot, kept: Long, today: Long): HealthSnapshot =
    if (kept == today) {
        snapshot
    } else {
        snapshot.copy(
            stepsToday = null,
            burnedToday = null,
            eatenToday = null,
            proteinToday = null,
            carbsToday = null,
            fatToday = null,
            burnedHourly = emptyList(),
            stepsHourly = emptyList(),
        )
    }

// ------------------------------------------------------------------ writing

/**
 * The whole entry as one line of JSON.
 *
 * The series inside it are *not* JSON. A day and a value as an object
 * costs about thirty characters where `"20405:8213.0"` costs twelve, and
 * there are several hundred of them; the structure buys nothing, because
 * every one of these lists is the same two fields in the same order.
 * `Double.toString` writes a full stop wherever the phone is and round
 * trips exactly, which is the whole requirement -- unlike
 * [encodeSeries], which rounds to a decimal because readiness is drawn
 * whole. A skin temperature delta of -0.23 cannot afford that.
 */
internal fun encodeHealth(kept: KeptHealth): String {
    val snapshot = kept.snapshot
    val json = JSONObject()
        .put("version", CACHE_VERSION)
        .put("readAt", kept.readAt)
        .put("day", kept.day)
        .put("days", kept.days)
        .put("steps", encodeReadings(snapshot.steps))
        .put("resting", encodeReadings(snapshot.restingHeartRate))
        .put("hrv", encodeReadings(snapshot.hrv))
        .put("breathing", encodeReadings(snapshot.respiratoryRate))
        .put("skin", encodeReadings(snapshot.skinTemperature))
        .put("vo2", encodeReadings(snapshot.vo2Max))
        .put("weight", encodeReadings(snapshot.weight))
        .put("burnedDaily", encodeReadings(snapshot.burnedDaily))
        .put("eatenDaily", encodeReadings(snapshot.eatenDaily))
        .put("proteinDaily", encodeReadings(snapshot.proteinDaily))
        .put("carbsDaily", encodeReadings(snapshot.carbsDaily))
        .put("fatDaily", encodeReadings(snapshot.fatDaily))
        .put("burnedHourly", encodeHours(snapshot.burnedHourly))
        .put("stepsHourly", encodeHours(snapshot.stepsHourly))
        .put("nights", JSONArray(snapshot.nights.map(::nightJson)))
        .put("sessions", JSONArray(snapshot.sessions.map(::sessionJson)))
        .put("food", JSONArray(snapshot.food.map(::foodJson)))
        .put("missing", JSONArray(snapshot.missingPermissions.toList()))
    snapshot.stepsToday?.let { json.put("stepsToday", it) }
    snapshot.burnedToday?.let { json.put("burnedToday", it) }
    snapshot.eatenToday?.let { json.put("eatenToday", it) }
    snapshot.proteinToday?.let { json.put("proteinToday", it) }
    snapshot.carbsToday?.let { json.put("carbsToday", it) }
    snapshot.fatToday?.let { json.put("fatToday", it) }
    snapshot.writtenAt?.let { json.put("writtenAt", it.toEpochMilli()) }
    return json.toString()
}

/**
 * The entry, or nothing at all.
 *
 * Fail closed, in the sense invariant 3 of the handover means it: a line
 * that does not parse, an entry written by a later version, a night whose
 * stages are in a shape this build does not recognise -- each of them
 * ends here as null, and the tab opens the way it did before this file
 * existed. There is no partial decode anywhere in it, because a snapshot
 * missing half its nights is indistinguishable, on every screen that
 * draws one, from a person who did not sleep.
 */
internal fun decodeHealth(stored: String): KeptHealth? {
    // A block body, so that every `return null` below is a plain return
    // out of this function through `runCatching`'s inline lambda. Half the
    // ways this entry can be wrong are not exceptions -- a series that
    // does not parse, a stage name from a later version -- and they have
    // to leave by the same door as the ones that are.
    return runCatching {
        val json = JSONObject(stored)
        // A newer format is thrown away rather than guessed at. So is an
        // older one: this is a cache, and rebuilding it costs one read.
        if (json.getInt("version") != CACHE_VERSION) return null
        val food = json.getJSONArray("food").objects().map { foodOf(it) }
        KeptHealth(
            readAt = json.getLong("readAt"),
            day = json.getLong("day"),
            days = json.getInt("days"),
            snapshot = HealthSnapshot(
                stepsToday = if (json.isNull("stepsToday")) null else json.getLong("stepsToday"),
                steps = decodeReadings(json.getString("steps")) ?: return null,
                restingHeartRate = decodeReadings(json.getString("resting")) ?: return null,
                hrv = decodeReadings(json.getString("hrv")) ?: return null,
                respiratoryRate = decodeReadings(json.getString("breathing")) ?: return null,
                skinTemperature = decodeReadings(json.getString("skin")) ?: return null,
                vo2Max = decodeReadings(json.getString("vo2")) ?: return null,
                nights = json.getJSONArray("nights").objects().map { nightOf(it) ?: return null },
                sessions = json.getJSONArray("sessions").objects().map { sessionOf(it) },
                burnedDaily = decodeReadings(json.getString("burnedDaily")) ?: return null,
                eatenDaily = decodeReadings(json.getString("eatenDaily")) ?: return null,
                weight = decodeReadings(json.getString("weight")) ?: return null,
                burnedHourly = decodeHours(json.getString("burnedHourly")) ?: return null,
                stepsHourly = decodeHours(json.getString("stepsHourly")) ?: return null,
                burnedToday = json.number("burnedToday"),
                eatenToday = json.number("eatenToday"),
                proteinDaily = decodeReadings(json.getString("proteinDaily")) ?: return null,
                carbsDaily = decodeReadings(json.getString("carbsDaily")) ?: return null,
                fatDaily = decodeReadings(json.getString("fatDaily")) ?: return null,
                mealsDaily = mealsOf(food),
                food = food,
                proteinToday = json.number("proteinToday"),
                carbsToday = json.number("carbsToday"),
                fatToday = json.number("fatToday"),
                writtenAt = if (json.isNull("writtenAt")) {
                    null
                } else {
                    Instant.ofEpochMilli(json.getLong("writtenAt"))
                },
                missingPermissions = json.getJSONArray("missing")
                    .let { array -> (0 until array.length()).map { array.getString(it) } }
                    .toSet(),
            ),
        )
    }.getOrNull()
}

// ------------------------------------------------------------ the two halves

/**
 * A series as `day:value`, separated by semicolons.
 *
 * Null from the decoder rather than a short list, for the reason
 * [decodeSeries] gives and this file needs even more: a truncated run of
 * steps is a fortnight somebody did not walk.
 */
internal fun encodeReadings(readings: List<Reading>): String =
    readings.joinToString(";") { "${it.day}:${it.value}" }

internal fun decodeReadings(stored: String): List<Reading>? {
    if (stored.isEmpty()) return emptyList()
    return stored.split(";").map { part ->
        val bits = part.split(":")
        if (bits.size != 2) return null
        val day = bits[0].toLongOrNull() ?: return null
        val value = bits[1].toDoubleOrNull() ?: return null
        Reading(day, value)
    }
}

/** Twenty-four figures, in order, for the two hourly series. */
internal fun encodeHours(hours: List<Double>): String = hours.joinToString(";")

internal fun decodeHours(stored: String): List<Double>? {
    if (stored.isEmpty()) return emptyList()
    return stored.split(";").map { it.toDoubleOrNull() ?: return null }
}

/**
 * A night's stages, as offsets inside the night rather than as instants.
 *
 * The hypnograms are the largest thing in this entry -- a Fitbit night is
 * thirty or forty segments and a month is a thousand of them -- and an
 * offset into a night is five or six digits where an absolute epoch
 * instant is thirteen. Nothing is lost: the night's own start is stored
 * beside them and every segment is inside it.
 *
 * The stage is written by name and not by ordinal, so reordering the enum
 * cannot silently turn deep sleep into light sleep. A name this build
 * does not know decodes to nothing at all, which throws the whole entry
 * away -- correct, because it means the file was written by a version
 * that knew a stage this one does not.
 */
internal fun encodeSegments(start: Instant, segments: List<SleepSegment>): String {
    val base = start.toEpochMilli()
    return segments.joinToString(";") {
        "${it.stage.name}:${it.start.toEpochMilli() - base}:${it.end.toEpochMilli() - base}"
    }
}

internal fun decodeSegments(stored: String, start: Instant): List<SleepSegment>? {
    if (stored.isEmpty()) return emptyList()
    return stored.split(";").map { part ->
        val bits = part.split(":")
        if (bits.size != 3) return null
        val stage = SleepStage.entries.firstOrNull { it.name == bits[0] } ?: return null
        val from = bits[1].toLongOrNull() ?: return null
        val until = bits[2].toLongOrNull() ?: return null
        SleepSegment(stage, start.plusMillis(from), start.plusMillis(until))
    }
}

private fun nightJson(night: Night): JSONObject = JSONObject()
    .put("day", night.day)
    .put("start", night.start.toEpochMilli())
    .put("end", night.end.toEpochMilli())
    // Milliseconds rather than seconds: these are sums of
    // `Duration.between` over instants Health Connect writes to the
    // millisecond, and rounding them would move a night's efficiency.
    .put("asleep", night.asleep.toMillis())
    .put("awake", night.awake.toMillis())
    .put("light", night.light.toMillis())
    .put("deep", night.deep.toMillis())
    .put("rem", night.rem.toMillis())
    // Two figures that cannot be recovered from the rest, now that a
    // day's sleeps are added together: time in bed is their sum and not
    // the span from start to end, and the middle is weighted by how much
    // sleep each of them was.
    .put("inBed", night.inBed.toMillis())
    .put("midpoint", night.midpoint.toEpochMilli())
    .put("stages", encodeSegments(night.start, night.segments))
    // The sleeps the day was added up from, each written exactly as a
    // night is. Empty for the ordinary single-sleep day, and one level
    // deep for any other: a part's own parts are always empty, so this
    // recursion terminates on the first step.
    .put("parts", JSONArray(night.parts.map(::nightJson)))

private fun nightOf(json: JSONObject): Night? {
    val start = Instant.ofEpochMilli(json.getLong("start"))
    return Night(
        day = json.getLong("day"),
        start = start,
        end = Instant.ofEpochMilli(json.getLong("end")),
        asleep = Duration.ofMillis(json.getLong("asleep")),
        awake = Duration.ofMillis(json.getLong("awake")),
        light = Duration.ofMillis(json.getLong("light")),
        deep = Duration.ofMillis(json.getLong("deep")),
        rem = Duration.ofMillis(json.getLong("rem")),
        segments = decodeSegments(json.getString("stages"), start) ?: return null,
        inBed = Duration.ofMillis(json.getLong("inBed")),
        midpoint = Instant.ofEpochMilli(json.getLong("midpoint")),
        parts = json.getJSONArray("parts").objects().map { nightOf(it) ?: return null },
    )
}

private fun sessionJson(session: Session): JSONObject {
    val json = JSONObject()
        .put("start", session.start.toEpochMilli())
        .put("end", session.end.toEpochMilli())
        .put("type", session.exerciseType)
    session.title?.let { json.put("title", it) }
    // Flattened rather than nested, because a device is three optional
    // fields and a sub-object for three optional fields is a sub-object
    // that is usually absent and sometimes empty.
    session.device?.let { device ->
        json.put("deviceType", device.type)
        device.manufacturer?.let { json.put("deviceMake", it) }
        device.model?.let { json.put("deviceModel", it) }
    }
    return json
}

private fun sessionOf(json: JSONObject): Session = Session(
    start = Instant.ofEpochMilli(json.getLong("start")),
    end = Instant.ofEpochMilli(json.getLong("end")),
    title = json.text("title"),
    exerciseType = json.getInt("type"),
    device = if (json.isNull("deviceType")) {
        null
    } else {
        RecordedBy(
            type = json.getInt("deviceType"),
            manufacturer = json.text("deviceMake"),
            model = json.text("deviceModel"),
        )
    },
)

private fun foodJson(entry: FoodEntry): JSONObject {
    val json = JSONObject()
        .put("id", entry.id)
        .put("at", entry.at.toEpochMilli())
        .put("day", entry.day)
        .put("meal", entry.mealType)
    entry.name?.let { json.put("name", it) }
    entry.kcal?.let { json.put("kcal", it) }
    entry.protein?.let { json.put("protein", it) }
    entry.carbs?.let { json.put("carbs", it) }
    entry.fat?.let { json.put("fat", it) }
    entry.source?.let { json.put("source", it) }
    return json
}

private fun foodOf(json: JSONObject): FoodEntry = FoodEntry(
    id = json.getString("id"),
    at = Instant.ofEpochMilli(json.getLong("at")),
    day = json.getLong("day"),
    name = json.text("name"),
    kcal = json.number("kcal"),
    protein = json.number("protein"),
    carbs = json.number("carbs"),
    fat = json.number("fat"),
    mealType = json.getInt("meal"),
    source = json.text("source"),
)

/** The objects in an array, which `org.json` will not hand over as a list. */
private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).map { getJSONObject(it) }

/**
 * A string, or null when the key is absent.
 *
 * An empty string reads back as null, and that is deliberate rather than
 * careless: a workout titled "" and a workout with no title are the same
 * thing to every screen that draws one, and collapsing them here means
 * nothing downstream has to know which it got.
 */
private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else getString(key).takeIf { it.isNotEmpty() }

/** A figure, or null when the key is absent. Absent is not zero. */
private fun JSONObject.number(key: String): Double? =
    if (isNull(key)) null else getDouble(key)
