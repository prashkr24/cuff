package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * A day's sleep is a day's sleep, however many times somebody lay down.
 *
 * Health Connect has no concept of a night: it holds sessions, and an
 * afternoon nap is a session exactly as last night is. For as long as this
 * app mapped one session to one [Night] that cost it in three separate
 * ways, and each of them is pinned here.
 *
 * - A total that left out an hour of afternoon sleep.
 * - "The last 7 nights" quietly becoming the last seven *sessions*, so a
 *   napper saw five days of history in a seven-day chart.
 * - Every screen that keyed nights by day keeping whichever session came
 *   last, which on a napping day is the nap -- so a twenty-minute doze
 *   replaced a seven-hour night in the sleep bank and in readiness.
 *
 * The other half of the design is what must *not* be summed, and those
 * tests matter more: a hypnogram spanning midnight to three the following
 * afternoon, an efficiency diluted by eight waking hours, and a body clock
 * judged by the middle of that span would each be a new lie told in place
 * of the old one.
 */
class SleepMergeTest {

    /** Midnight-ish on an arbitrary day, so nothing depends on when this runs. */
    private val base = Instant.ofEpochMilli(1_762_000_000_000L)

    private fun sleep(
        day: Long,
        startOffset: Duration,
        asleep: Duration,
        awake: Duration = Duration.ofMinutes(20),
        staged: Boolean = true,
    ): Night {
        val start = base.plus(startOffset)
        val end = start.plus(asleep).plus(awake)
        val deep = if (staged) asleep.dividedBy(5) else Duration.ZERO
        val rem = if (staged) asleep.dividedBy(4) else Duration.ZERO
        val light = if (staged) asleep.minus(deep).minus(rem) else Duration.ZERO
        return Night(
            day = day,
            start = start,
            end = end,
            asleep = asleep,
            awake = awake,
            light = light,
            deep = deep,
            rem = rem,
            segments = if (!staged) emptyList() else listOf(
                SleepSegment(SleepStage.Light, start, start.plus(light)),
                SleepSegment(SleepStage.Deep, start.plus(light), start.plus(light).plus(deep)),
                SleepSegment(SleepStage.Rem, start.plus(light).plus(deep), end),
            ),
        )
    }

    /** Last night: in at 23:00 the day before, up at 06:30. */
    private fun night(day: Long = 100L, hours: Long = 7) =
        sleep(day, Duration.ofHours(-1), Duration.ofHours(hours))

    /** A doze after lunch on the same day. */
    private fun nap(day: Long = 100L, minutes: Long = 40) =
        sleep(day, Duration.ofHours(14), Duration.ofMinutes(minutes), awake = Duration.ofMinutes(5))

    // ------------------------------------------------------------ the total

    @Test
    fun `a nap is added to the night rather than counted as a second one`() {
        val folded = oneNightPerDay(listOf(night(), nap()))

        assertEquals(1, folded.size)
        assertEquals(Duration.ofHours(7).plusMinutes(40), folded.single().asleep)
    }

    @Test
    fun `the stages of a nap are added to the day's stages`() {
        val only = oneNightPerDay(listOf(night(), nap())).single()
        val separately = night().let { it.light + it.deep + it.rem } +
            nap().let { it.light + it.deep + it.rem }

        assertEquals(separately, only.light + only.deep + only.rem)
    }

    @Test
    fun `the nap is disclosed, not folded in silently`() {
        val only = oneNightPerDay(listOf(night(), nap(minutes = 40))).single()

        assertEquals(1, only.naps)
        assertEquals(Duration.ofMinutes(40), only.napAsleep)
    }

    @Test
    fun `two naps are two naps`() {
        val only = oneNightPerDay(
            listOf(night(), nap(minutes = 40), sleep(100L, Duration.ofHours(18), Duration.ofMinutes(15))),
        ).single()

        assertEquals(2, only.naps)
        assertEquals(Duration.ofMinutes(55), only.napAsleep)
    }

    @Test
    fun `a day with one sleep is left exactly as it was`() {
        val alone = night()

        assertEquals(alone, oneNightPerDay(listOf(alone)).single())
    }

    // ------------------------------------- what the night still has to mean

    @Test
    fun `the timeline stays the night's, so no hypnogram runs into the afternoon`() {
        val only = oneNightPerDay(listOf(night(), nap())).single()

        assertEquals(night().start, only.start)
        assertEquals(night().end, only.end)
        assertEquals(night().segments, only.segments)
    }

    @Test
    fun `efficiency counts time in bed, not the waking hours between sleeps`() {
        val only = oneNightPerDay(listOf(night(hours = 7), nap(minutes = 40))).single()

        // Nothing here was spent lying awake beyond the 25 minutes the two
        // sessions actually record; the eight hours between them were a day.
        val inBed = Duration.between(night().start, night().end) +
            Duration.between(nap().start, nap().end)
        assertEquals(inBed, only.inBed)
        assertTrue("efficiency was ${only.efficiency}", only.efficiency!! > 0.9)
    }

    @Test
    fun `the midpoint stays in the middle of the night`() {
        val only = oneNightPerDay(listOf(night(), nap())).single()

        assertEquals(night().midpoint, only.midpoint)
    }

    // ------------------------------------------------------------ stability

    @Test
    fun `the order Health Connect hands sessions back does not change the result`() {
        val forwards = oneNightPerDay(listOf(night(), nap())).single()
        val backwards = oneNightPerDay(listOf(nap(), night())).single()

        assertEquals(forwards, backwards)
    }

    @Test
    fun `the longest sleep leads even when it is not the first of the day`() {
        // Awake before dawn, back to bed for six hours: the second sleep is
        // the night, and the hypnogram should be its own.
        val early = sleep(100L, Duration.ofHours(-2), Duration.ofMinutes(45))
        val long = sleep(100L, Duration.ofHours(1), Duration.ofHours(6))

        assertEquals(long.start, oneNightPerDay(listOf(early, long)).single().start)
    }

    @Test
    fun `a night a writer split in two is one night again`() {
        val first = sleep(100L, Duration.ofHours(-1), Duration.ofHours(3))
        val second = sleep(100L, Duration.ofHours(3), Duration.ofHours(4))
        val only = oneNightPerDay(listOf(first, second)).single()

        assertEquals(Duration.ofHours(7), only.asleep)
        // Not a nap: the second half is still part of the night. It is
        // counted as one because nothing here can tell the difference, and
        // saying "includes 1 nap, 3h" would be worse than saying nothing.
        assertEquals(1, only.naps)
    }

    @Test
    fun `days come back oldest first, one each`() {
        val folded = oneNightPerDay(listOf(night(102L), nap(101L), night(101L), night(100L)))

        assertEquals(listOf(100L, 101L, 102L), folded.map { it.day })
    }

    @Test
    fun `nothing in is nothing out`() {
        assertEquals(emptyList<Night>(), oneNightPerDay(emptyList()))
    }
}
