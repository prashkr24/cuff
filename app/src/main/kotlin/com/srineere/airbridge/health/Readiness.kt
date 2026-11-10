package com.srineere.airbridge.health

import android.content.Context
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One of the four things readiness stands on.
 *
 * Never the total without its parts. That is the rule the sleep score
 * already keeps, and it is the only thing that makes a composite
 * defensible: a reader who disagrees with the number can see which row
 * caused it. A part with nothing behind it says so and is left out of the
 * total rather than scoring zero.
 */
data class ReadinessPart(
    val name: String,
    /** What it is measured against, when that is not obvious. */
    val note: String?,
    val value: Double?,
    val unit: String,
    val decimals: Int,
    /** The person's own usual range for this part. */
    val usual: Band?,
    /** Points it moved the total, signed. Zero when it is missing. */
    val push: Double,
    /** How much of the total it is allowed to move, before anything is read. */
    val weight: Double,
) {
    val present: Boolean get() = value != null && usual != null
}

/**
 * Where this morning sits against your own mornings, as a figure.
 *
 * Fifty is "like you usually are", not "like an average person". There is
 * no population anywhere in this number, which is both its whole value and
 * the reason it is withheld until there are enough nights to have a usual.
 *
 * [score] is null while calibrating. Sixty nights is sixty nights: before
 * that the band is noise, and a confident 68 built on nine of them is
 * exactly the clinical claim the rest of this app refuses to make. The
 * parts are shown throughout, because they are measurements either way.
 */
data class Readiness(
    val score: Int?,
    /** The readiness series, for the band and the chart. */
    val trend: Trend?,
    val parts: List<ReadinessPart>,
    val days: Int,
    /**
     * The morning this figure is about, which is not always today.
     *
     * HRV arrives when the band syncs. Before that happens the newest
     * figure there is belongs to yesterday, and a screen headed "this
     * morning" showing it would be quietly wrong.
     */
    val day: Long? = null,
) {
    val calibrating: Boolean get() = score == null
    val standing: List<ReadinessPart> get() = parts.filter { it.present }

    /** Where this morning sits, in the words the rest of the tab uses. */
    val word: String
        get() {
            val trend = trend ?: return "calibrating"
            return when {
                trend.latest in trend.usual -> "like your usual"
                trend.latest > trend.usual.high -> "above your usual"
                else -> "below your usual"
            }
        }

    companion object {
        /**
         * Nights before a figure is shown.
         *
         * Two months. A band needs enough readings that one bad week cannot
         * define it, and sixty is where the usual range of these four stops
         * moving much when another night arrives.
         */
        const val NEEDED = 60

        /**
         * How far one standard deviation moves the figure.
         *
         * Fifteen points, so a day a full deviation above your baseline
         * reads 65 and one below reads 35. Picked so the ordinary range of
         * a person's own mornings fills the middle of the scale rather than
         * the ends: a readiness that spends all year between 48 and 52 is
         * a readiness nobody can use.
         */
        const val POINTS_PER_SD = 15.0

        /**
         * How far back the screen asks the tab to read.
         *
         * [NEEDED] counts readings, not days, and the tab reads thirty days
         * by default -- so a screen that did not widen the window could
         * never get past thirty of sixty however long somebody had been
         * wearing the band. Twice the gate leaves room for the nights HRV
         * does not land, without reading a year to draw one figure.
         */
        const val WINDOW_DAYS = 120
    }
}

/**
 * Readiness for every day there is enough to compute one.
 *
 * Each day is scored against the baseline of the whole window rather than
 * against a trailing one. Slightly circular, and deliberately: a trailing
 * baseline makes every day's figure depend on how much history happened to
 * be loaded, so the same morning would read differently on a tab showing
 * thirty days and one showing ninety.
 */
