package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Six hours overnight and three in the afternoon is a nine-hour day.
 *
 * Health Connect has no concept of a night. It holds sessions, and an
 * afternoon sleep is a session exactly as the night is. For as long as
 * this app mapped one session to one [Night] that cost it in three ways,
 * and each of them is pinned here:
 *
 * - a total that left the afternoon out;
 * - "the last 7 nights" quietly becoming the last seven *sessions*, so a
 *   napper saw five days of history in a seven-day chart;
 * - every screen keying nights by day -- the sleep bank's `associateBy`,
 *   readiness' `sleepByDay` -- keeping whichever session came last, so
 *   three hours in the afternoon replaced the six-hour night outright.
 *
 * Everything is added and no sleep leads. The other half of the tests are
 * the three figures that must **not** be stretched across the waking
 * hours between two sleeps: time in bed, the count of wakes, and the
 * middle of the sleep. Each of those, done naively, would turn a day with
 * a nap in it into a worse night than the same night without one.
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
                SleepSegment(SleepStage.Rem, start.plus(light).plus(deep), start.plus(asleep)),
                // Getting up. Last of its own sleep, and the segment that
                // a single pass over a merged day would miscount.
                SleepSegment(SleepStage.Awake, start.plus(asleep), end),
            ),
        )
    }

    /** In at 23:00 the day before, up six hours later. */
    private fun overnight(day: Long = 100L, hours: Long = 6) =
        sleep(day, Duration.ofHours(-1), Duration.ofHours(hours))

    /** Three hours in the afternoon of the same day. */
    private fun afternoon(day: Long = 100L, hours: Long = 3) =
        sleep(day, Duration.ofHours(14), Duration.ofHours(hours), awake = Duration.ofMinutes(5))

    // ------------------------------------------------------------ the total

    @Test
    fun `six hours overnight and three in the afternoon is nine hours`() {
        val folded = oneNightPerDay(listOf(overnight(), afternoon()))

        assertEquals(1, folded.size)
        assertEquals(Duration.ofHours(9), folded.single().asleep)
    }

    @Test
    fun `the stages are added up too`() {
        val only = oneNightPerDay(listOf(overnight(), afternoon())).single()
        val separately = overnight().let { it.light + it.deep + it.rem } +
            afternoon().let { it.light + it.deep + it.rem }

        assertEquals(separately, only.light + only.deep + only.rem)
        assertEquals(overnight().awake + afternoon().awake, only.awake)
    }

    @Test
    fun `the day reaches from the first sleep to the last`() {
        val only = oneNightPerDay(listOf(overnight(), afternoon())).single()

        assertEquals(overnight().start, only.start)
        assertEquals(afternoon().end, only.end)
    }

    @Test
    fun `the hypnogram is every sleep of the day, in order`() {
        val only = oneNightPerDay(listOf(afternoon(), overnight())).single()

        assertEquals(overnight().segments + afternoon().segments, only.segments)
    }

    @Test
    fun `a day with one sleep is left exactly as it was`() {
        val alone = overnight()

        assertEquals(alone, oneNightPerDay(listOf(alone)).single())
        assertEquals(1, alone.sleeps)
    }

    // ------------------------------------------- and what it is a sum of

    @Test
    fun `the sleeps it was added up from are kept, in order`() {
        val only = oneNightPerDay(listOf(afternoon(), overnight())).single()

        assertEquals(2, only.sleeps)
        assertEquals(listOf(overnight(), afternoon()), only.parts)
        assertEquals(
            listOf(Duration.ofHours(6), Duration.ofHours(3)),
            only.parts.map { it.asleep },
        )
    }

    @Test
    fun `a part is a sleep in its own right, with its own figures`() {
        val only = oneNightPerDay(listOf(overnight(), afternoon())).single()
        val second = only.parts[1]

        assertEquals(afternoon().start, second.start)
        assertEquals(afternoon().end, second.end)
        assertEquals(Duration.ofHours(3), second.asleep)
        // One level, never a tree: whatever walks these cannot recurse.
        assertTrue(only.parts.all { it.parts.isEmpty() })
    }

    @Test
    fun `three sleeps are three parts`() {
        val only = oneNightPerDay(
            listOf(overnight(), afternoon(), sleep(100L, Duration.ofHours(20), Duration.ofMinutes(25))),
        ).single()

        assertEquals(3, only.sleeps)
        assertEquals(Duration.ofHours(9).plusMinutes(25), only.asleep)
    }

    // ------------------------------- what must not stretch across the gap

    @Test
    fun `the waking afternoon is not counted as time in bed`() {
        val only = oneNightPerDay(listOf(overnight(), afternoon())).single()

        val lyingDown = Duration.between(overnight().start, overnight().end) +
            Duration.between(afternoon().start, afternoon().end)
        assertEquals(lyingDown, only.inBed)
        // The span from first to last is sixteen hours; against that, nine
        // hours of sleep would read as a wretched night.
        assertTrue("efficiency was ${only.efficiency}", only.efficiency!! > 0.9)
    }

    @Test
    fun `getting up in the morning is not a wake in the middle of the day`() {
        // Each sleep here ends on an Awake segment. Merged into one list,
        // the first sleep's last segment is no longer last, and a single
        // pass would charge a wake for having got up.
        val only = oneNightPerDay(listOf(overnight(), afternoon())).single()

        assertEquals(0, only.wakes)
        assertEquals(0, overnight().wakes)
    }

    @Test
    fun `a wake inside a sleep is still counted`() {
        val broken = overnight().let { night ->
            night.copy(
                segments = listOf(
                    SleepSegment(SleepStage.Light, night.start, night.start.plusSeconds(3_600)),
                    SleepSegment(SleepStage.Awake, night.start.plusSeconds(3_600), night.start.plusSeconds(4_200)),
                    SleepSegment(SleepStage.Deep, night.start.plusSeconds(4_200), night.end),
                ),
            )
        }

        assertEquals(1, broken.wakes)
        assertEquals(1, oneNightPerDay(listOf(broken, afternoon())).single().wakes)
    }

    @Test
    fun `the middle of the sleep is weighted, not the middle of the span`() {
        val only = oneNightPerDay(listOf(overnight(), afternoon())).single()

        // Halfway between 23:00 and the end of the afternoon sleep is
        // about seven in the morning, which is nobody's body clock. The
        // weighted middle stays in the small hours, pulled a few hours on
        // by three real hours of afternoon sleep.
        val span = only.start.plusSeconds(Duration.between(only.start, only.end).seconds / 2)
        assertTrue(only.midpoint.isBefore(span))
        assertTrue(only.midpoint.isAfter(overnight().midpoint))
        // Six hours against three, so two parts of the night's middle to
        // one of the afternoon's. Within a minute, since the weighting
        // divides in whole seconds.
        val weighted =
            (overnight().midpoint.epochSecond * 6 + afternoon().midpoint.epochSecond * 3) / 9
        assertTrue(
            "weighted middle was ${only.midpoint}, expected about ${Instant.ofEpochSecond(weighted)}",
            Duration.between(Instant.ofEpochSecond(weighted), only.midpoint).abs() <
                Duration.ofMinutes(1),
        )
    }

    @Test
    fun `one sleep keeps the middle it always had`() {
        val alone = overnight()

        assertEquals(
            alone.start.plusSeconds(Duration.between(alone.start, alone.end).seconds / 2),
            alone.midpoint,
        )
    }

    // ------------------------------------------------------------ stability

    @Test
    fun `the order Health Connect hands sessions back does not change the result`() {
        val forwards = oneNightPerDay(listOf(overnight(), afternoon())).single()
        val backwards = oneNightPerDay(listOf(afternoon(), overnight())).single()

        assertEquals(forwards, backwards)
    }

    @Test
    fun `a sleep wholly inside another does not shorten the day`() {
        // Two writers describing the same night, one of them in part.
        val whole = sleep(100L, Duration.ofHours(-1), Duration.ofHours(7))
        val inside = sleep(100L, Duration.ofHours(1), Duration.ofHours(2))

        assertEquals(whole.end, oneNightPerDay(listOf(whole, inside)).single().end)
    }

    @Test
    fun `a night a writer split in two is one night again`() {
        val first = sleep(100L, Duration.ofHours(-1), Duration.ofHours(3))
        val second = sleep(100L, Duration.ofHours(3), Duration.ofHours(4))

        assertEquals(Duration.ofHours(7), oneNightPerDay(listOf(first, second)).single().asleep)
    }

    @Test
    fun `days come back oldest first, one each`() {
        val folded = oneNightPerDay(
            listOf(overnight(102L), afternoon(101L), overnight(101L), overnight(100L)),
        )

        assertEquals(listOf(100L, 101L, 102L), folded.map { it.day })
        assertEquals(listOf(1, 2, 1), folded.map { it.sleeps })
    }

    @Test
    fun `nothing in is nothing out`() {
        assertEquals(emptyList<Night>(), oneNightPerDay(emptyList()))
    }
}
