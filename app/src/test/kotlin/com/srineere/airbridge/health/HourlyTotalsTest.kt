package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The hourly read, on the two days a year it used to get wrong.
 *
 * Deliberately not in the zone this app was written in. IST has never had a
 * clock change, so every fault below passes on the author's phone and fails
 * on somebody else's -- which is exactly how the old `Duration.ofDays(1)`
 * and elapsed-hour index survived being looked at.
 */
class HourlyTotalsTest {

    private val newYork = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")

    /**
     * The buckets Health Connect hands back for a day sliced by the hour,
     * each worth 1.0, so a slot's value is the number of hours in it.
     */
    private fun everyHour(day: LocalDate, zone: ZoneId): List<Pair<Instant, Double>> {
        val (start, end) = localDay(day, zone)
        val buckets = mutableListOf<Pair<Instant, Double>>()
        var at = start
        while (at < end) {
            buckets += at to 1.0
            at = at.plus(Duration.ofHours(1))
        }
        return buckets
    }

    // ------------------------------------------------------- an ordinary day

    @Test
    fun `an ordinary day is twenty-four hours in twenty-four slots`() {
        val day = LocalDate.of(2026, 6, 10)
        val (start, end) = localDay(day, newYork)
        assertEquals(24L, Duration.between(start, end).toHours())
        assertEquals(List(24) { 1.0 }, byLocalHour(everyHour(day, newYork), newYork))
    }

    @Test
    fun `a day nothing was written on is zeroes, not an empty list`() {
        // The chart these feed is a running total, and a gap in the middle
        // of one means nothing.
        assertEquals(List(24) { 0.0 }, byLocalHour(emptyList(), newYork))
    }

    // ---------------------------------------------------------- spring forward

    @Test
    fun `the day the clocks go forward is twenty-three hours long`() {
        // 8 March 2026, 02:00 EST becomes 03:00 EDT. Asking for a fixed
        // twenty-four hours from midnight ran an hour into the next day.
        val (start, end) = localDay(LocalDate.of(2026, 3, 8), newYork)
        assertEquals(23L, Duration.between(start, end).toHours())
    }

    @Test
    fun `the hour that never happened is empty and the rest are in place`() {
        val day = LocalDate.of(2026, 3, 8)
        val hours = byLocalHour(everyHour(day, newYork), newYork)

        assertEquals(24, hours.size)
        assertEquals(1.0, hours[1], 0.0)
        // There was no 02:00 on this day, so there is nothing in its slot.
        assertEquals(0.0, hours[2], 0.0)
        assertEquals(1.0, hours[3], 0.0)
        // And the last slot holds tonight's eleven o'clock rather than
        // tomorrow's midnight, which is what the old range read into it.
        assertEquals(1.0, hours[23], 0.0)
        assertEquals(23.0, hours.sum(), 0.0)
    }

    @Test
    fun `counting elapsed hours is what put the afternoon one slot early`() {
        // The old arithmetic, kept beside the new so the difference is
        // visible rather than argued about: the fourth bucket of this day
        // is the 04:00 hour, and it is three hours after midnight.
        val day = LocalDate.of(2026, 3, 8)
        val (start, _) = localDay(day, newYork)
        val fourth = everyHour(day, newYork)[3].first

        assertEquals(4, fourth.atZone(newYork).hour)
        assertEquals(3L, Duration.between(start, fourth).toHours())
    }

    // ----------------------------------------------------------------- autumn

    @Test
    fun `the day the clocks go back is twenty-five hours long`() {
        // 1 November 2026, 02:00 EDT becomes 01:00 EST. Reading twenty-four
        // hours from midnight never saw the last local hour of this day.
        val (start, end) = localDay(LocalDate.of(2026, 11, 1), newYork)
        assertEquals(25L, Duration.between(start, end).toHours())
    }

    @Test
    fun `the hour that happened twice is summed, and the last hour is read`() {
        val day = LocalDate.of(2026, 11, 1)
        val hours = byLocalHour(everyHour(day, newYork), newYork)

        assertEquals(24, hours.size)
        // Both runs of 01:00 are in slot 1. Two hours under one clock
        // reading is the honest answer; dropping either loses a real hour.
        assertEquals(2.0, hours[1], 0.0)
        assertEquals(1.0, hours[0], 0.0)
        assertEquals(1.0, hours[2], 0.0)
        assertEquals(1.0, hours[23], 0.0)
        // Twenty-five hours of walking, all of it accounted for.
        assertEquals(25.0, hours.sum(), 0.0)
    }

    // ------------------------------------------------------------ and elsewhere

    @Test
    fun `London does the same thing at a different hour`() {
        // Not a duplicate of the above: London jumps at 01:00 rather than
        // 02:00, so a fix that happened to hard-code an hour would pass
        // every test before this one and fail here.
        val spring = byLocalHour(everyHour(LocalDate.of(2026, 3, 29), london), london)
        assertEquals(0.0, spring[1], 0.0)
        assertEquals(1.0, spring[0], 0.0)
        assertEquals(1.0, spring[2], 0.0)
        assertEquals(23.0, spring.sum(), 0.0)

        val autumn = byLocalHour(everyHour(LocalDate.of(2026, 10, 25), london), london)
        assertEquals(2.0, autumn[1], 0.0)
        assertEquals(1.0, autumn[23], 0.0)
        assertEquals(25.0, autumn.sum(), 0.0)
    }
}