internal fun readinessSeries(snapshot: HealthSnapshot): List<Reading> {
    val hrv = Baseline.of(snapshot.hrv) ?: return emptyList()
    val resting = Baseline.of(snapshot.restingHeartRate)
    val sleep = Baseline.of(snapshot.nights.map { Reading(it.day, it.asleep.toMinutes().toDouble()) })
    val load = loadRatios(snapshot.steps)
    val loadBase = Baseline.of(load)

    val days = (snapshot.hrv.map { it.day } + snapshot.restingHeartRate.map { it.day })
        .distinct().sorted()
    val hrvByDay = snapshot.hrv.associate { it.day to it.value }
    val restingByDay = snapshot.restingHeartRate.associate { it.day to it.value }
    val sleepByDay = snapshot.nights.associate { it.day to it.asleep.toMinutes().toDouble() }
    val loadByDay = load.associate { it.day to it.value }

    return days.mapNotNull { day ->
        val pushes = mutableListOf<Pair<Double, Double>>()
        hrvByDay[day]?.let { pushes += WEIGHT_HRV to hrv.z(it) }
        restingByDay[day]?.let { value ->
            // Lower is better, so the sign is turned over.
            resting?.let { pushes += WEIGHT_RESTING to -it.z(value) }
        }
        sleepByDay[day]?.let { value -> sleep?.let { pushes += WEIGHT_SLEEP to it.z(value) } }
        loadByDay[day]?.let { value ->
            // A ratio far from one in either direction is the tiring thing:
            // a week far above the month is a hard week, and a week far
            // below it is a week off. Only the first costs readiness.
            loadBase?.let { pushes += WEIGHT_LOAD to -maxOf(it.z(value), 0.0) }
        }
        if (pushes.isEmpty()) return@mapNotNull null
        // Rescaled by the weight actually present, so a day missing its
        // sleep is not a day scored as though sleep were zero.
        val weight = pushes.sumOf { it.first }
        val z = pushes.sumOf { it.first * it.second } / weight
        Reading(day, (50.0 + Readiness.POINTS_PER_SD * z).coerceIn(0.0, 100.0))
    }
}

/** This morning's readiness, and the four rows it stands on. */
fun readinessOf(snapshot: HealthSnapshot): Readiness {
    val series = readinessSeries(snapshot)
    val trend = trendOf(series)
    val hrv = Baseline.of(snapshot.hrv)
    val resting = Baseline.of(snapshot.restingHeartRate)
    val sleepReadings = snapshot.nights.map { Reading(it.day, it.asleep.toMinutes().toDouble()) }
    val sleep = Baseline.of(sleepReadings)
    val load = loadRatios(snapshot.steps)
    val loadBase = Baseline.of(load)

    val parts = listOf(
        part("HRV", "weighted most", snapshot.hrv.lastOrNull()?.value, "ms", 0, hrv, WEIGHT_HRV, 1),
        part(
            "Resting HR", null, snapshot.restingHeartRate.lastOrNull()?.value, "bpm", 0,
            resting, WEIGHT_RESTING, -1,
        ),
        part(
            "Last night", null, sleepReadings.maxByOrNull { it.day }?.value, "min", 0,
            sleep, WEIGHT_SLEEP, 1,
        ),
        part(
            "Recent load", "7 against 28 days", load.lastOrNull()?.value, "", 2,
            loadBase, WEIGHT_LOAD, -1,
        ),
    )
    return Readiness(
        // Withheld, not guessed. The parts above it are shown either way.
        score = if (series.size >= Readiness.NEEDED) {
            series.last().value.roundToInt()
        } else {
            null
        },
        trend = trend,
        parts = parts,
        days = series.size,
        day = series.lastOrNull()?.day,
    )
}

private fun part(
    name: String,
    note: String?,
    value: Double?,
    unit: String,
    decimals: Int,
    baseline: Baseline?,
    weight: Double,
    way: Int,
): ReadinessPart = ReadinessPart(
    name = name,
    note = note,
    value = value,
    unit = unit,
    decimals = decimals,
    usual = baseline?.usual,
    push = if (value == null || baseline == null) 0.0
    else way * baseline.z(value) * weight * Readiness.POINTS_PER_SD,
    weight = weight,
)

/**
 * Seven days of steps against twenty-eight, which is the acute-to-chronic
 * ratio athletics uses to spot a week that got ahead of itself.
 *
 * Steps rather than workout minutes because the snapshot carries a month of
 * steps and only a week of sessions: a chronic load you cannot see four
 * weeks of is not a chronic load.
 */
