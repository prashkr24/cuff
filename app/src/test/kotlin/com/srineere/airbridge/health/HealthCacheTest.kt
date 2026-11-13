package com.srineere.airbridge.health

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * What the tab keeps of its last read, and the three ways that can lie.
 *
 * A cache of health figures goes wrong in ways nothing about it looks
 * broken. Half a snapshot drawn as a whole one reads as a fortnight
 * somebody did not walk. Yesterday's running totals drawn under today's
 * date read as a morning that has already gone badly. And a figure that
 * changes a second after it is drawn reads as an app that cannot make its
 * mind up. Each of those has a test here.
 */
class HealthCacheTest {

    /** An arbitrary epoch day, so none of this depends on when it is run. */
    private val today = 20_405L

    /** A fixed instant, late on [today] as far as anything here cares. */
    private val at = Instant.ofEpochMilli(1_762_000_000_000L)

    private fun night(day: Long, minutes: Long): Night {
        val start = at.plusMillis(day * 1000)
        return Night(
            day = day,
            start = start,
            end = start.plus(Duration.ofMinutes(minutes)),
            asleep = Duration.ofMinutes(minutes),
            awake = Duration.ofMinutes(12),
            light = Duration.ofMinutes(minutes / 2),
            deep = Duration.ofMinutes(minutes / 4),
            rem = Duration.ofMinutes(minutes / 4),
            segments = listOf(
                SleepSegment(SleepStage.Light, start, start.plusMillis(600_000)),
                SleepSegment(SleepStage.Deep, start.plusMillis(600_000), start.plusMillis(1_200_000)),
                SleepSegment(SleepStage.Awake, start.plusMillis(1_200_000), start.plusMillis(1_260_000)),
                SleepSegment(SleepStage.Rem, start.plusMillis(1_260_000), start.plusMillis(1_800_000)),
            ),
        )
    }

    private fun meal(day: Long, id: String, kcal: Double?) = FoodEntry(
        id = id,
        at = at.plusMillis(day * 1000),
        day = day,
        name = "Porridge",
        kcal = kcal,
        protein = 11.5,
        carbs = null,
        fat = 4.25,
        mealType = 1,
        source = "com.srineere.cuff",
    )

    /** A read with something of everything in it, including the parts not kept. */
    private fun full(from: Long = today - 4): HealthSnapshot {
        val days = (from..today).toList()
        val food = days.map { meal(it, "food-$it", 420.0) } + meal(today, "food-extra", null)
        return HealthSnapshot(
            stepsToday = 8_213L,
            steps = days.map { Reading(it, 6000.0 + it % 7 * 250) },
            restingHeartRate = days.map { Reading(it, 56.0 + it % 3) },
            hrv = days.map { Reading(it, 41.5 + it % 5) },
            respiratoryRate = days.map { Reading(it, 14.25) },
            // Negative, and finer than one decimal: skin temperature is
            // written as a delta and rounding it would erase the reading.
            skinTemperature = days.map { Reading(it, -0.23) },
            vo2Max = listOf(Reading(today - 3, 42.7)),
            nights = days.map { night(it, 400L + it % 30) },
            sessions = listOf(
                Session(
                    start = at,
                    end = at.plus(Duration.ofMinutes(45)),
                    title = "Evening walk",
                    exerciseType = 79,
                    device = RecordedBy(type = 2, manufacturer = "Fitbit", model = null),
                ),
                Session(at, at.plus(Duration.ofMinutes(20)), null, 56, null),
            ),
            heartToday = listOf(HeartSample(at.toEpochMilli(), 71)),
            heartRecent = listOf(HeartSample(at.toEpochMilli(), 71)),
            burnedDaily = days.map { Reading(it, 2210.5) },
            eatenDaily = days.map { Reading(it, 1803.0) },
            weight = listOf(Reading(today - 1, 72.35)),
            burnedHourly = List(24) { it * 1.5 },
            stepsHourly = List(24) { it * 40.0 },
            burnedToday = 1_440.25,
            eatenToday = 980.0,
            proteinDaily = days.map { Reading(it, 96.0) },
            carbsDaily = days.map { Reading(it, 210.0) },
            fatDaily = days.map { Reading(it, 61.0) },
            mealsDaily = food.groupingBy { it.day }.eachCount(),
            food = food,
            proteinToday = 48.0,
            carbsToday = 102.5,
            fatToday = 30.0,
            writtenAt = at,
            missingPermissions = setOf("android.permission.health.READ_OXYGEN_SATURATION"),
        )
    }

