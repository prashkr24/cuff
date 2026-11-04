package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

class SleepScoreTest {

    private val zone: ZoneId = ZoneOffset.UTC

    /**
     * A night that went to bed at [bedHour] and slept [hours], with stages in
     * the proportions a scored night needs.
     */
    private fun night(
        day: Long,
        hours: Double = 7.0,
        bedHour: Double = 23.0,
        deepShare: Double = 0.18,
        remShare: Double = 0.22,
        awakeMinutes: Long = 20,
        wakes: Int = 1,
        staged: Boolean = true,
    ): Night {
        val date = LocalDate.ofEpochDay(day)
        val start = date.minusDays(1)
            .atTime(LocalTime.ofSecondOfDay((bedHour * 3600).toLong()))
            .toInstant(ZoneOffset.UTC)
        val asleep = Duration.ofMinutes((hours * 60).toLong())
        val awake = Duration.ofMinutes(awakeMinutes)
        val end = start.plus(asleep).plus(awake)
        val deep = Duration.ofMinutes((asleep.toMinutes() * deepShare).toLong())
        val rem = Duration.ofMinutes((asleep.toMinutes() * remShare).toLong())
        val light = asleep.minus(deep).minus(rem)
        val segments = if (!staged) emptyList() else buildList {
            var at = start
            add(SleepSegment(SleepStage.Light, at, at.plus(light).also { at = it }))
            repeat(wakes) {
                add(SleepSegment(SleepStage.Awake, at, at.plusSeconds(300).also { at = it }))
                add(SleepSegment(SleepStage.Deep, at, at.plus(deep.dividedBy(wakes.toLong().coerceAtLeast(1))).also { at = it }))
            }
            add(SleepSegment(SleepStage.Rem, at, at.plus(rem).also { at = it }))
            add(SleepSegment(SleepStage.Awake, at, end))
        }
        return Night(
            day = day,
            start = start,
            end = end,
            asleep = asleep,
            awake = awake,
            light = if (staged) light else Duration.ZERO,
            deep = if (staged) deep else Duration.ZERO,
            rem = if (staged) rem else Duration.ZERO,
            segments = segments,
        )
    }

    private fun history(count: Int, from: Long = 100L) = (0 until count).map { night(from + it) }

    // ------------------------------------------------------------ components

    @Test
    fun `a night like all the others scores well on every part`() {
        val past = history(10)
        val score = sleepScoreOf(night(200), past, zone)
        assertEquals(4, score.parts.size)
        assertTrue("got ${score.total}", (score.total ?: 0) >= 75)
    }

    @Test
    fun `a short night loses duration without losing the rest`() {
        val past = history(10)
        val short = sleepScoreOf(night(200, hours = 3.5), past, zone)
        val normal = sleepScoreOf(night(200), past, zone)
        assertTrue(short.duration!! < normal.duration!!)
        // Stages is a share of the night, so a shorter night of the same
        // shape scores about the same there -- "about", because stages are
        // whole minutes and a short night rounds differently. Consistency is
        // left out on purpose: sleeping three hours less does move the
        // middle of your night.
        assertTrue(
            "stages ${short.stages} against ${normal.stages}",
            abs(short.stages!! - normal.stages!!) <= 5,
        )
    }

    @Test
    fun `a longer night than usual is not punished for it`() {
        val past = history(10)
        assertEquals(100, sleepScoreOf(night(200, hours = 9.0), past, zone).duration)
    }

    @Test
    fun `going to bed hours late costs consistency`() {
        val past = history(10)
        val late = sleepScoreOf(night(200, bedHour = 3.0), past, zone)
        assertTrue("got ${late.consistency}", late.consistency!! < 50)
    }

    @Test
    fun `a midnight bedtime is not three hours from eleven`() {
        // The clock wraps: 23:30 and 00:30 are an hour apart, and a plain
        // average of the two would put the usual middle at midday.
        val past = (0 until 10).map { night(100L + it, bedHour = 23.5) }
        val score = sleepScoreOf(night(200, bedHour = 0.5), past, zone)
        assertTrue("got ${score.consistency}", score.consistency!! >= 80)
    }

    @Test
    fun `waking repeatedly costs interruptions`() {
        val past = history(10)
        val broken = sleepScoreOf(night(200, awakeMinutes = 90, wakes = 7), past, zone)
        val settled = sleepScoreOf(night(200, awakeMinutes = 10, wakes = 1), past, zone)
        assertTrue(broken.interruptions!! < settled.interruptions!!)
        assertEquals(100, settled.interruptions)
    }

    @Test
    fun `losing deep and REM costs stages`() {
        val past = history(10)
        val shallow = sleepScoreOf(night(200, deepShare = 0.04, remShare = 0.06), past, zone)
        assertTrue("got ${shallow.stages}", shallow.stages!! < 60)
    }

    // -------------------------------------------------------------- absences

    @Test
    fun `a night without stages scores on three parts and says so`() {
        val past = history(10)
        val score = sleepScoreOf(night(200, staged = false), past, zone)
        assertNull(score.stages)
        assertEquals(3, score.parts.size)
        assertEquals("3 parts of four", score.basis)
        assertNotNull(score.total)
    }

    @Test
    fun `too few nights leaves everything personal unmeasured`() {
        val score = sleepScoreOf(night(200), history(2), zone)
        assertNull(score.duration)
        assertNull(score.stages)
        assertNull(score.consistency)
        // Interruptions needs no history: it is a fact about the one night.
        assertNotNull(score.interruptions)
        assertEquals("one part of four", score.basis)
    }

    @Test
    fun `a night in bed for no time at all scores nothing rather than crashing`() {
        val instant = Instant.ofEpochSecond(1_700_000_000)
        val empty = Night(
            day = 200,
            start = instant,
            end = instant,
            asleep = Duration.ZERO,
            awake = Duration.ZERO,
            light = Duration.ZERO,
            deep = Duration.ZERO,
            rem = Duration.ZERO,
        )
        val score = sleepScoreOf(empty, emptyList(), zone)
        assertNull(score.total)
        assertEquals("nothing measurable", score.basis)
    }

    @Test
    fun `the night being scored is not part of its own normal`() {
        // Ten identical nights plus one short one: the short night must be
        // measured against the ten, not against a set it has dragged down.
        val past = history(10)
        val withItself = sleepScoreOf(night(200, hours = 3.5), past + night(200, hours = 3.5), zone)
        val without = sleepScoreOf(night(200, hours = 3.5), past, zone)
        assertEquals(without.duration, withItself.duration)
    }

    // ---------------------------------------------------------------- totals

    @Test
    fun `the total is the mean of what could be measured`() {
        val score = SleepScore(duration = 100, stages = 60, consistency = 80, interruptions = 40)
        assertEquals(70, score.total)
        assertEquals("all four parts", score.basis)
    }

    @Test
    fun `a wake at either end is going to bed and getting up`() {
        val one = night(200, wakes = 1)
        // The generator ends every night with an awake segment, and starts
        // the wake loop after the first light stretch, so one real wake.
        assertEquals(1, one.wakes)
    }
}