private fun loadRatios(steps: List<Reading>): List<Reading> {
    if (steps.size < 28) return emptyList()
    val byDay = steps.associate { it.day to it.value }
    return steps.sortedBy { it.day }.mapNotNull { reading ->
        // Both windows end the day before, never on the day being scored.
        //
        // Today's steps grow all day, so including them made this morning's
        // readiness climb while you walked -- a figure about how you woke
        // up, moving because of what you did after. Asked again at six it
        // gave a different answer to the one the morning report had already
        // sent. Ending at yesterday makes the day's figure settle when the
        // night does and stay settled.
        val acute = (1..7).mapNotNull { byDay[reading.day - it] }
        val chronic = (1..28).mapNotNull { byDay[reading.day - it] }
        if (acute.size < 5 || chronic.size < 20) return@mapNotNull null
        val base = chronic.average().takeIf { it > 0 } ?: return@mapNotNull null
        Reading(reading.day, acute.average() / base)
    }
}

/**
 * The morning's figure, worked out once and then remembered.
 *
 * Readiness is a statement about one morning, so it should be the same
 * number at breakfast and at bedtime. The arithmetic no longer drifts --
 * the load window ends at yesterday — but it still reads whatever Health
 * Connect holds at the moment it is asked, and a reading that is corrected
 * or arrives late would quietly move a figure somebody has already seen
 * and had sent to them in the morning report.
 *
 * What is kept is the whole series rather than the single figure, and that
 * is the change that made the screen quick. The figure alone was enough to
 * stop it drifting and no use at all for drawing it: a screen still had to
 * read a hundred and twenty days of Health Connect and do every day's
 * arithmetic before it could find out that the answer was already known.
 * That read is the several seconds somebody spent looking at "calibrating"
 * each time they opened Readiness, on a morning whose figure had been
 * settled since six.
 *
 * Only a figure that exists is kept. Asked before HRV has synced there is
 * nothing to remember, so nothing is written and the real figure is picked
 * up whenever it turns up. Keyed by the morning it describes rather than
 * by today, which is the same thing on most days and the honest answer on
 * the rest.
 *
 * Everything but the series is worked out fresh: the four parts under the
 * figure are measurements, and a measurement that has been corrected
 * should say so.
 */
fun readinessToday(context: Context, snapshot: HealthSnapshot): Readiness {
    val today = LocalDate.now().toEpochDay()
    val worked = readinessOf(snapshot)
    val fresh = worked.asKept()
    // A store that cannot be read is not a reason to show nothing: the
    // figure can always be worked out again, which is what this is for.
    val kept = runCatching { ReadinessStore.read(context, today) }.getOrNull()
    if (fresh != null && supersedes(fresh, kept, today)) {
        runCatching { ReadinessStore.write(context, fresh) }
        return worked
    }
    return kept?.let { worked.restoredFrom(it) } ?: worked
}

/**
 * Works this morning's figure out in the background, once the readings it
 * stands on are there, and says whether anything is still to come.
 *
 * Health Connect fills up whenever the Fitbit app syncs, which this app
 * neither controls nor can ask for. So this is written to be *called
 * again* rather than to be called at the right moment: it looks, settles
 * the morning if it can, and tells the caller whether to come back.
 *
 * [snapshot] is the cheap thirty-day read the caller already has, and the
 * cost control is that it is enough to answer "has anything arrived yet".
 * The hundred and twenty days the figure is counted against are read here
 * and only here, on the pass that can actually finish the job -- so a
 * morning the band never syncs costs the short read it was already
 * costing, and the long one is paid once.
 */
internal suspend fun settleReadiness(
    context: Context,
    snapshot: HealthSnapshot,
    today: Long = LocalDate.now().toEpochDay(),
    reader: HealthReader = HealthReader(),
): Boolean {
    // Until both halves are written, whatever could be worked out now is
    // provisional, and keeping it would be keeping the wrong answer: a
    // figure that sleep had no say in is a figure three quarters made.
    if (!readinessInputsLanded(snapshot, today)) return false
    val kept = runCatching { ReadinessStore.read(context, today) }.getOrNull()
    // Already worked out this morning, on everything this morning has to
    // stand on. Counted from the short read rather than remembered as a
    // flag, because the question is not "did it run" but "could it do any
    // better now" -- and somebody whose phone has never written a step has
    // no recent load part to wait for and should not be waited on for one.
    val available = readinessOf(snapshot).standing.size
    if (kept != null && kept.day == today && kept.standing >= available) return true

    val wide = runCatching { reader.read(context, Readiness.WINDOW_DAYS) }.getOrNull()
        ?: return false
    // Through the same door every screen uses, so there is one place a
    // figure is worked out and one place it is kept.
    return readinessToday(context, wide).day == today
}

