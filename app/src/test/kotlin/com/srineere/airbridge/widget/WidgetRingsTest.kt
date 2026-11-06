package com.srineere.airbridge.widget

import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.HealthStage
import com.srineere.airbridge.ui.sampleGoals
import com.srineere.airbridge.ui.sampleHealthData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WidgetRingsTest {

    private val goals = Goals(steps = 8_000, sleepMinutes = 420, activeMinutes = 150)

    /** A session that began today, so it lands inside this week. */
    private fun session(minutes: Long) = com.srineere.airbridge.health.Session(
        start = Instant.now().minus(Duration.ofMinutes(minutes)),
        end = Instant.now(),
        title = null,
        exerciseType = 0,
    )

    private fun night(minutes: Long) = Night(
        // Today, because "last night" now means the night that ended this
        // morning and these tests are about which rings appear, not about
        // whether a night from 1970 still counts as last night.
        day = java.time.LocalDate.now().toEpochDay(),
        start = Instant.EPOCH,
        end = Instant.EPOCH.plus(Duration.ofMinutes(minutes)),
        asleep = Duration.ofMinutes(minutes),
        awake = Duration.ZERO,
        light = Duration.ofMinutes(minutes),
        deep = Duration.ZERO,
        rem = Duration.ZERO,
    )

    private fun data(
        steps: Long? = 4_000,
        asleep: Long? = 420,
        burnedToday: Double? = 2_000.0,
        burnedDaily: List<Double> = listOf(1_800.0, 2_200.0, 2_000.0, 1_900.0, 5_000.0),
        active: Long? = 75,
    ) = HealthData(
        stage = HealthStage.Granted,
        snapshot = HealthSnapshot(
            stepsToday = steps,
            burnedToday = burnedToday,
            burnedDaily = burnedDaily.mapIndexed { index, value -> Reading(index.toLong(), value) },
            nights = asleep?.let { listOf(night(it)) } ?: emptyList(),
            sessions = active?.let { listOf(session(it)) } ?: emptyList(),
        ),
        nights = asleep?.let { listOf(night(it)) } ?: emptyList(),
    )

    // -------------------------------------------------------- which rings

    /**
     * Steps, active minutes and sleep -- not burn.
     *
     * Burn was measured against its own fortnight average, a denominator
     * this app invented; an arc that closes needs a goal the person set.
     * Burn still has its figure on the Summary face and its dial on Three
     * dials, where nothing has to be a fraction of anything.
     */
    @Test
    fun `three rings when all three have something behind them`() {
        val rings = ringsOf(data(), goals)
        assertEquals(listOf("Steps", "Active", "Sleep"), rings.map { it.label })
        assertEquals(
            listOf(TileColour.Steps, TileColour.Activity, TileColour.Sleep),
            rings.map { it.colour },
        )
    }

    /** Each arc says which window it is about, because they differ. */
    @Test
    fun `every ring names its own window`() {
        assertEquals(
            listOf("today", "this week", "last night"),
            ringsOf(data(), goals).map { it.note },
        )
    }

    @Test
    fun `a night nobody recorded is no ring, not an empty one`() {
        val rings = ringsOf(data(asleep = null), goals)
        assertEquals(listOf("Steps", "Active"), rings.map { it.label })
    }

    @Test
    fun `no session logged is no active ring`() {
        val rings = ringsOf(data(active = null), goals)
        assertEquals(listOf("Steps", "Sleep"), rings.map { it.label })
    }

    @Test
    fun `a goal of zero drops its ring rather than dividing by it`() {
        val rings = ringsOf(data(), goals.copy(steps = 0))
        assertTrue(rings.none { it.label == "Steps" })
    }

    @Test
    fun `nothing at all gives no rings`() {
        val bare = ringsOf(
            data(
                steps = null,
                asleep = null,
                burnedToday = null,
                burnedDaily = emptyList(),
                active = null,
            ),
            goals.copy(steps = 0, sleepMinutes = 0),
        )
        assertTrue(bare.isEmpty())
    }

    // ------------------------------------------------------ how far round

    @Test
    fun `half a goal is half a ring`() {
        val steps = ringsOf(data(steps = 4_000), goals).first { it.label == "Steps" }
        assertEquals(0.5f, steps.fraction, 0.001f)
    }

    @Test
    fun `beating a goal closes the ring and does not keep going`() {
        val steps = ringsOf(data(steps = 24_000), goals).first { it.label == "Steps" }
        assertEquals(1f, steps.fraction, 0.001f)
    }

    @Test
    fun `sleep is measured against the stored goal`() {
        val sleep = ringsOf(data(asleep = 210), goals).first { it.label == "Sleep" }
        assertEquals(0.5f, sleep.fraction, 0.001f)
    }

    // -------------------------------------------------------- a usual day

    @Test
    fun `today is left out of the average it is measured against`() {
        // Four days of 2,000 behind a today of 6,000. Including today would
        // drag the usual figure up to 2,800 and make a big day look ordinary.
        val health = data(
            burnedToday = 6_000.0,
            burnedDaily = listOf(2_000.0, 2_000.0, 2_000.0, 2_000.0, 6_000.0),
        )
        assertEquals(2_000.0, usualBurn(health)!!, 0.001)
    }

    @Test
    fun `fewer than three days behind it is no usual day at all`() {
        assertNull(usualBurn(data(burnedDaily = listOf(2_000.0, 2_100.0))))
    }

    @Test
    fun `a fortnight is the longest window the burn ring looks back over`() {
        val many = List(40) { 1_000.0 } + List(14) { 3_000.0 } + listOf(0.0)
        // The last entry is today and is dropped; the fourteen before it are
        // all 3,000, so the older thousands must not reach the average.
        assertEquals(3_000.0, usualBurn(data(burnedDaily = many))!!, 0.001)
    }

    // ------------------------------------------------------- the catalogue

    @Test
    fun `rings are in the catalogue and their ids round-trip`() {
        RingFace.entries.forEach { face ->
            val design = WidgetDesign.OfRings(face)
            assertTrue(design in WidgetCatalogue.all)
            assertEquals(design, WidgetCatalogue.byId(design.id))
        }
    }

    @Test
    fun `the rings family is no longer listed as unbuilt`() {
        assertTrue(WidgetFamily.Arcs !in WidgetCatalogue.notBuilt)
        assertTrue(WidgetCatalogue.families().any { it.first == WidgetFamily.Arcs })
    }

    /**
     * The sample is what the catalogue draws, and a catalogue has to show
     * the mechanism. Goals level with the sample day closed every ring and
     * every progress bar in every preview, which looks like a decoration
     * rather than a chart.
     */
    @Test
    fun `the sample day leaves its goal rings visibly open`() {
        val rings = ringsOf(sampleHealthData(), sampleGoals)
        val byLabel = rings.associateBy { it.label }
        listOf("Steps", "Sleep").forEach { label ->
            val fraction = byLabel.getValue(label).fraction
            assertTrue("$label is $fraction, which draws as a closed circle", fraction < 0.95f)
            assertTrue("$label is $fraction, which draws as an empty one", fraction > 0.3f)
        }
    }

    @Test
    fun `the arcs face refuses a strip, where three rings would be three dots`() {
        val arcs = WidgetDesign.OfRings(RingFace.Arcs)
        assertTrue(arcs.fits(WidgetSize.Square))
        assertTrue(!arcs.fits(WidgetSize.Strip))
        assertTrue(!arcs.fits(WidgetSize.Row))
    }
}
