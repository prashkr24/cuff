package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MorningMomentTest {

    @Test fun `the next one is later today, or this time tomorrow`() {
        // Later today.
        assertEquals(70L, MorningMoment.untilNext(7 * 60 + 10, LocalTime.of(6, 0)))
        // Already past, so tomorrow: a booking for "right now" would spin.
        assertEquals((24 * 60 - 50).toLong(), MorningMoment.untilNext(7 * 60, LocalTime.of(7, 50)))
        // Exactly now counts as past, for the same reason.
        assertEquals(24 * 60L, MorningMoment.untilNext(7 * 60, LocalTime.of(7, 0)))
    }

    @Test fun `a figure against its band is said in its own units`() {
        val inside = trendOf((1..10).map { Reading(it.toLong(), 50.0 + it % 3) })!!
        assertEquals("inside the band", MorningReport.against(inside))
        assertEquals("no band yet", MorningReport.against(null))
    }

    @Test fun `a report with nothing in it is not worth waking anyone for`() {
        val empty = morningReport(HealthSnapshot(), Goals())
        assertEquals(false, empty.worthSending)
        // And it still says something rather than throwing.
        assertEquals("No night was recorded, and no morning readings yet", empty.headline)
    }
}