/**
 * Whether this morning's readings have landed: the sleep, and the vitals.
 *
 * The owner's own two, in their own order. HRV stands for the vitals
 * because it is the one of the four readiness cannot be computed at all
 * without, and it arrives on the same sync as the resting rate; the night
 * stands for itself. Both are asked of **today**, because last night is
 * the night that ended this morning and Health Connect files it under the
 * day it ended.
 */
internal fun readinessInputsLanded(snapshot: HealthSnapshot, today: Long): Boolean =
    snapshot.hrv.any { it.day == today } && snapshot.nights.any { it.day == today }

/**
 * One morning's readiness, as the series it was worked out from.
 *
 * The series rather than the figure, because the figure is only the last
 * point of it: the band the screen draws, the rail the figure sits on and
 * the count of nights behind it are the rest, and each of them costs the
 * same hundred and twenty days to rebuild.
 */
data class KeptReadiness(
    /** The morning it is about, which is not always today. */
    val day: Long,
    /** Readiness a day at a time, oldest first. */
    val series: List<Reading>,
    /**
     * How many of the four parts stood behind it when it was worked out.
     *
     * The one thing that makes a kept figure *wrong* rather than merely
     * old, and so the thing it has to be invalidated on. HRV lands when
     * the band syncs and the night is often a moment behind it, so a pass
     * at twenty past six can produce a real figure for today that sleep
     * had no say in -- and keyed by the day alone, that figure would be
     * served until midnight.
     */
    val standing: Int,
) {
    /**
     * Derived rather than stored. The figure is the newest point of the
     * series by the same rule [readinessOf] uses, and a second field
     * holding it would be a second answer waiting to disagree.
     */
    val score: Int?
        get() = if (series.size >= Readiness.NEEDED) {
            series.maxByOrNull { it.day }?.value?.roundToInt()
        } else {
            null
        }
}

/** This figure, in the shape the store keeps, or null if there is nothing to keep. */
internal fun Readiness.asKept(): KeptReadiness? {
    val day = day ?: return null
    // The series is the trend's readings: below three there is no trend,
    // and two points are not a thing worth remembering.
    val series = trend?.readings?.takeIf { it.isNotEmpty() } ?: return null
    return KeptReadiness(day, series, standing.size)
}

/**
 * The kept figure, drawn over this morning's parts.
 *
 * Everything the long read decided -- the figure, the band it sits in, the
 * nights behind it -- comes from what was kept. The four rows underneath
 * are whatever has just been read, which is the rule this file already
 * followed when only the score was remembered.
 */
internal fun Readiness.restoredFrom(kept: KeptReadiness): Readiness = copy(
    score = kept.score,
    trend = trendOf(kept.series),
    days = kept.series.size,
    day = kept.day,
)

/**
 * Whether a kept morning is still one that this morning could be about.
 *
 * Today or yesterday, and nothing else. A figure dated today is the one
 * being waited for; a figure dated yesterday is the honest answer until
 * the band syncs, which is the same pair of silences `nightPending` tells
 * apart. Older than that and it is not late, it is somebody else's
 * morning -- and showing it for the second before the real one lands is
 * how a screen comes to print a number nobody can place.
 *
 * A day in the future is not current either. It cannot be a morning that
 * has happened, and a phone carried west produces one.
 */
internal fun isCurrent(day: Long, today: Long): Boolean = day == today || day == today - 1

/**
 * Whether a figure just worked out should replace the one being kept.
 *
 * Three rules, and each of them exists because something otherwise serves
 * the wrong number for a day:
 *
 * - A morning older than yesterday is not kept at all. See [isCurrent].
 * - A **later** morning wins, but not by being worse. The widgets read
 *   thirty days unless a readiness card is on the home screen, and a
 *   thirty-day read is bound to come back calibrating: without the second
 *   half of this rule, the half-hourly widget turn would replace this
 *   morning's real figure with no figure, and the screen would go blank
 *   until something widened again.
 * - The **same** morning is replaced only by a better-founded figure: one
 *   standing on more of its four parts, which is what a late night
 *   changes, or built from more readings, which is what the long read
 *   buys. A part that has gone away leaves the kept figure alone; it is
 *   still the best answer anything had about that morning.
 */
