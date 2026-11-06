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

    @Test fun `readiness leads the headline, and its absence does not silence it`() {
        val slept = HealthSnapshot(
            nights = listOf(
                Night(
                    day = java.time.LocalDate.now().toEpochDay(),
                    start = java.time.Instant.EPOCH,
                    end = java.time.Instant.EPOCH.plus(java.time.Duration.ofMinutes(388)),
                    asleep = java.time.Duration.ofMinutes(388),
                    awake = java.time.Duration.ZERO,
                    light = java.time.Duration.ofMinutes(388),
                    deep = java.time.Duration.ZERO,
                    rem = java.time.Duration.ZERO,
                ),
            ),
        )
        // With a figure, the headline carries it.
        val scored = morningReport(
            slept,
            Goals(),
            Readiness(score = 68, trend = null, parts = emptyList(), days = 60),
        )
        assertEquals("You slept 6h 28m, and readiness is 68", scored.headline)

        // Calibrating is not a reason to say nothing: the phrase the report
        // carried before readiness existed is still true.
        val calibrating = morningReport(
            slept,
            Goals(),
            Readiness(score = null, trend = null, parts = emptyList(), days = 9),
        )
        assertEquals("You slept 6h 28m, and no morning readings yet", calibrating.headline)
    }

    @Test fun `a report with nothing in it is not worth waking anyone for`() {
        val empty = morningReport(HealthSnapshot(), Goals())
        assertEquals(false, empty.worthSending)
        // And it still says something rather than throwing.
        assertEquals("No night was recorded, and no morning readings yet", empty.headline)
    }

    @Test fun `a night that is not last night is not called last night`() {
        // With the band two days out of sync, the newest night on file is
        // the night before last. The report took it anyway -- the retry
        // gives up at eleven and sends what it has -- so a lock screen read
        // "You slept 6h 28m" about a night its reader had already lived two
        // days past. HealthData.lastNight makes the same check; this did not.
        val twoDaysAgo = java.time.LocalDate.now().toEpochDay() - 2
        val stale = HealthSnapshot(
            nights = listOf(
                Night(
                    day = twoDaysAgo,
                    start = java.time.Instant.EPOCH,
                    end = java.time.Instant.EPOCH.plus(java.time.Duration.ofMinutes(388)),
                    asleep = java.time.Duration.ofMinutes(388),
                    awake = java.time.Duration.ZERO,
                    light = java.time.Duration.ofMinutes(388),
                    deep = java.time.Duration.ZERO,
                    rem = java.time.Duration.ZERO,
                ),
            ),
        )
        val report = morningReport(stale, Goals())
        assertEquals(null, report.night)
        assertEquals(null, report.score)
        assertEquals("No night was recorded, and no morning readings yet", report.headline)
        // Nothing else to carry, so there is nothing to wake anyone for.
        assertEquals(false, report.worthSending)
    }
}
