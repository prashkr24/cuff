package com.srineere.airbridge.widget

import com.srineere.airbridge.R
import com.srineere.airbridge.health.FoodEntry
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.HeartSample
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.RecordedBy
import com.srineere.airbridge.health.Session
import com.srineere.airbridge.health.SleepSegment
import com.srineere.airbridge.health.SleepStage
import com.srineere.airbridge.ui.HealthStage
import com.srineere.airbridge.ui.healthDataOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * What a widget keeps on disk, and what it is allowed to draw from it.
 *
 * Two bugs live behind these. A widget's snapshot used to be held in
 * memory and nowhere else, so a reboot left every face with nothing to
 * draw; and nothing told the two empty states of a stack apart, so a
 * perfectly well configured stack came back from every restart asking to
 * be set up again.
 *
 * The rule that is easiest to break quietly is the day boundary. A figure
 * that meant "today" when it was written is not today's figure after
 * midnight, and the restore has to say so -- a widget drawing yesterday's
 * eleven thousand steps at two in the morning is wrong in the one way
 * nobody catches, because the number looks right.
 */
class WidgetSnapshotStoreTest {

    /** Fixed, so "which local day was this read on" is arithmetic, not weather. */
    private val zone = ZoneId.of("UTC")
    private val today = 20_405L
    private val noon = today * DAY_MILLIS + 12 * 3_600_000L
    private val lastNight = noon - 13 * 3_600_000L

    // ------------------------------------------------------------ fixture

    private fun reading(day: Long, value: Double) = Reading(day, value)

    private fun health() = HealthSnapshot(
        stepsToday = 8_421,
        steps = (0..4).map { reading(today - 4 + it, 7_000.0 + it * 500) },
        restingHeartRate = (0..4).map { reading(today - 4 + it, 52.0 - it * 0.5) },
        hrv = (0..4).map { reading(today - 4 + it, 40.0 + it) },
        respiratoryRate = (0..4).map { reading(today - 4 + it, 14.0 + it * 0.1) },
        skinTemperature = (0..4).map { reading(today - 4 + it, -0.2 + it * 0.05) },
        vo2Max = listOf(reading(today - 7, 41.3)),
        weight = (0..4).map { reading(today - 4 + it, 74.0 + it * 0.2) },
        burnedDaily = (0..4).map { reading(today - 4 + it, 2_300.0 + it * 20) },
        eatenDaily = (0..4).map { reading(today - 4 + it, 2_100.0 + it * 30) },
        nights = listOf(
            Night(
                day = today,
                start = Instant.ofEpochMilli(lastNight),
                end = Instant.ofEpochMilli(lastNight + 26_000_000L),
                asleep = Duration.ofMinutes(412),
                awake = Duration.ofMinutes(23),
                light = Duration.ofMinutes(210),
                deep = Duration.ofMinutes(92),
                rem = Duration.ofMinutes(110),
                // Kept here to prove they are let go: no widget draws a
                // hypnogram, and thirty nights of these is the biggest
                // thing in the file after the beats.
                segments = listOf(
                    SleepSegment(
                        SleepStage.Light,
                        Instant.ofEpochMilli(lastNight),
                        Instant.ofEpochMilli(lastNight + 3_600_000L),
                    ),
                ),
            ),
        ),
        sessions = listOf(
            Session(
                start = Instant.ofEpochMilli(noon - 7_200_000L),
                end = Instant.ofEpochMilli(noon - 5_400_000L),
                title = "Evening run",
                exerciseType = 56,
                device = RecordedBy(2, "Fitbit", "Air"),
            ),
            // A workout whose writer filled nothing in, which is most of
            // them: the absence has to survive as an absence.
            Session(
                start = Instant.ofEpochMilli(noon - 90_000_000L),
                end = Instant.ofEpochMilli(noon - 86_400_000L),
                title = null,
                exerciseType = 79,
                device = null,
            ),
        ),
        food = listOf(
            FoodEntry(
                id = "f-1",
                at = Instant.ofEpochMilli(noon - 3_600_000L),
                day = today,
                name = "Porridge",
                kcal = 340.0,
                protein = 12.0,
                carbs = 55.0,
                fat = 6.0,
                mealType = 1,
                source = "com.srineere.cuff",
            ),
        ),
        // Either side of a five-minute boundary, so both survive the
        // thinning and this fixture says nothing about it either way.
        heartToday = listOf(HeartSample(noon - 1_000L, 70), HeartSample(noon, 92)),
        burnedHourly = List(24) { it * 90.0 },
        stepsHourly = List(24) { it * 400.0 },
        burnedToday = 1_100.0,
        eatenToday = 1_740.0,
        proteinToday = 88.0,
        carbsToday = 190.0,
        fatToday = 54.0,
    )

