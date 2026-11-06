package com.srineere.airbridge.health

import android.content.Context
import com.srineere.airbridge.RelayRuleStore
import org.json.JSONObject
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

/**
 * Your readings, as files you keep.
 *
 * This app is unofficial and rides a protocol an update can break. The
 * readings should outlive it, and Health Connect holds only some of them:
 * the goals, the tab, the saved foods and the relay's rules exist nowhere
 * else, so losing this app loses them for good unless they come out too.
 *
 * CSV, and one file a kind, because a spreadsheet is the only tool everyone
 * already has. The rolling averages are columns rather than something to
 * work out afterwards: a file that answers a question the moment it opens
 * is worth more than a file that is merely complete.
 *
 * Nothing is uploaded. The caller hands this an [OutputStream] from
 * Android's own file picker, so where it lands is the person's choice and
 * this object never learns of it.
 */
object HealthExport {

    /** How far back, in the sizes somebody actually picks. */
    enum class Range(val label: String, val days: Int) {
        Month("30 days", 30),
        Quarter("90 days", 90),
        Year("1 year", 365),

        /**
         * Everything Health Connect will give up.
         *
         * Five years rather than no limit: the API takes a start, so
         * "everything" has to be a number, and a window longer than the
         * platform keeps costs nothing but reads an empty range.
         */
        All("Everything", 1825),
    }

    /** One file, and what it is worth saying it holds. */
    enum class Part(val label: String, val detail: String, val file: String) {
        Daily(
            "Daily figures",
            "steps, resting rate, HRV, breathing, weight",
            "daily.csv",
        ),
        Nights("Nights", "stages, score and its four parts", "nights.csv"),
        Workouts("Workouts", "kind, length, distance and heart rate", "workouts.csv"),
        Food("Food", "every entry, and what was in it", "food.csv"),

        /**
         * The app's own, kept apart from the readings on purpose: these are
         * the rows no other app holds a copy of.
         */
        Settings(
            "This app's own",
            "goals, tiles, saved foods, rules",
            "air-bridge.json",
        ),
    }

    /**
     * How much each part would carry, for the row that offers it.
     *
     * "Steps" and "steps, 412 days of them" are different promises, and
     * only the second is checkable before the button is pressed.
     */
    fun counts(snapshot: HealthSnapshot, context: Context): Map<Part, String> {
        val days = (
            snapshot.steps.map { it.day } + snapshot.burnedDaily.map { it.day } +
                snapshot.restingHeartRate.map { it.day } + snapshot.weight.map { it.day }
            ).distinct().size
        return mapOf(
            Part.Daily to plural(days, "day", "days"),
            Part.Nights to plural(snapshot.nights.size, "night", "nights"),
            Part.Workouts to "${snapshot.sessions.size}",
            Part.Food to "${snapshot.food.size}",
            Part.Settings to "${SavedFoods.read(context).size} foods, settings",
        )
    }