    // -------------------------------------------------- the two string halves

    @Test fun `a series survives the preferences file exactly as it was read`() {
        val readings = listOf(
            Reading(20_405L, 8213.0),
            // The reason this is not [encodeSeries]: readiness rounds to a
            // decimal because it is drawn whole, and a skin temperature
            // delta of -0.23 cannot afford that.
            Reading(20_406L, -0.23),
            Reading(20_407L, 72.355),
        )
        assertEquals("20405:8213.0;20406:-0.23;20407:72.355", encodeReadings(readings))
        assertEquals(readings, decodeReadings(encodeReadings(readings)))
        assertEquals(emptyList<Reading>(), decodeReadings(""))
    }

    @Test fun `a series that does not parse is nothing, never the half that did`() {
        // Null rather than a short list, and the difference matters more
        // here than it does for readiness: every chart on this tab draws a
        // missing day as a day you did not walk.
        assertNull(decodeReadings("20405:8213.0;rubbish;20407:1.0"))
        assertNull(decodeReadings("20405"))
        assertNull(decodeReadings("20405:8213.0:9"))
        assertNull(decodeReadings("day:8213.0"))
    }

    @Test fun `hourly figures keep their order and their silence`() {
        assertEquals("0.0;12.5;0.0", encodeHours(listOf(0.0, 12.5, 0.0)))
        assertEquals(listOf(0.0, 12.5, 0.0), decodeHours("0.0;12.5;0.0"))
        assertEquals(emptyList<Double>(), decodeHours(""))
        assertNull(decodeHours("0.0;noon"))
    }

    @Test fun `a hypnogram is kept as offsets inside its own night`() {
        val start = Instant.ofEpochMilli(1_700_000_000_000L)
        val segments = listOf(
            SleepSegment(SleepStage.Light, start, start.plusMillis(600_000)),
            SleepSegment(SleepStage.Deep, start.plusMillis(600_000), start.plusMillis(1_200_000)),
        )
        // Offsets, not instants: five or six digits where an absolute
        // epoch millisecond is thirteen, a thousand times over.
        assertEquals("Light:0:600000;Deep:600000:1200000", encodeSegments(start, segments))
        assertEquals(segments, decodeSegments(encodeSegments(start, segments), start))
        assertEquals(emptyList<SleepSegment>(), decodeSegments("", start))
    }

    @Test fun `a stage this build does not know throws the night away`() {
        val start = Instant.ofEpochMilli(1_700_000_000_000L)
        // Written by a later version that distinguishes a stage this one
        // does not. Guessing at it would file somebody's deep sleep under
        // whichever name happened to sort first.
        assertNull(decodeSegments("Dreaming:0:600000", start))
        assertNull(decodeSegments("Light:0", start))
    }

    // ------------------------------------------------------- the whole entry

    @Test fun `the read comes back the way it went in`() {
        val kept = KeptHealth(
            readAt = 1_762_000_000_000L,
            day = today,
            days = HealthCache.DAYS,
            snapshot = keepable(full(), today - HealthCache.DAYS + 1),
        )
        val back = decodeHealth(encodeHealth(kept))
        assertNotNull(back)
        assertEquals(kept.readAt, back!!.readAt)
        assertEquals(kept.day, back.day)
        assertEquals(kept.days, back.days)
        // The whole snapshot, field for field. Trends, bands and scores are
        // all worked out from this by one function, so getting this right
        // is the whole of getting a restored screen right.
        assertEquals(kept.snapshot, back.snapshot)
    }

    @Test fun `the beat samples are the one thing not kept`() {
        val read = full()
        assertTrue(read.heartToday.isNotEmpty())
        val kept = keepable(read, today - HealthCache.DAYS + 1)
        // Tens of thousands of readings about *today*, in a preferences
        // file read into memory whole. The Heart screen says it is reading
        // rather than that nothing was recorded.
        assertTrue(kept.heartToday.isEmpty())
        assertTrue(kept.heartRecent.isEmpty())
        assertTrue(decodeHealth(encodeHealth(entry(kept)))!!.snapshot.heartRecent.isEmpty())
    }

    @Test fun `anything that does not parse is no cache at all`() {
        assertNull(decodeHealth(""))
        assertNull(decodeHealth("not json"))
        // Legal JSON with nothing in it is the same answer. A snapshot
        // missing half its fields draws as a person missing half their life.
        assertNull(decodeHealth("{}"))
        assertNull(decodeHealth("""{"version":1}"""))
    }