    private fun kept(snapshot: HealthSnapshot = health(), at: Long = noon, days: Int = 30) =
        KeptSnapshot(snapshot, at, days)

    private fun roundTrip(kept: KeptSnapshot, on: Long = today): KeptSnapshot? =
        decodeKept(encodeKept(kept, zone), on)

    /** What a night looks like once the hypnogram has gone. */
    private fun withoutSegments(snapshot: HealthSnapshot) =
        snapshot.copy(nights = snapshot.nights.map { it.copy(segments = emptyList()) })

    // ------------------------------------------------------- the round trip

    @Test
    fun `a snapshot read today comes back as it went in`() {
        val decoded = roundTrip(kept())
        assertNotNull(decoded)
        assertEquals(withoutSegments(health()), decoded?.health)
        assertEquals(noon, decoded?.readAt)
        assertEquals(30, decoded?.days)
    }

    @Test
    fun `a wider read says how wide it was, so a narrow one can be noticed`() {
        assertEquals(120, roundTrip(kept(days = 120))?.days)
    }

    /**
     * The hypnogram is not kept. `hasStages` is answered by light, deep
     * and rem rather than by the segments, so the two sleep faces are
     * unaffected; `Night.wakes` would read zero, and nothing in `widget/`
     * asks it. Anything that starts to has to keep them.
     */
    @Test
    fun `a night keeps its stages' lengths and lets its segments go`() {
        val night = roundTrip(kept())?.health?.nights?.single()
        assertEquals(emptyList<SleepSegment>(), night?.segments)
        assertEquals(Duration.ofMinutes(92), night?.deep)
        assertEquals(true, night?.hasStages)
    }

    /** A workout with no device is not a workout whose device was forgotten. */
    @Test
    fun `a session keeps the hardware it named and the silence where it did not`() {
        val sessions = roundTrip(kept())?.health?.sessions.orEmpty()
        assertEquals(RecordedBy(2, "Fitbit", "Air"), sessions[0].device)
        assertEquals("Evening run", sessions[0].title)
        assertNull(sessions[1].device)
        assertNull(sessions[1].title)
    }

    /**
     * Four series no widget draws, left out on purpose.
     *
     * `heartRecent` is yesterday-and-today's beats for a sleeping heart
     * rate no face shows; the three macro series by day, the meals count
     * and `writtenAt` are tab-only. Each would be kept the day something
     * on the home screen starts drawing it.
     */
    @Test
    fun `the series no widget draws are not kept at all`() {
        val fat = health().copy(
            heartRecent = listOf(HeartSample(noon - DAY_MILLIS, 58)),
            proteinDaily = listOf(reading(today, 88.0)),
            carbsDaily = listOf(reading(today, 190.0)),
            fatDaily = listOf(reading(today, 54.0)),
            mealsDaily = mapOf(today to 3),
            writtenAt = Instant.ofEpochMilli(noon),
        )
        val decoded = roundTrip(kept(fat))!!.health
        assertEquals(emptyList<HeartSample>(), decoded.heartRecent)
        assertEquals(emptyList<Reading>(), decoded.proteinDaily)
        assertEquals(emptyList<Reading>(), decoded.carbsDaily)
        assertEquals(emptyList<Reading>(), decoded.fatDaily)
        assertEquals(emptyMap<Long, Int>(), decoded.mealsDaily)
        assertNull(decoded.writtenAt)
    }

    // --------------------------------------------------- what is on the disk

    /**
     * A series is a pair per row, and this is asserted rather than left to
     * the round trip because the round trip would pass just as happily on
     * four times the bytes. Five hundred rows of named objects in a
     * preferences file rewritten every half hour is the thing being
     * avoided.
     */
    @Test
    fun `a series is stored as day and value, and nothing else`() {
        val raw = encodeKept(
            kept(HealthSnapshot(steps = listOf(reading(20_405, 8_421.0), reading(20_406, 1_000.5)))),
            zone,
        )
        assertEquals("[[20405,8421],[20406,1000.5]]", JSONObject(raw).getJSONArray("steps").toString())
    }

