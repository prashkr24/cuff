package com.srineere.airbridge.widget

import com.srineere.airbridge.health.TileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStoreTest {

    private val steps = WidgetDesign.OfTile(TileKind.Steps)
    private val activity = WidgetDesign.OfTile(TileKind.Activity)
    private val band = WidgetDesign.OfBand(BandFace.Everything)

    // ------------------------------------------------------------ encoding

    @Test
    fun `a config round-trips`() {
        val config = WidgetConfig(steps, pinned = WidgetSize.Square, opensTab = true)
        assertEquals(config, WidgetStore.decode(WidgetStore.encode(config)))
    }

    @Test
    fun `auto is stored as no size, not as a size`() {
        val config = WidgetConfig(steps)
        val decoded = WidgetStore.decode(WidgetStore.encode(config))
        assertNull(decoded?.pinned)
        assertEquals(config, decoded)
    }

    @Test
    fun `nothing stored means nothing chosen`() {
        assertNull(WidgetStore.decode(null))
        assertNull(WidgetStore.decode(""))
    }

    @Test
    fun `a row naming a design this build dropped is refused whole`() {
        assertNull(WidgetStore.decode("tile:Readiness\u001FSquare\u001F1"))
        assertNull(WidgetStore.decode("band:Stop\u001F\u001F0"))
    }

    @Test
    fun `a truncated row keeps what it can and defaults the rest`() {
        // Written by a build before opensTab existed: the design is still
        // good, so the widget keeps drawing rather than asking to be set up.
        val decoded = WidgetStore.decode("tile:Steps")
        assertEquals(steps, decoded?.design)
        assertNull(decoded?.pinned)
        assertEquals(false, decoded?.opensTab)
    }

    @Test
    fun `a size this build no longer has falls back to auto`() {
        val decoded = WidgetStore.decode("tile:Steps\u001FHuge\u001F0")
        assertEquals(steps, decoded?.design)
        assertNull(decoded?.pinned)
    }

    // --------------------------------------------------------- which face

    @Test
    fun `auto draws the face of the cell it is in`() {
        val config = WidgetConfig(steps)
        assertEquals(WidgetSize.Strip, config.face(WidgetSize.Strip))
        assertEquals(WidgetSize.Tall, config.face(WidgetSize.Tall))
    }

    @Test
    fun `a pinned face ignores the cell`() {
        val config = WidgetConfig(steps, pinned = WidgetSize.Square)
        assertEquals(WidgetSize.Square, config.face(WidgetSize.Tall))
        assertEquals(WidgetSize.Square, config.face(WidgetSize.Strip))
    }

    @Test
    fun `a kind that refuses the cell falls back to its largest, never to nothing`() {
        // Activity stops at Wide. Dropped in a 4x4 it draws Wide rather than
        // an empty card.
        val config = WidgetConfig(activity)
        assertEquals(WidgetSize.Wide, config.face(WidgetSize.Tall))
    }

    @Test
    fun `a pinned face the kind refuses falls back the same way`() {
        val config = WidgetConfig(activity, pinned = WidgetSize.Tall)
        assertEquals(WidgetSize.Wide, config.face(WidgetSize.Wide))
    }

    @Test
    fun `a band face shrunk below what it offers draws the nearest, not the largest`() {
        val config = WidgetConfig(band)
        assertEquals(WidgetSize.Tall, config.face(WidgetSize.Tall))
        // Everything offers Wide and Tall. Squeezed into a strip it draws
        // Wide -- the Tall face in a 2x1 cell is the worse mistake.
        assertEquals(WidgetSize.Wide, config.face(WidgetSize.Strip))
    }
}
