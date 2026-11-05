package com.srineere.airbridge.widget

import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.ui.sampleGoals
import com.srineere.airbridge.ui.sampleHealthData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTypeTest {

    private val snapshot = WidgetSnapshot(
        health = sampleHealthData(),
        band = BandFacts(
            battery = 72,
            batteryAt = 1_000_000L,
            relaying = true,
            quietEnabled = true,
            quietStartMinute = 22 * 60,
            quietEndMinute = 7 * 60,
        ),
        goals = sampleGoals,
    )
    private val now = 2_000_000L

    // ------------------------------------------------- borrowed, not rebuilt

    @Test
    fun `a type face says exactly what its tile says`() {
        // The whole style is the tile with its card taken away. Two sets of
        // arithmetic for one reading would be two readings eventually.
        val tile = faceFor(TileKind.Steps, snapshot)!!
        val type = typeFaceOf(TypeFace.Steps, snapshot, now)!!
        assertEquals(tile.figure, type.figure)
        assertEquals(tile.sub, type.sub)
    }

    @Test
    fun `a band type face says exactly what its band widget says`() {
        val band = bandFace(BandFace.Battery, snapshot.band, now)
        val type = typeFaceOf(TypeFace.Battery, snapshot, now)!!
        assertEquals(band.figure, type.figure)
        assertEquals("72%", type.figure)
    }

    @Test
    fun `no charge read means no battery face, not a dash dressed as one`() {
        val never = snapshot.copy(band = BandFacts())
        assertNull(typeFaceOf(TypeFace.Battery, never, now))
    }

    @Test
    fun `relaying puts a word where the figure goes`() {
        // Quiet hours off, because the state the band is in depends on the
        // clock: with a 22:00 window this very assertion read "Quiet" at
        // first, which was the arithmetic being right about a test that was
        // not. What is being checked here is that a word fills the place a
        // figure usually holds.
        val awake = snapshot.copy(band = snapshot.band.copy(quietEnabled = false))
        assertEquals("Relaying", typeFaceOf(TypeFace.Relay, awake, now)!!.figure)
        assertEquals("Not relaying", typeFaceOf(
            TypeFace.Relay,
            awake.copy(band = awake.band.copy(relaying = false)),
            now,
        )!!.figure)
    }

    @Test
    fun `inside quiet hours the relay face says so`() {
        // 06:03 local on the day this epoch lands in, which is inside the
        // 22:00 to 07:00 window the sample band keeps.
        val type = typeFaceOf(TypeFace.Relay, snapshot, now)!!
        assertEquals(snapshot.band.quietNow(now), type.figure == "Quiet")
    }

    // --------------------------------------------------------- the catalogue

    @Test
    fun `type faces are in the catalogue and their ids round-trip`() {
        TypeFace.entries.forEach { face ->
            val design = WidgetDesign.OfType(face)
            assertTrue(design in WidgetCatalogue.all)
            assertEquals(design, WidgetCatalogue.byId(design.id))
        }
    }

    @Test
    fun `the type family is no longer listed as unbuilt`() {
        assertTrue(WidgetFamily.Type !in WidgetCatalogue.notBuilt)
    }

    @Test
    fun `type stops at Wide, because one figure at four by four is whitespace`() {
        TypeFace.entries.forEach { face ->
            val design = WidgetDesign.OfType(face)
            assertTrue(face.name, design.fits(WidgetSize.Strip))
            assertTrue(face.name, design.fits(WidgetSize.Wide))
            assertTrue(face.name, !design.fits(WidgetSize.Tall))
        }
    }

    @Test
    fun `every family prefix decodes to its own family and no other`() {
        // Four prefixes now share one decoder, and a widget that was placed
        // as a dial must never come back as a ring.
        WidgetCatalogue.all.forEach { design ->
            assertEquals(design.family, WidgetCatalogue.byId(design.id)!!.family)
        }
        assertNull(WidgetCatalogue.byId("type:Arcs"))
        assertNull(WidgetCatalogue.byId("dials:Steps"))
    }

    @Test
    fun `every design carries a word for the type family to print`() {
        TypeFace.entries.forEach { assertTrue(it.name, it.word.isNotBlank()) }
    }
}