    /**
     * Eleven positions, every length in seconds.
     *
     * Time in bed is among them rather than measured back from the start
     * and end: a day whose naps have been folded into one night has its
     * sleep spread over sessions, and the span between the two instants
     * here covers only the longest of them.
     */
    @Test
    fun `a night is stored as eleven numbers`() {
        val raw = encodeKept(kept(HealthSnapshot(nights = health().nights)), zone)
        assertEquals(
            "[[20405,${lastNight},${lastNight + 26_000_000L},24720,1380,12600,5520,6600,26000,0,0]]",
            JSONObject(raw).getJSONArray("nights").toString(),
        )
    }

    /**
     * The day the read happened on is stored beside the clock reading, and
     * is not derivable from it: a phone carried two time zones west is on
     * a different day at the same instant.
     */
    @Test
    fun `the entry says which version and which local day it is`() {
        val root = JSONObject(encodeKept(kept(), zone))
        // Written down rather than compared against the constant, so that
        // changing the shape of an entry without bumping it fails here --
        // which is the only thing standing between an old entry and being
        // read as a new one. 2 added time in bed and the nap counts.
        assertEquals(2, root.getInt("version"))
        assertEquals(today, root.getLong("day"))
        assertEquals(noon, root.getLong("readAt"))
    }

    // ------------------------------------------------------- failing closed

    @Test
    fun `nothing stored means nothing kept`() {
        assertNull(decodeKept(null, today))
        assertNull(decodeKept("", today))
    }

    @Test
    fun `something that is not an entry at all is refused`() {
        assertNull(decodeKept("not json", today))
        assertNull(decodeKept("{}", today))
        assertNull(decodeKept("[]", today))
    }

    /**
     * A row written by a later build is thrown away rather than read for
     * the half of it this one understands. The entry is a cache: there is
     * nothing in it that cannot be read again in half a minute, which
     * makes this the cheapest place in the app to obey "fail closed".
     */
    @Test
    fun `an entry from a newer build decodes to nothing`() {
        // Any number above the one this build writes; it has to be raised
        // with the constant.
        val newer = JSONObject(encodeKept(kept(), zone)).put("version", 3).toString()
        assertNull(decodeKept(newer, today))
    }

    @Test
    fun `an entry missing a series is refused whole, not read in part`() {
        val short = JSONObject(encodeKept(kept(), zone)).apply { remove("nights") }.toString()
        assertNull(decodeKept(short, today))
    }

    /** A write cut off halfway is the realistic corruption, and parses as nothing. */
    @Test
    fun `a truncated entry decodes to nothing`() {
        val raw = encodeKept(kept(), zone)
        assertNull(decodeKept(raw.substring(0, raw.length / 2), today))
    }

    @Test
    fun `a series whose rows are not pairs is refused whole`() {
        val halfRow = JSONArray().put(JSONArray().put(20_405L))
        val bent = JSONObject(encodeKept(kept(), zone)).put("steps", halfRow).toString()
        assertNull(decodeKept(bent, today))
        val notAnArray = JSONObject(encodeKept(kept(), zone)).put("steps", "9000").toString()
        assertNull(decodeKept(notAnArray, today))
    }

    // -------------------------------------------------------- age, and midnight

    @Test
    fun `a snapshot restored on the day it was read keeps today's figures`() {
        val decoded = roundTrip(kept(), on = today)!!.health
        assertEquals(8_421L, decoded.stepsToday!!)
        assertEquals(1_100.0, decoded.burnedToday!!, 0.001)
        assertEquals(1_740.0, decoded.eatenToday!!, 0.001)
        assertEquals(24, decoded.burnedHourly.size)
        assertEquals(2, decoded.heartToday.size)
        assertEquals(1, decoded.food.size)
    }

    /**
     * The bug this rule exists for: a widget drawn at two in the morning
     * from a snapshot taken at eleven last night, printing yesterday's
     * eleven thousand steps under today's heading.
     */
    @Test
    fun `a snapshot restored after midnight drops everything that meant today`() {
        val decoded = roundTrip(kept(), on = today + 1)!!.health
        assertNull(decoded.stepsToday)
        assertNull(decoded.burnedToday)
        assertNull(decoded.eatenToday)
        assertNull(decoded.proteinToday)
        assertNull(decoded.carbsToday)
        assertNull(decoded.fatToday)
        assertEquals(emptyList<Double>(), decoded.burnedHourly)
        assertEquals(emptyList<Double>(), decoded.stepsHourly)
        assertEquals(emptyList<HeartSample>(), decoded.heartToday)
        assertEquals(emptyList<FoodEntry>(), decoded.food)
    }

