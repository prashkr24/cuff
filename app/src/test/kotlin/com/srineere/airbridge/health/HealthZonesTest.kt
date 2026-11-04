package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class HealthZonesTest {

    private val max = 200 // round numbers: light 100, moderate 120, vigorous 140, peak 170

    private fun samples(vararg pairs: Pair<Long, Int>): List<HeartSample> =
        pairs.map { (minute, bpm) -> HeartSample(minute * 60_000L, bpm) }

    @Test
    fun `resting is in no zone at all`() {
        // The important one: a desk day must not read as an hour of Light.
        assertNull(zoneOf(58, max))
        assertNull(zoneOf(99, max))
        assertEquals(Zone.Light, zoneOf(100, max))
    }

    @Test
    fun `zone edges fall where the fractions say`() {
        assertEquals(Zone.Light, zoneOf(119, max))
        assertEquals(Zone.Moderate, zoneOf(120, max))
        assertEquals(Zone.Moderate, zoneOf(139, max))
        assertEquals(Zone.Vigorous, zoneOf(140, max))
        assertEquals(Zone.Vigorous, zoneOf(169, max))
        assertEquals(Zone.Peak, zoneOf(170, max))
    }

    @Test
    fun `above maximum is still peak`() {
        // A reading over the assumed maximum means the assumption is wrong,
        // not that the zone is undefined.
        assertEquals(Zone.Peak, zoneOf(210, max))
    }

    @Test
    fun `a single reading buys no time`() {
        assertEquals(emptyMap<Zone, Duration>(), timeInZones(samples(0L to 150), max))
    }

    @Test
    fun `time is credited to the earlier reading`() {
        // 0-2 min at 150 (vigorous), 2-3 min at 125 (moderate).
        val result = timeInZones(samples(0L to 150, 2L to 125, 3L to 125), max)
        assertEquals(Duration.ofMinutes(2), result[Zone.Vigorous])
        assertEquals(Duration.ofMinutes(1), result[Zone.Moderate])
    }

    @Test
    fun `a long gap is not credited to anything`() {
        // The band came off for an hour. Stretching one reading across it
        // would invent an hour of exercise.
        val result = timeInZones(samples(0L to 150, 60L to 150, 62L to 150), max)
        assertEquals(Duration.ofMinutes(2), result[Zone.Vigorous])
    }

    @Test
    fun `resting stretches are skipped but do not break the run`() {
        val result = timeInZones(samples(0L to 60, 1L to 150, 3L to 60, 4L to 60), max)
        assertEquals(Duration.ofMinutes(2), result[Zone.Vigorous])
        assertTrue("resting should buy no zone time", result.keys.none { it == Zone.Light })
    }

    @Test
    fun `readings out of order are sorted first`() {
        val jumbled = samples(3L to 125, 0L to 150, 2L to 150)
        val ordered = samples(0L to 150, 2L to 150, 3L to 125)
        // Sorted, that is three minutes at 150 and a trailing 125 that buys
        // nothing, because the last reading has no reading after it.
        assertEquals(timeInZones(ordered, max), timeInZones(jumbled, max))
        assertEquals(Duration.ofMinutes(3), timeInZones(jumbled, max)[Zone.Vigorous])
        assertNull(timeInZones(jumbled, max)[Zone.Moderate])
    }

    @Test
    fun `duplicate timestamps add nothing`() {
        val result = timeInZones(samples(0L to 150, 0L to 150, 1L to 150), max)
        assertEquals(Duration.ofMinutes(1), result[Zone.Vigorous])
    }

    @Test
    fun `summary reports the spread and the count`() {
        val summary = summarise(samples(0L to 60, 1L to 150, 2L to 90))!!
        assertEquals(60, summary.lowest)
        assertEquals(150, summary.highest)
        assertEquals(100, summary.average)
        assertEquals(3, summary.count)
    }

    @Test
    fun `no samples means no summary`() {
        assertNull(summarise(emptyList()))
    }
}