internal fun supersedes(fresh: KeptReadiness?, kept: KeptReadiness?, today: Long): Boolean {
    if (fresh == null || !isCurrent(fresh.day, today)) return false
    if (kept == null) return true
    if (fresh.day != kept.day) {
        return fresh.day > kept.day && (fresh.score != null || kept.score == null)
    }
    return fresh.standing > kept.standing || fresh.series.size > kept.series.size
}

/**
 * The series as one line of text, because this is a preferences file and
 * not a database.
 *
 * A hundred and twenty days comes to about a kilobyte, which is nothing
 * beside the tile layout already in here. Written to one decimal: the
 * figure is drawn whole and its band to the nearest point, so anything
 * finer is precision nobody can see. [Locale.ROOT], because half of Europe
 * would otherwise write "48,3" and read back nothing.
 */
internal fun encodeSeries(series: List<Reading>): String =
    series.joinToString(";") { "${it.day}:${"%.1f".format(Locale.ROOT, it.value)}" }

/**
 * Fail closed: a line that does not parse decodes to nothing at all, never
 * to the half of it that did. A short series is not a corrupt series -- it
 * is a person with fewer nights -- so a silently truncated one would read
 * as somebody still calibrating rather than as a store to throw away.
 */
internal fun decodeSeries(stored: String): List<Reading> {
    if (stored.isEmpty()) return emptyList()
    return stored.split(";").map { part ->
        val bits = part.split(":")
        val day = bits.getOrNull(0)?.toLongOrNull()
        val value = bits.getOrNull(1)?.toDoubleOrNull()
        if (bits.size != 2 || day == null || value == null) return emptyList()
        Reading(day, value)
    }
}

/** One morning's figure, in the preferences everything else on this tab uses. */
object ReadinessStore {
    private const val PREFS = "air_health"
    private const val KEY_DAY = "readiness_day"
    private const val KEY_SERIES = "readiness_series"
    private const val KEY_STANDING = "readiness_standing"

    /**
     * Written by the version that kept the figure alone, and read by
     * nothing now. Removed on the next write rather than migrated: it is
     * one morning's arithmetic, and a migration for it would be more code
     * than the thing it saves.
     */
    private const val KEY_SCORE = "readiness_score"

    /**
     * What is kept, when it is still about a morning worth showing.
     *
     * Null rather than an old entry, so every caller gets the same answer
     * to "is there a figure for this morning" without each of them having
     * to know what a morning is.
     */
    fun read(context: Context, today: Long = LocalDate.now().toEpochDay()): KeptReadiness? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = p.getLong(KEY_DAY, Long.MIN_VALUE)
        if (!isCurrent(day, today)) return null
        val series = decodeSeries(p.getString(KEY_SERIES, null) ?: return null)
        if (series.isEmpty()) return null
        return KeptReadiness(day, series, p.getInt(KEY_STANDING, 0))
    }

    /** One morning at a time: yesterday's is replaced, not kept beside it. */
    fun write(context: Context, kept: KeptReadiness) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DAY, kept.day)
            .putString(KEY_SERIES, encodeSeries(kept.series))
            .putInt(KEY_STANDING, kept.standing)
            .remove(KEY_SCORE)
            .apply()
    }
}

/** A mean and a spread, which is all a z-score needs. */
private class Baseline(val mean: Double, val deviation: Double, val usual: Band) {
    fun z(value: Double): Double = if (deviation <= 0.0) 0.0 else (value - mean) / deviation

    companion object {
        fun of(readings: List<Reading>): Baseline? {
            if (readings.size < 3) return null
            val values = readings.map { it.value }
            val mean = values.average()
            val deviation = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            // Half a deviation either side: the same smallest worthwhile
            // change every other band on this tab is drawn from.
            val half = deviation / 2
            return Baseline(mean, deviation, Band(mean - half, mean + half))
        }
    }
}

// HRV carries most of it, which is what the published method does: it is
// the one of the four that moves first when something is wrong. Load is a
// nudge rather than a verdict -- a hard week is a choice, not an illness.
private const val WEIGHT_HRV = 0.40
private const val WEIGHT_RESTING = 0.25
private const val WEIGHT_SLEEP = 0.25
private const val WEIGHT_LOAD = 0.10