    @Test fun `an entry from another version is thrown away rather than read`() {
        val text = encodeHealth(entry(keepable(full(), today - 29)))
        assertNotNull(decodeHealth(text))
        val later = JSONObject(text).put("version", CACHE_VERSION + 1).toString()
        assertNull(decodeHealth(later))
        val earlier = JSONObject(text).put("version", CACHE_VERSION - 1).toString()
        assertNull(decodeHealth(earlier))
    }

    @Test fun `one unreadable night costs the whole entry, not one night`() {
        val text = encodeHealth(entry(keepable(full(), today - 29)))
        val damaged = JSONObject(text)
        damaged.getJSONArray("nights").getJSONObject(0).put("stages", "Dreaming:0:600000")
        // Not four nights out of five. A sleep chart with a hole in it is a
        // night somebody is told they did not sleep.
        assertNull(decodeHealth(damaged.toString()))

        val mangled = JSONObject(text).put("steps", "20405:lots")
        assertNull(decodeHealth(mangled.toString()))
    }

    // ------------------------------------------------------- the day boundary

    @Test fun `a cache read on the day it was written is the whole of it`() {
        val read = keepable(full(), today - 29)
        assertEquals(read, asOf(read, today, today))
    }

    @Test fun `tomorrow morning, today's running totals are not today's`() {
        val read = keepable(full(), today - 29)
        // Two in the morning, the day after the last read. Restoring the
        // step count would open the tab on yesterday's nine thousand steps
        // under today's date, which is the kind of wrong that looks right.
        val next = asOf(read, today, today + 1)
        assertNull(next.stepsToday)
        assertNull(next.burnedToday)
        assertNull(next.eatenToday)
        assertNull(next.proteinToday)
        assertNull(next.carbsToday)
        assertNull(next.fatToday)
        assertTrue(next.burnedHourly.isEmpty())
        assertTrue(next.stepsHourly.isEmpty())

        // Everything keyed by a day is still true, because a day is a day
        // whenever you read it. This is the half that makes the tab open
        // with figures on it rather than a spinner.
        assertEquals(read.steps, next.steps)
        assertEquals(read.nights, next.nights)
        assertEquals(read.hrv, next.hrv)
        assertEquals(read.food, next.food)
        assertEquals(read.writtenAt, next.writtenAt)
    }

    @Test fun `a cache older than yesterday is still every day it holds`() {
        // Not thrown away on age. A week of not opening the app leaves
        // thirty real days of readings ending a week ago, and the header's
        // age comes from `writtenAt` -- an instant -- so the screen says
        // how old they are without this having to decide for it.
        val read = keepable(full(), today - 29)
        val later = asOf(read, today, today + 9)
        assertEquals(read.nights, later.nights)
        assertNull(later.stepsToday)
    }

    // ------------------------------------------------------------ the window

    @Test fun `only the tab's own window is kept, however far the read went`() {
        // Readiness and fitness age read a hundred and twenty days. Keeping
        // that would quadruple the entry for a figure readiness already
        // keeps for itself.
        val long = full(from = today - 119)
        assertEquals(120, long.steps.size)
        val kept = keepable(long, today - HealthCache.DAYS + 1)
        assertEquals(HealthCache.DAYS, kept.steps.size)
        assertEquals(HealthCache.DAYS, kept.nights.size)
        assertEquals(today - HealthCache.DAYS + 1, kept.steps.first().day)
        assertTrue(kept.food.all { it.day > today - HealthCache.DAYS })
    }

    @Test fun `the meal count is the food list counted, after the trim`() {
        val kept = keepable(full(from = today - 119), today - HealthCache.DAYS + 1)
        // Not stored: a count kept beside the rows it counts is a count
        // that can disagree with them, which is the bug it would exist to
        // prevent. Two entries on the last day, one on every other.
        assertEquals(kept.food.groupingBy { it.day }.eachCount(), kept.mealsDaily)
        assertEquals(2, kept.mealsDaily[today])
        assertEquals(1, kept.mealsDaily[today - 1])
        assertEquals(kept.mealsDaily, decodeHealth(encodeHealth(entry(kept)))!!.snapshot.mealsDaily)
    }

    private fun entry(snapshot: HealthSnapshot) = KeptHealth(
        readAt = 1_762_000_000_000L,
        day = today,
        days = HealthCache.DAYS,
        snapshot = snapshot,
    )
}
