package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.ZoneId

class HealthWriterTest {

    private val london = ZoneId.of("Europe/London")

    // ---------------------------------------------------------- the span

    @Test
    fun `a meal lasts a second, because it may not last none`() {
        // Health Connect rejects a record that starts when it ends, and it
        // rejects it in the constructor -- which took the app down the first
        // time anyone pressed Save.
        val at = Instant.parse("2026-11-04T12:30:00Z")
        val (start, end) = HealthWriter.span(at, london)
        assertTrue(start.isBefore(end))
        assertEquals(Duration.ofSeconds(1), Duration.between(start, end))
    }

    @Test
    fun `the second never crosses midnight`() {
        // 23:59:59 local. A second forward would land the record on the next
        // day and have it counted in two days' totals, so it is logged as
        // the last second of its own day instead.
        val at = Instant.parse("2026-11-04T23:59:59Z")
        val (start, end) = HealthWriter.span(at, ZoneId.of("UTC"))
        assertTrue(start.isBefore(end))
        assertEquals(
            start.atZone(ZoneId.of("UTC")).toLocalDate(),
            end.atZone(ZoneId.of("UTC")).toLocalDate(),
        )
        assertEquals(at, end)
    }

    @Test
    fun `midnight is read in the phone's zone, not UTC`() {
        // 23:59:59 in London is not 23:59:59 in Tokyo, and the day that must
        // not be straddled is the one the person is living in.
        val at = Instant.parse("2026-11-04T23:59:59Z")
        val (start, end) = HealthWriter.span(at, ZoneId.of("Asia/Tokyo"))
        // Already 08:59 the next morning in Tokyo, so the second is free.
        assertEquals(at, start)
        assertEquals(at.plusSeconds(1), end)
    }

    @Test
    fun `the record Health Connect rejected can now be built`() {
        // The exact failure: NutritionRecord validates its own times in the
        // constructor, so a zero-length span threw before the insert was
        // attempted, on the main thread, with nothing catching it. Built
        // here rather than only on a phone, because the thing that broke was
        // argument validation and that is plain Kotlin.
        val at = Instant.parse("2026-11-04T12:30:00Z")
        val (start, end) = HealthWriter.span(at, london)
        val record = NutritionRecord(
            startTime = start,
            startZoneOffset = london.rules.getOffset(start),
            endTime = end,
            endZoneOffset = london.rules.getOffset(start),
            energy = Energy.kilocalories(325.0),
            protein = Mass.grams(5.0),
            mealType = Meal.Lunch.type,
            name = "Cooked rice",
            metadata = Metadata.manualEntry(),
        )
        assertEquals(325.0, record.energy!!.inKilocalories, 0.001)
        assertTrue(record.startTime.isBefore(record.endTime))
    }

    // ------------------------------------------------------ the arithmetic

    @Test
    fun `macros convert at the Atwater factors`() {
        assertEquals(4.0 * 10 + 4.0 * 20 + 9.0 * 5, HealthWriter.macroEnergy(10.0, 20.0, 5.0)!!, 0.001)
        assertEquals(null, HealthWriter.macroEnergy(null, null, null))
    }

    @Test
    fun `a tenth out is rounding, a third out is a different meal`() {
        assertTrue(HealthWriter.agrees(500.0, 520.0))
        assertTrue(HealthWriter.agrees(null, 520.0))
        // Small absolute gaps pass whatever the share.
        assertTrue(HealthWriter.agrees(40.0, 60.0))
        assertTrue(!HealthWriter.agrees(500.0, 900.0))
    }
}
