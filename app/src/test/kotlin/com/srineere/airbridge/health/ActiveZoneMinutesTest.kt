package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveZoneMinutesTest {

    @Test
    fun `karvonen HRR zones weight moderate 1x and cardio-peak 2x`() {
        // Age 40 -> HRmax = 180; RHR = 60 -> HRR = 120
        // Moderate >= 60 + 0.40*120 = 108
        // Cardio   >= 60 + 0.60*120 = 132
        // Peak     >= 60 + 0.85*120 = 162
        val t0 = 1_700_000_000_000L
        val samples = listOf(
            HeartSample(t0, 75), // below moderate -> 0
            HeartSample(t0 + 60_000L * 2, 115), // moderate for 3 min
            HeartSample(t0 + 60_000L * 5, 140), // cardio for 4 min
            HeartSample(t0 + 60_000L * 9, 170), // peak for 2 min
            HeartSample(t0 + 60_000L * 11, 80), // end marker
        )

        val summary = activeZonesFromSamples(
            day = 20_000L,
            samples = samples,
            age = 40,
            restingBpm = 60.0,
        )
        assertEquals(3.0, summary.moderateMin, 1e-6)
        assertEquals(4.0, summary.cardioMin, 1e-6)
        assertEquals(2.0, summary.peakMin, 1e-6)
        assertEquals(6.0, summary.vigorousCardioMin, 1e-6)
        // AZM = 3 + 2*(4 + 2) = 15
        assertEquals(15.0, summary.azm, 1e-6)
    }

    @Test
    fun `gaps wider than 5 minutes are dropped rather than credited`() {
        val t0 = 1_700_000_000_000L
        val samples = listOf(
            HeartSample(t0, 150), // cardio, followed by 30-min off-wrist gap -> dropped
            HeartSample(t0 + 60_000L * 30, 150), // cardio for 2 min
            HeartSample(t0 + 60_000L * 32, 70),
        )
        val summary = activeZonesFromSamples(day = 20_000L, samples = samples, age = 40, restingBpm = 60.0)
        assertEquals(2.0, summary.cardioMin, 1e-6)
        assertEquals(4.0, summary.azm, 1e-6)
    }

    @Test
    fun `store json codec round-trips cleanly and fails closed on garbage`() {
        val input = mapOf(
            20_001L to DailyZoneSummary(20_001L, 12.5, 8.0, 1.5, 1_400, ageUsed = 34, rhrUsed = 58),
            20_002L to DailyZoneSummary(20_002L, 0.0, 0.0, 0.0, 0, ageUsed = 34, rhrUsed = 58),
        )
        val encoded = ActiveZoneStore.encode(input)
        val decoded = ActiveZoneStore.decode(encoded)
        assertEquals(2, decoded.size)
        assertEquals(12.5, decoded[20_001L]!!.moderateMin, 1e-6)
        assertEquals(8.0, decoded[20_001L]!!.cardioMin, 1e-6)
        assertEquals(1.5, decoded[20_001L]!!.peakMin, 1e-6)
        assertEquals(34, decoded[20_001L]!!.ageUsed)
        assertTrue(ActiveZoneStore.decode("not-json").isEmpty())
    }
}
