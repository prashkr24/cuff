package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {

    private fun t(kind: TileKind, size: TileSize) = Tile(kind, size)

    // ------------------------------------------------------------- packing

    @Test
    fun `three smalls share a row`() {
        val rows = packTiles(
            listOf(
                t(TileKind.Energy, TileSize.S),
                t(TileKind.Activity, TileSize.S),
                t(TileKind.Vitals, TileSize.S),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals(3, rows.first().tiles.size)
    }

    @Test
    fun `two mediums share a row and a third starts the next`() {
        val rows = packTiles(List(3) { t(TileKind.Steps, TileSize.M) })
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].tiles.size)
        assertEquals(1, rows[1].tiles.size)
    }

    @Test
    fun `a large always has a row to itself`() {
        val rows = packTiles(
            listOf(
                t(TileKind.Energy, TileSize.S),
                t(TileKind.Sleep, TileSize.L),
                t(TileKind.Vitals, TileSize.S),
            ),
        )
        assertEquals(3, rows.size)
        assertEquals(TileKind.Sleep, rows[1].tiles.single().kind)
    }

    @Test
    fun `a small and a medium fit together`() {
        // 2 + 3 = 5 of 6, which is allowed to look uneven rather than be
        // rearranged behind the person's back.
        val rows = packTiles(listOf(t(TileKind.Vitals, TileSize.S), t(TileKind.Steps, TileSize.M)))
        assertEquals(1, rows.size)
        assertEquals(2, rows.first().tiles.size)
    }

    @Test
    fun `order is never rearranged to close a gap`() {
        val given = listOf(
            t(TileKind.Steps, TileSize.M),
            t(TileKind.Sleep, TileSize.L),
            t(TileKind.Vitals, TileSize.S),
        )
        val flat = packTiles(given).flatMap { it.tiles }
        assertEquals(given.map { it.kind }, flat.map { it.kind })
    }

    @Test
    fun `no tiles means no rows`() {
        assertTrue(packTiles(emptyList()).isEmpty())
    }

    @Test
    fun `the default layout leaves no gaps`() {
        // The point of the default is that it demonstrates the grid: every
        // row filled exactly, every size in use. A size added later that
        // does not divide into six will fail here, which is the idea.
        val rows = packTiles(defaultTiles)
        rows.forEach { row ->
            assertEquals(TILE_COLUMNS, row.tiles.sumOf { it.size.span })
        }
        assertEquals(
            TileSize.entries.toSet(),
            defaultTiles.map { it.size }.toSet(),
        )
    }

    @Test
    fun `the default layout sets no colours`() {
        // Each tile teaches its own, which is what the edit screen's
        // "give each its own colour back" returns to.
        assertTrue(defaultTiles.all { it.colour == null })
    }

    // -------------------------------------------------------------- sizes

    @Test
    fun `a tile only offers sizes it can fill`() {
        assertFalse(TileSize.XL in TileKind.Activity.sizes)
        assertFalse(TileSize.XL in TileKind.Hrv.sizes)
        assertTrue(TileSize.XL in TileKind.Heart.sizes)
    }

    @Test
    fun `a tile offers only the sizes it can fill`() {
        // The panel lays out whatever is in `sizes`, so what is missing here
        // is what nobody can pick.
        assertTrue(TileSize.W in TileKind.Activity.sizes)
        assertFalse(TileSize.XL in TileKind.Activity.sizes)
        assertEquals(TileSize.entries.toSet(), TileKind.Heart.sizes)
    }

    @Test
    fun `a small and a wide tile fill a row exactly`() {
        // The whole reason W exists: S beside M left a column of dead air
        // that no arrangement could use.
        assertEquals(TILE_COLUMNS, TileSize.S.span + TileSize.W.span)
        val rows = packTiles(
            listOf(
                Tile(TileKind.Steps, TileSize.S),
                Tile(TileKind.Sleep, TileSize.W),
                Tile(TileKind.Hrv, TileSize.S),
                Tile(TileKind.Vitals, TileSize.M),
            ),
        )
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].tiles.size)
        // The pair that does not fill a row still shares one, and still
        // leaves its gap rather than being reordered.
        assertEquals(2, rows[1].tiles.size)
    }

    @Test
    fun `the sizes that share a row are one row high`() {
        // A wide tile that stood taller than the small one beside it would
        // make the grid ragged.
        assertEquals(TileSize.S.rows, TileSize.W.rows)
    }

    @Test
    fun `an impossible size is clamped to the widest the tile offers`() {
        assertEquals(TileSize.L, TileKind.Activity.clamp(TileSize.XL))
        assertEquals(TileSize.M, TileKind.Activity.clamp(TileSize.M))
    }

    // ------------------------------------------------------------ storage

    @Test
    fun `a layout survives a round trip`() {
        val tiles = listOf(
            Tile(TileKind.Heart, TileSize.XL, TileColour.Accent),
            Tile(TileKind.Steps, TileSize.M, null),
        )
        assertEquals(tiles, decodeTiles(encodeTiles(tiles)))
    }

    @Test
    fun `nothing stored means the default layout`() {
        assertEquals(defaultTiles, decodeTiles(null))
        assertEquals(defaultTiles, decodeTiles(""))
        assertEquals(defaultTiles, decodeTiles("   "))
    }

    @Test
    fun `a tile kind we no longer know costs one tile, not the tab`() {
        val stored = "Heart:XL:,Teleportation:L:,Steps:M:"
        val tiles = decodeTiles(stored)
        assertEquals(listOf(TileKind.Heart, TileKind.Steps), tiles.map { it.kind })
    }

    @Test
    fun `an unreadable layout falls back rather than emptying the tab`() {
        assertEquals(defaultTiles, decodeTiles("nonsense"))
        assertEquals(defaultTiles, decodeTiles(",,,"))
    }

    @Test
    fun `a stored size the tile no longer offers is clamped on the way in`() {
        assertEquals(TileSize.L, decodeTiles("Activity:XL:").single().size)
    }

    // ----------------------------------------------------------- reorder

    @Test
    fun `moving a tile shifts it one place`() {
        val tiles = listOf(
            t(TileKind.Heart, TileSize.L),
            t(TileKind.Sleep, TileSize.L),
            t(TileKind.Steps, TileSize.L),
        )
        assertEquals(
            listOf(TileKind.Sleep, TileKind.Heart, TileKind.Steps),
            moveTile(tiles, 0, 1).map { it.kind },
        )
        assertEquals(
            listOf(TileKind.Heart, TileKind.Steps, TileKind.Sleep),
            moveTile(tiles, 2, -1).map { it.kind },
        )
    }

    @Test
    fun `moving past either end changes nothing`() {
        val tiles = listOf(t(TileKind.Heart, TileSize.L), t(TileKind.Sleep, TileSize.L))
        assertEquals(tiles, moveTile(tiles, 0, -1))
        assertEquals(tiles, moveTile(tiles, 1, 1))
        assertEquals(tiles, moveTile(tiles, 5, 1))
    }

    @Test
    fun `the add list is what is not already shown`() {
        val unused = unusedKinds(defaultTiles)
        assertTrue(TileKind.Weight in unused)
        assertTrue(TileKind.Heart in unused)
        assertTrue(TileKind.Skin in unused)
        assertFalse(TileKind.Hrv in unused)
        assertFalse(TileKind.Zones in unused)
        assertTrue(unusedKinds(TileKind.entries.map { t(it, TileSize.S) }).isEmpty())
    }
}
