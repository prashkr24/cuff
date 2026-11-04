package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTrendTest {

    private fun readings(vararg values: Double): List<Reading> =
        values.mapIndexed { index, value -> Reading(day = index.toLong(), value = value) }

    @Test
    fun `too few readings have no trend`() {
        assertNull(trendOf(emptyList()))
        assertNull(trendOf(readings(58.0)))
        assertNull(trendOf(readings(58.0, 57.0)))
    }

    @Test
    fun `three readings are enough`() {
        val trend = trendOf(readings(58.0, 57.0, 56.0))
        assertEquals(57.0, trend!!.average, 0.001)
        assertEquals(56.0, trend.latest, 0.001)
    }

    @Test
    fun `readings out of order are sorted by day`() {
        val jumbled = listOf(Reading(3, 56.0), Reading(1, 60.0), Reading(2, 58.0))
        val trend = trendOf(jumbled)!!
        assertEquals(listOf(60.0, 58.0, 56.0), trend.readings.map { it.value })
        // Latest means the newest day, not the last item handed in.
        assertEquals(56.0, trend.latest, 0.001)
    }

    @Test
    fun `usual band is the middle half`() {
        val trend = trendOf(readings(1.0, 2.0, 3.0, 4.0, 5.0))!!
        assertEquals(2.0, trend.usual.low, 0.001)
        assertEquals(4.0, trend.usual.high, 0.001)
        assertTrue(3.0 in trend.usual)
        assertFalse(5.0 in trend.usual)
    }

    @Test
    fun `quantile interpolates between readings`() {
        val sorted = listOf(10.0, 20.0, 30.0, 40.0)
        assertEquals(10.0, quantile(sorted, 0.0), 0.001)
        assertEquals(17.5, quantile(sorted, 0.25), 0.001)
        assertEquals(25.0, quantile(sorted, 0.5), 0.001)
        assertEquals(40.0, quantile(sorted, 1.0), 0.001)
    }

    @Test
    fun `one freak night does not swallow the band`() {
        // The point of quartiles over mean-and-SD: a single outlier moves the
        // average but must not widen what counts as usual.
        val steady = readings(56.0, 57.0, 58.0, 57.0, 56.0, 58.0, 57.0, 56.0)
        val withSpike = steady + Reading(day = 8, value = 120.0)
        val a = trendOf(steady)!!
        val b = trendOf(withSpike)!!
        assertEquals(a.usual.low, b.usual.low, 1.0)
        assertEquals(a.usual.high, b.usual.high, 1.0)
        assertTrue("the spike should still show as the highest", b.highest > 100.0)
    }

    @Test
    fun `change compares thirds rather than endpoints`() {
        // Falls from 60 to 54, but the very first reading is an outlier low.
        val values = readings(50.0, 60.0, 60.0, 59.0, 58.0, 57.0, 55.0, 54.0, 54.0)
        val trend = trendOf(values)!!
        // First third averages 56.67, last third 54.33, so a modest fall --
        // not the +4 that comparing first point to last would have given.
        assertTrue("expected a fall, got ${trend.change}", trend.change < 0)
        assertEquals(-2.33, trend.change, 0.01)
    }

    @Test
    fun `recent window is the tail and is compared with the whole`() {
        val values = readings(60.0, 60.0, 60.0, 60.0, 50.0, 50.0)
        val trend = trendOf(values, recentCount = 2)!!
        assertEquals(50.0, trend.recent, 0.001)
        assertEquals(56.667, trend.average, 0.001)
        assertEquals(-6.667, trend.versusAverage, 0.001)
    }

    @Test
    fun `recent window longer than the data uses everything`() {
        val trend = trendOf(readings(10.0, 20.0, 30.0), recentCount = 50)!!
        assertEquals(20.0, trend.recent, 0.001)
        assertEquals(0.0, trend.versusAverage, 0.001)
    }

    @Test
    fun `flat readings have no spread and nothing has moved`() {
        val trend = trendOf(readings(42.0, 42.0, 42.0, 42.0))!!
        assertEquals(0.0, trend.smallestWorthwhileChange, 0.001)
        assertEquals(0.0, trend.versusAverage, 0.001)
        // Exactly zero movement against a zero threshold is not movement.
        assertFalse(trend.recentHasMoved)
        assertTrue(trend.latestIsUsual)
    }

    @Test
    fun `a move counts only once it clears half a standard deviation`() {
        // Noisy but level: the last reading is low, the recent mean is not.
        val noisy = readings(40.0, 44.0, 39.0, 45.0, 41.0, 43.0, 40.0, 44.0, 42.0)
        val trend = trendOf(noisy, recentCount = 3)!!
        assertFalse("ordinary scatter is not a move", trend.recentHasMoved)

        // A sustained drop of the same size as the scatter, repeated, is.
        val dropped = readings(40.0, 44.0, 39.0, 45.0, 41.0, 43.0, 30.0, 31.0, 29.0)
        val after = trendOf(dropped, recentCount = 3)!!
        assertTrue("a sustained fall is a move", after.recentHasMoved)
        assertTrue(after.versusAverage < 0)
    }

    @Test
    fun `standard deviation of a single reading is zero`() {
        assertEquals(0.0, standardDeviation(listOf(5.0), 5.0), 0.001)
    }

    @Test
    fun `coverage counts distinct days and never exceeds the window`() {
        assertEquals(0, coverage(emptyList(), 30))
        assertEquals(3, coverage(readings(1.0, 2.0, 3.0), 30))
        // A duplicate day is one night, however many records carried it.
        val duplicated = listOf(Reading(1, 50.0), Reading(1, 51.0), Reading(2, 52.0))
        assertEquals(2, coverage(duplicated, 30))
        // More readings than days asked for cannot report more than the window.
        assertEquals(2, coverage(readings(1.0, 2.0, 3.0, 4.0), 2))
        assertEquals(0, coverage(readings(1.0), 0))
    }
}
