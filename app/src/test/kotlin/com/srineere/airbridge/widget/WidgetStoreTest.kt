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
        val config = WidgetConfig(steps)
        assertEquals(config, WidgetStore.decode(WidgetStore.encode(config)))
    }

    /** A row is the design's id now, with nothing after it. */
    @Test
    fun `a config is stored as its design and nothing else`() {
        assertEquals(steps.id, WidgetStore.encode(WidgetConfig(steps)))
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

    /**
     * A widget placed by an older build keeps drawing.
     *
     * Those rows carry a pinned face and a tap target after the design.
     * Both are gone, so both are let go: an upgrade costs a widget its pin,
     * never its existence.
     */
    @Test
    fun `a row from the build with a pinned face is read for its design`() {
        assertEquals(steps, WidgetStore.decode("tile:Steps\u001FSquare\u001F1")?.design)
        assertEquals(steps, WidgetStore.decode("tile:Steps\u001F\u001F0")?.design)
        assertEquals(steps, WidgetStore.decode("tile:Steps")?.design)
    }

    // --------------------------------------------------------- which face

    @Test
    fun `a widget draws the face of the cell it is in`() {
        val config = WidgetConfig(steps)
        assertEquals(WidgetSize.Strip, config.face(WidgetSize.Strip))
        assertEquals(WidgetSize.Tall, config.face(WidgetSize.Tall))
    }

    @Test
    fun `a kind that refuses the cell falls back to its largest, never to nothing`() {
        // Activity stops at Wide. Dropped in a 4x4 it draws Wide rather than
        // an empty card.
        val config = WidgetConfig(activity)
        assertEquals(WidgetSize.Wide, config.face(WidgetSize.Tall))
    }

    /** Every widget is auto now: the cell it is in is the only input. */
    @Test
    fun `resizing a widget changes the face it draws`() {
        val config = WidgetConfig(steps)
        assertEquals(WidgetSize.entries.toList(), WidgetSize.entries.map { config.face(it) })
    }

    @Test
    fun `a band face shrunk below what it offers draws the nearest, not the largest`() {
        val config = WidgetConfig(band)
        assertEquals(WidgetSize.Tall, config.face(WidgetSize.Tall))
        // Everything offers Wide and Tall. Squeezed into a strip it draws
        // Wide -- the Tall face in a 2x1 cell is the worse mistake.
        assertEquals(WidgetSize.Wide, config.face(WidgetSize.Strip))
    }

    // ------------------------------------------------------------- stacks

    /** A stack is one row with several ids in it, in the order chosen. */
    @Test
    fun `a stack round-trips, and keeps its order`() {
        val config = WidgetConfig.of(listOf(band, steps, activity))
        val decoded = WidgetStore.decode(WidgetStore.encode(config))
        assertEquals(config, decoded)
        assertEquals(listOf(band, steps, activity), decoded?.designs)
    }

    /** The first card is what a single-card widget would have drawn. */
    @Test
    fun `the first card of a stack is its design`() {
        assertEquals(band, WidgetConfig.of(listOf(band, steps)).design)
    }

    /**
     * A design this build dropped costs the stack that card, not the stack.
     *
     * The single-card rule is the opposite -- an unknown id there leaves
     * nothing to draw, so the widget asks to be set up again. Here there is
     * still something to show.
     */
    @Test
    fun `a stack naming one design this build dropped keeps the rest`() {
        val decoded = WidgetStore.decode("band:Everything\u001Ftile:Readiness\u001Ftile:Steps")
        assertEquals(listOf(band, steps), decoded?.designs)
    }

    @Test
    fun `a row of nothing this build knows is refused whole`() {
        assertNull(WidgetStore.decode("tile:Readiness\u001Fband:Stop"))
    }

    @Test
    fun `each card of a stack draws the face of the cell the stack is in`() {
        // Activity stops at Wide, the band's Everything face offers Wide and
        // Tall: in a 4x4 stack one card falls back and the other does not.
        assertEquals(WidgetSize.Wide, WidgetConfig.faceSizeOf(activity, WidgetSize.Tall))
        assertEquals(WidgetSize.Tall, WidgetConfig.faceSizeOf(band, WidgetSize.Tall))
    }
}
