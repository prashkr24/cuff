package com.srineere.airbridge.widget

import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.sampleGoals
import com.srineere.airbridge.ui.sampleHealthData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDialsTest {

    private val health = sampleHealthData()

    // --------------------------------------------------- what gets a dial

    @Test
    fun `only the three readings with a ceiling get one`() {
        val dials = dialsOf(health, sampleGoals)
        assertEquals(listOf("Sleep", "Steps", "Burn"), dials.map { it.label })
        assertEquals(
            listOf(TileColour.Sleep, TileColour.Steps, TileColour.Energy),
            dials.map { it.colour },
        )
    }

    @Test
    fun `a goal of zero takes its dial away rather than dividing by it`() {
        val dials = dialsOf(health, sampleGoals.copy(steps = 0))
        assertTrue(dials.none { it.label == "Steps" })
    }

    @Test
    fun `every dial sits between empty and full`() {
        dialsOf(health, sampleGoals).forEach {
            assertTrue("${it.label} is ${it.fraction}", it.fraction in 0f..1f)
        }
    }

    // ----------------------------------------------------- what is inside

    @Test
    fun `the sleep dial holds its score, not a percentage of it`() {
        val sleep = sleepDial(health)!!
        val score = health.sleepScore!!.total!!
        assertEquals("$score", sleep.centre)
        assertEquals(score / 100f, sleep.fraction, 0.001f)
    }

    @Test
    fun `steps and burn hold a percentage, because their own figures do not fit`() {
        val dials = dialsOf(health, sampleGoals)
        listOf("Steps", "Burn").forEach { label ->
            val dial = dials.first { it.label == label }
            val percent = dial.centre.toInt()
            assertTrue("$label reads ${dial.centre}", percent in 0..100)
            assertEquals(dial.fraction, percent / 100f, 0.01f)
        }
    }

    @Test
    fun `no score means no dial, never an empty circle`() {
        // SleepScore refuses to invent one before it has nights enough, and
        // a dial drawn empty would make the claim the arithmetic declined to.
        val bare = health.copy(nights = emptyList())
        assertNull(sleepDial(bare))
        assertTrue(dialsOf(bare, sampleGoals).none { it.label == "Sleep" })
    }

    // ------------------------------------------------------- seven nights

    @Test
    fun `a week is seven nights at most, newest last`() {
        val scores = nightScores(health)
        assertTrue("got ${scores.size}", scores.size in 1..7)
        scores.forEach { assertTrue("$it", it in 0f..100f) }
    }

    @Test
    fun `a night is scored against the whole history, not against its week`() {
        // Scoring each night against only the six beside it would re-base a
        // bad week until every night in it looked ordinary. The last night's
        // score here must match the one the tab shows for the same night.
        val scores = nightScores(health)
        assertEquals(health.sleepScore!!.total!!.toFloat(), scores.last(), 0.001f)
    }

    @Test
    fun `no nights is no week`() {
        assertTrue(nightScores(health.copy(nights = emptyList())).isEmpty())
    }

    // ------------------------------------------------------- the catalogue

    @Test
    fun `dials are in the catalogue and their ids round-trip`() {
        DialFace.entries.forEach { face ->
            val design = WidgetDesign.OfDials(face)
            assertTrue(design in WidgetCatalogue.all)
            assertEquals(design, WidgetCatalogue.byId(design.id))
        }
    }

    @Test
    fun `the dials family is no longer listed as unbuilt`() {
        assertTrue(WidgetFamily.Dials !in WidgetCatalogue.notBuilt)
    }

    @Test
    fun `three dials refuse a strip, where three arcs would be three dots`() {
        assertTrue(WidgetDesign.OfDials(DialFace.Three).fits(WidgetSize.Wide))
        assertTrue(!WidgetDesign.OfDials(DialFace.Three).fits(WidgetSize.Strip))
        // One dial does fit a strip: it is a circle and a number.
        assertTrue(WidgetDesign.OfDials(DialFace.Score).fits(WidgetSize.Strip))
    }

    @Test
    fun `a dials id is not mistaken for a rings one`() {
        assertTrue(WidgetDesign.OfDials(DialFace.Score).id.startsWith("dials:"))
        assertNull(WidgetCatalogue.byId("dials:Arcs"))
        assertNull(WidgetCatalogue.byId("rings:Score"))
    }

    // ------------------------------------------- burned against eaten

    /**
     * The screen's axis, which ends where the lines end.
     *
     * Fixed marks at six, twelve and eighteen put the last label four
     * hours short of the end of the chart on an evening, and left a
     * morning with one mark on it.
     */
    @Test
    fun `the axis ends at the hour it is now, not at a round one`() {
        val day = DayBalance(List(20) { it.toFloat() }, emptyList(), entries = 0, hourNow = 19)
        assertEquals(listOf("00", "04", "08", "12", "16", "19"), day.hourTicks.map { it.second })
        assertEquals(1f, day.hourTicks.last().first, 0.001f)
    }

    @Test
    fun `before one in the morning the axis is a single mark`() {
        assertEquals(listOf("00"), DayBalance(listOf(0f), emptyList(), 0, 0).hourTicks.map { it.second })
    }

    /** Eaten less burned, so the usual day reads as a negative. */
    @Test
    fun `the gap is what the screen puts at the top of the card`() {
        val day = DayBalance(listOf(0f, 1_769f), listOf(0f, 770f), entries = 3, hourNow = 1)
        assertEquals(-999f, day.gap, 0.001f)
        assertEquals(1_769, day.lastBurned)
        assertEquals(770, day.lastEaten)
        assertTrue(day.anything)
    }

    @Test
    fun `a day with nothing read on either line is not drawn`() {
        assertTrue(!DayBalance(listOf(0f, 0f), listOf(0f, 0f), 0, 1).anything)
    }

    @Test
    fun `the burn face takes both sizes it is offered`() {
        val burn = WidgetDesign.OfDials(DialFace.Burn)
        assertTrue(burn.fits(WidgetSize.Wide))
        assertTrue(burn.fits(WidgetSize.Tall))
    }
}
