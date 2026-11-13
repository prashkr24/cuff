package com.srineere.airbridge.widget

import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.health.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCatalogueTest {

    // --------------------------------------------------------------- sizes

    @Test
    fun `every widget size maps to a distinct tile size`() {
        val tiles = WidgetSize.entries.map { it.tile }
        assertEquals(tiles.size, tiles.toSet().size)
        assertEquals(TileSize.entries.toSet(), tiles.toSet())
    }

    @Test
    fun `the nearest cell to a square of 150dp is Square`() {
        assertEquals(WidgetSize.Square, WidgetSize.nearest(150, 150))
    }

    @Test
    fun `a launcher reporting a slightly short wide cell still gets Wide`() {
        assertEquals(WidgetSize.Wide, WidgetSize.nearest(296, 138))
    }

    @Test
    fun `a tall cell is not mistaken for a wide one`() {
        assertEquals(WidgetSize.Tall, WidgetSize.nearest(318, 286))
    }

    // ------------------------------------------------------------ the tiles

    @Test
    fun `a tile offers exactly the sizes its kind does`() {
        // Activity stops at L on the tab, so it must stop at Wide here.
        val activity = WidgetDesign.OfTile(TileKind.Activity)
        assertTrue(activity.fits(WidgetSize.Wide))
        assertFalse(activity.fits(WidgetSize.Tall))

        val steps = WidgetDesign.OfTile(TileKind.Steps)
        assertEquals(WidgetSize.entries.toSet(), steps.sizes)
    }

    @Test
    fun `the five that reach Tall are the five the tab allows`() {
        val tall = WidgetCatalogue.forSize(WidgetSize.Tall)
            .filterIsInstance<WidgetDesign.OfTile>()
            .map { it.kind }
            .toSet()
        assertEquals(TileKind.entries.filter { TileSize.XL in it.sizes }.toSet(), tall)
    }

    @Test
    fun `every kind is offered, including the optional ones`() {
        val kinds = WidgetCatalogue.all.filterIsInstance<WidgetDesign.OfTile>().map { it.kind }
        assertEquals(TileKind.entries.toSet(), kinds.toSet())
    }

    // ----------------------------------------------------------------- ids

    @Test
    fun `every design has a unique id that round-trips`() {
        val ids = WidgetCatalogue.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        WidgetCatalogue.all.forEach { design ->
            assertEquals(design, WidgetCatalogue.byId(design.id))
        }
    }

    @Test
    fun `an id this build does not know decodes to nothing`() {
        // Deliberately absurd. These stood on "tile:Readiness" until the
        // build grew a readiness tile and four tests went red at once: a
        // placeholder that is merely unbuilt is a placeholder with a
        // shelf life.
        assertNull(WidgetCatalogue.byId("tile:Horoscope"))
        assertNull(WidgetCatalogue.byId("band:Stop"))
        assertNull(WidgetCatalogue.byId("rings:Three"))
        assertNull(WidgetCatalogue.byId(""))
        assertNull(WidgetCatalogue.byId(null))
    }

    // ------------------------------------------------------------ grouping

    @Test
    fun `every size has something to offer, and a default that fits it`() {
        WidgetSize.entries.forEach { size ->
            assertTrue(size.name, WidgetCatalogue.forSize(size).isNotEmpty())
            assertTrue(size.name, WidgetCatalogue.default(size).fits(size))
        }
    }

    @Test
    fun `families list in catalogue order and hold only designs that fit`() {
        val families = WidgetCatalogue.families(WidgetSize.Tall)
        // The order, not the membership: which families have something at a
        // given size changes every time one is built, and a test that names
        // them is a test that fails on the good news.
        val shown = families.map { it.first }
        assertEquals(shown.sortedBy { it.ordinal }, shown)
        assertTrue(WidgetFamily.Tiles in shown)
        families.forEach { (family, designs) ->
            designs.forEach {
                assertEquals(family, it.family)
                assertTrue(it.fits(WidgetSize.Tall))
            }
        }
    }

    @Test
    fun `no family is listed empty`() {
        WidgetSize.entries.forEach { size ->
            WidgetCatalogue.families(size).forEach { (_, designs) ->
                assertTrue(designs.isNotEmpty())
            }
        }
    }

    // ------------------------------------------- with the health half off

    @Test
    fun `nothing that needs a reading is offered with health off`() {
        val offered = WidgetCatalogue.offered(healthOn = false)
        assertTrue(offered.none { it.needsHealth() })
        // Said the other way round too, so that a family added without a
        // thought for the gate fails here rather than drawing a blank
        // card on somebody's home screen.
        assertTrue(offered.none { it is WidgetDesign.OfTile })
        assertTrue(offered.none { it is WidgetDesign.OfRings })
        assertTrue(offered.none { it is WidgetDesign.OfDials })
        assertEquals(WidgetCatalogue.all, WidgetCatalogue.offered(healthOn = true))
    }

    @Test
    fun `the band keeps every face it had`() {
        // The whole promise of the gate: a battery, a relay state and a
        // count of buzzes are facts this app holds itself, so none of
        // them goes away with Health Connect.
        val offered = WidgetCatalogue.offered(healthOn = false)
        BandFace.entries.forEach { assertTrue(it.name, WidgetDesign.OfBand(it) in offered) }
        assertTrue(WidgetDesign.OfType(TypeFace.Battery) in offered)
        assertTrue(WidgetDesign.OfType(TypeFace.Relay) in offered)
        // And the two type faces that are a tile with the card taken away
        // do, because the reading underneath is still a reading.
        assertFalse(WidgetDesign.OfType(TypeFace.Steps) in offered)
        assertFalse(WidgetDesign.OfType(TypeFace.Sleep) in offered)
    }

    @Test
    fun `every cell still has something to offer with health off`() {
        // A picker with nothing in it is a launcher drop that cannot be
        // completed, and default() calls first() on this list.
        WidgetSize.entries.forEach { size ->
            val fitting = WidgetCatalogue.forSize(size, healthOn = false)
            assertTrue(size.name, fitting.isNotEmpty())
            assertTrue(size.name, fitting.none { it.needsHealth() })
            val fallback = WidgetCatalogue.default(size, healthOn = false)
            assertTrue(size.name, fallback.fits(size))
            assertFalse(size.name, fallback.needsHealth())
        }
    }

    @Test
    fun `no family is listed empty with health off either`() {
        WidgetSize.entries.forEach { size ->
            WidgetCatalogue.families(size, healthOn = false).forEach { (family, designs) ->
                assertTrue(designs.isNotEmpty())
                assertTrue(family.name, designs.none { it.needsHealth() })
            }
        }
        assertTrue(WidgetCatalogue.families(healthOn = false).none { it.first == WidgetFamily.Tiles })
    }

    // ------------------------------------------------------------ the band

    @Test
    fun `the band faces that need room do not offer a strip`() {
        assertFalse(WidgetDesign.OfBand(BandFace.Everything).fits(WidgetSize.Strip))
        assertTrue(WidgetDesign.OfBand(BandFace.Everything).fits(WidgetSize.Wide))
    }

    @Test
    fun `every band face offers at least one size`() {
        BandFace.entries.forEach { assertTrue(it.name, it.sizes.isNotEmpty()) }
    }

    @Test
    fun `summaries are written for every design`() {
        WidgetCatalogue.all.forEach {
            assertTrue(it.id, it.title.isNotBlank())
            assertTrue(it.id, it.summary.isNotBlank())
        }
    }

    @Test
    fun `tile designs carry their kind's own title`() {
        assertEquals("Resting heart rate", WidgetDesign.OfTile(TileKind.Heart).title)
        assertEquals("Heart rate", WidgetDesign.OfTile(TileKind.Zones).title)
    }

    @Test
    fun `a design decoded by id is the one it was encoded from`() {
        val design = WidgetDesign.OfTile(TileKind.Skin)
        assertNotNull(WidgetCatalogue.byId(design.id))
        assertEquals(TileKind.Skin, (WidgetCatalogue.byId(design.id) as WidgetDesign.OfTile).kind)
    }
}