    /** Writes the chosen parts into [output] as a zip, and closes nothing. */
    suspend fun write(
        context: Context,
        output: OutputStream,
        snapshot: HealthSnapshot,
        parts: Set<Part>,
        range: Range,
        zone: ZoneId = ZoneId.systemDefault(),
        /**
         * How far through the workouts it is, since that part reads.
         *
         * Everything else in this zip is already in the snapshot and
         * writes at the speed of a string builder. Workouts cost one
         * aggregate call each, so a year of them is a wait -- and a wait
         * with no sign of progress is indistinguishable from a hang.
         */
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val zip = ZipOutputStream(output)
        // Not closed with `use`: the stream belongs to the caller's content
        // resolver, and finishing the zip is this object's job while
        // closing the stream under it is not.
        zip.setComment(
            "CUFF export, ${LocalDate.now(zone)}, ${range.label.lowercase()}",
        )
        parts.forEach { part ->
            zip.putNextEntry(ZipEntry(part.file))
            val writer = OutputStreamWriter(zip, Charsets.UTF_8)
            when (part) {
                Part.Daily -> daily(writer, snapshot)
                Part.Nights -> nights(writer, snapshot, zone)
                Part.Workouts -> workouts(context, writer, snapshot, zone, onProgress)
                Part.Food -> food(writer, snapshot, zone)
                Part.Settings -> writer.write(settings(context).toString(2))
            }
            // Flushed rather than closed: closing the writer would close the
            // zip under it and the next entry would have nowhere to go.
            writer.flush()
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    // ------------------------------------------------------------- the files

    private fun daily(writer: OutputStreamWriter, snapshot: HealthSnapshot) {
        val series = listOf(
            "steps" to snapshot.steps,
            "resting_bpm" to snapshot.restingHeartRate,
            "hrv_ms" to snapshot.hrv,
            "breathing_per_min" to snapshot.respiratoryRate,
            "skin_c" to snapshot.skinTemperature,
            "weight_kg" to snapshot.weight,
            "burned_kcal" to snapshot.burnedDaily,
            "eaten_kcal" to snapshot.eatenDaily,
            "protein_g" to snapshot.proteinDaily,
            "carbs_g" to snapshot.carbsDaily,
            "fat_g" to snapshot.fatDaily,
        )
        val days = series.flatMap { (_, rows) -> rows.map { it.day } }
            .distinct()
            .sorted()
        val byName = series.associate { (name, rows) ->
            name to rows.associate { it.day to it.value }
        }
        // Every series gets its own trailing mean, over the days that have a
        // reading rather than over the calendar: a seven day window with two
        // gaps in it is an average of five days, not an average of five days
        // and two zeroes.
        val rolling = byName.mapValues { (_, byDay) -> trailingMean(days, byDay) }

        val head = listOf("date", "epoch_day") +
            series.flatMap { (name, _) -> listOf(name, "${name}_7d") } +
            "food_entries"
        writer.write(head.joinToString(","))
        writer.write("\n")
        days.forEach { day ->
            val row = mutableListOf(LocalDate.ofEpochDay(day).toString(), day.toString())
            series.forEach { (name, _) ->
                row += figure(byName.getValue(name)[day])
                row += figure(rolling.getValue(name)[day])
            }
            row += (snapshot.mealsDaily[day] ?: 0).toString()
            writer.write(row.joinToString(","))
            writer.write("\n")
        }
    }

    private fun nights(writer: OutputStreamWriter, snapshot: HealthSnapshot, zone: ZoneId) {
        writer.write(
            "date,start,end,asleep_min,awake_min,light_min,deep_min,rem_min," +
                "efficiency,score,score_duration,score_stages,score_consistency," +
                "score_interruptions\n",
        )
        snapshot.nights.sortedBy { it.day }.forEach { night ->
            // Scored against the nights that came before it, which is how
            // the tab scores it: a score out of its own history is a
            // different number from one out of everybody's.
            val score = sleepScoreOf(night, snapshot.nights)
            val row = listOf(
                LocalDate.ofEpochDay(night.day).toString(),
                night.start.atZone(zone).toLocalTime().toString(),
                night.end.atZone(zone).toLocalTime().toString(),
                night.asleep.toMinutes().toString(),
                night.awake.toMinutes().toString(),
                night.light.toMinutes().toString(),
                night.deep.toMinutes().toString(),
                night.rem.toMinutes().toString(),
                figure(night.efficiency),
                score?.total?.toString().orEmpty(),
                score?.duration?.toString().orEmpty(),
                score?.stages?.toString().orEmpty(),
                score?.consistency?.toString().orEmpty(),
                score?.interruptions?.toString().orEmpty(),
            )
            writer.write(row.joinToString(","))
            writer.write("\n")
        }
    }

    /**
     * Every workout, with the figures its own screen shows.
     *
     * This file wrote the exercise type as the integer Health Connect
     * stores -- a column of 76s and 79s -- and no heart rate at all,
     * while the row offering it promised "kind, length and heart rate".
     * A promise unkept in the one file somebody opens precisely because
     * they want the numbers out of this app.
     *
     * One aggregate call per session, which is what SessionSummary was
     * built for. The alternative is reading per-second samples for every
     * workout in the range: tens of thousands of records to print two
     * columns.
     */
    private suspend fun workouts(
        context: Context,
        writer: OutputStreamWriter,
        snapshot: HealthSnapshot,
        zone: ZoneId,
        onProgress: (Int, Int) -> Unit,
    ) {
        writer.write(
            "date,start,end,kind,title,length_min,distance_km,kcal,steps," +
                "avg_bpm,peak_bpm,device,type_code\n"
        )
        val ordered = snapshot.sessions.sortedBy { it.start }
        onProgress(0, ordered.size)
        ordered.forEachIndexed { index, session ->
            // A session whose summary will not read still gets its row.
            // The times and the kind are already in hand, and a workout
            // missing from the file would be worse than one with empty
            // columns.
            val found = runCatching {
                SessionReader().summary(context, session.start, session.end)
            }.getOrNull()
            val row = listOf(
                session.start.atZone(zone).toLocalDate().toString(),
                session.start.atZone(zone).toLocalTime().toString(),
                session.end.atZone(zone).toLocalTime().toString(),
                quote(session.exerciseName()),
                quote(session.title.orEmpty()),
                java.time.Duration.between(session.start, session.end).toMinutes().toString(),
                found?.distanceKm?.let { "%.2f".format(it) }.orEmpty(),
                found?.kcal?.roundToInt()?.toString().orEmpty(),
                found?.steps?.toString().orEmpty(),
                found?.averageBpm?.toString().orEmpty(),
                found?.peakBpm?.toString().orEmpty(),
                quote(session.device?.label.orEmpty()),
                // Kept beside the name rather than replaced by it: the
                // name is for reading and the code is for grouping, and
                // a spreadsheet wants both.
                session.exerciseType.toString(),
            )
            writer.write(row.joinToString(","))
            writer.write("\n")
            onProgress(index + 1, ordered.size)
        }
    }

    private fun food(writer: OutputStreamWriter, snapshot: HealthSnapshot, zone: ZoneId) {
        writer.write("date,time,meal,name,kcal,protein_g,carbs_g,fat_g\n")
        snapshot.food.sortedBy { it.at }.forEach { entry ->
            val meal = Meal.entries.firstOrNull { it.type == entry.mealType }?.label.orEmpty()
            val row = listOf(
                LocalDate.ofEpochDay(entry.day).toString(),
                entry.at.atZone(zone).toLocalTime().toString(),
                meal,
                quote(entry.name.orEmpty()),
                figure(entry.kcal),
                figure(entry.protein),
                figure(entry.carbs),
                figure(entry.fat),
            )
            writer.write(row.joinToString(","))
            writer.write("\n")
        }
    }

    /**
     * Everything this app keeps that Health Connect does not.
     *
     * JSON rather than CSV because it is not a table: it is the shape of a
     * tab, a list of foods and a set of rules, and flattening those into
     * columns would lose the thing worth keeping.
     */
    private fun settings(context: Context): JSONObject {
        val goals = HealthGoals.read(context)
        return JSONObject()
            .put("writtenAt", java.time.Instant.now().toString())
            .put(
                "goals",
                JSONObject()
                    .put("steps", goals.steps)
                    .put("sleepMinutes", goals.sleepMinutes)
                    .put("energyKcal", goals.energyKcal)
                    .put("proteinGrams", goals.proteinGrams ?: JSONObject.NULL)
                    .put("activeMinutes", goals.activeMinutes),
            )
            .put("tiles", encodeTiles(HealthSettings.tiles(context)))
            .put("savedFoods", SavedFoods.encode(SavedFoods.read(context)))
            // The relay's own rules, in the shape the store writes them, so
            // this file is also what a restore would read back.
            .put("relay", RelayRuleStore.encode(RelayRuleStore.read(context)))
    }

    // ------------------------------------------------------------- the small

    /**
     * A trailing seven-day mean over the days that have a reading.
     *
     * Not a calendar window: a fortnight with four gaps in it averages the
     * ten days that exist, because a missing day is a day nobody measured
     * and counting it as zero would be the chart lying in a spreadsheet.
     */
    private fun trailingMean(days: List<Long>, byDay: Map<Long, Double>): Map<Long, Double?> {
        val answer = mutableMapOf<Long, Double?>()
        days.forEach { day ->
            val window = (0 until 7)
                .mapNotNull { back -> byDay[day - back] }
            answer[day] = window.takeIf { it.isNotEmpty() }?.average()
        }
        return answer
    }

    /** A figure a spreadsheet will read as a number, or an empty cell. */
    private fun figure(value: Double?): String = when {
        value == null -> ""
        value == value.roundToInt().toDouble() -> value.roundToInt().toString()
        else -> "%.3f".format(value)
    }

    /** CSV quoting: only when it is needed, and doubled inside. */
    private fun quote(value: String): String =
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) value
        else "\"" + value.replace("\"", "\"\"") + "\""

    private fun plural(count: Int, one: String, many: String): String =
        if (count == 1) "1 $one" else "%,d %s".format(count, many)
}