    /**
     * And keeps everything counted by day, which is still true: the last
     * entry in `steps` was today's running count when it was written and
     * is yesterday's finished total now, filed under the day it belongs
     * to either way.
     */
    @Test
    fun `a snapshot restored after midnight keeps the days behind it`() {
        val decoded = roundTrip(kept(), on = today + 1)!!.health
        assertEquals(health().steps, decoded.steps)
        assertEquals(health().restingHeartRate, decoded.restingHeartRate)
        assertEquals(1, decoded.nights.size)
        assertEquals(2, decoded.sessions.size)
        assertEquals(health().vo2Max, decoded.vo2Max)
    }

    /**
     * The age is the real one, in both directions.
     *
     * A restored snapshot that claimed to have been read just now would
     * look fresh to the cache and never be replaced, and every age-aware
     * figure drawn from it -- a battery dimmed after a day, a night that
     * counts as last night only if it ended this morning -- would be
     * confidently wrong.
     */
    @Test
    fun `a restored snapshot carries the moment it was really read`() {
        assertEquals(noon, roundTrip(kept())?.readAt)
        assertEquals(noon, roundTrip(kept(), on = today + 9)?.readAt)
    }

    // ------------------------------------------------------------- the beats

    /**
     * Today's beats are kept one every five minutes, and the one thing
     * drawn from them is the highest sample in each hour. Five minutes
     * divides an hour, so that picture is unchanged.
     */
    @Test
    fun `thinning the beats leaves every hour's highest where it was`() {
        val base = today * DAY_MILLIS
        val samples = (0 until 720).map { HeartSample(base + it * 60_000L, 55 + (it * 7) % 83) }
        val thin = thinHeart(samples)
        assertEquals(hourlyMax(samples), hourlyMax(thin))
        assertTrue("thinned to ${thin.size} of ${samples.size}", thin.size <= samples.size / 4)
        // Every sample kept is one that was really taken, at its own time.
        assertTrue(thin.all { it in samples })
        assertEquals(thin.sortedBy { it.atMillis }, thin)
    }

    @Test
    fun `an empty day of beats thins to nothing rather than to a zero`() {
        assertEquals(emptyList<HeartSample>(), thinHeart(emptyList()))
    }

    private fun hourlyMax(samples: List<HeartSample>): Map<Long, Int> =
        samples.groupBy { it.atMillis / 3_600_000L }
            .mapValues { (_, run) -> run.maxOf { it.bpm } }

    // --------------------------------------------------------- the trends

    /**
     * Nothing derived is kept, so a restored reading has its trends worked
     * out again -- through the tab's own `healthDataOf`, which is the same
     * reduction a live read runs, so a widget and the tile beside it
     * cannot come to different answers.
     */
    @Test
    fun `a restored reading has the trends a fresh one would`() {
        val data = healthDataOf(roundTrip(kept())!!.health)
        assertEquals(HealthStage.Granted, data.stage)
        assertNotNull(data.stepsTrend)
        assertNotNull(data.restingTrend)
        assertNotNull(data.hrvTrend)
        assertNotNull(data.weightTrend)
        assertNotNull(data.nutritionTrend)
        // Burned and eaten both have five days, so a balance can be had.
        assertNotNull(data.energyTrend)
        assertEquals(1, data.nights.size)
    }

    /** A reading with nothing in it has no trends, and is not an error. */
    @Test
    fun `an empty reading restores to no trends rather than to zeroes`() {
        val data = healthDataOf(HealthSnapshot())
        assertNull(data.stepsTrend)
        assertNull(data.sleepTrend)
        assertEquals(emptyList<Night>(), data.nights)
    }

    // ------------------------------------------- the two silences of a stack

    /**
     * The bug as reported: a stack that had been set up for weeks came
     * back from every reboot saying "tap to choose what this stack holds".
     */
    @Test
    fun `a stack with nothing chosen is the one that asks to be set up`() {
        assertEquals(R.string.widget_stack_empty, stackEmptyText(emptyList()))
    }

    @Test
    fun `a stack whose row survived the reboot never asks again`() {
        val designs = WidgetStore.decode("tile:Steps\u001Fband:Everything")?.designs.orEmpty()
        assertEquals(2, designs.size)
        assertEquals(R.string.widget_stack_quiet, stackEmptyText(designs))
    }

    /**
     * And the two really are different sentences. Asserting this reads as
     * pedantry until somebody points both at the same string resource to
     * save a line, which is where this started.
     */
    @Test
    fun `the two silences do not share a sentence`() {
        assertTrue(R.string.widget_stack_empty != R.string.widget_stack_quiet)
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
