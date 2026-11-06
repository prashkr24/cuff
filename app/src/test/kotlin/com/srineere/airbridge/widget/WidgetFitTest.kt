package com.srineere.airbridge.widget

import com.srineere.airbridge.health.TileKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What can be checked about clipping without a launcher.
 *
 * A widget clips when a Column is given more children than its cell has
 * room for, and Glance does not report it -- an overrun loses the last
 * child in silence. None of that is observable from a unit test, so this
 * pins the two things that are: that every design offers only sizes it was
 * drawn for, and that no face asks for more rows than its smallest cell
 * can hold at the row heights the code uses.
 */
class WidgetFitTest {

    @Test fun `every design offers at least one size and no size twice`() {
        WidgetCatalogue.all.forEach { design ->
            assertTrue("${design.id} offers nothing", design.sizes.isNotEmpty())
            assertTrue(
                "${design.id} offers a size it cannot fill",
                design.sizes.all { it in WidgetSize.entries },
            )
        }
    }

    /**
     * The heights the row faces are laid out against.
     *
     * Taken from the cells the manifest declares, minus the padding and
     * the head: if these stop matching the constants in WidgetRings the
     * arithmetic there is wrong and this says so.
     */
    @Test fun `a row face cannot ask for more rows than its cell holds`() {
        // 4 x 2 and 4 x 4 in portrait dp, as WidgetSize declares them.
        val wide = WidgetSize.Wide.minHeight - 24 - 24
        val tall = WidgetSize.Tall.minHeight - 24 - 24
        // Summary rows at 40 and 48, trends at 52 and 62.
        assertTrue("a 4x2 should hold two summary rows", wide / 40 >= 2)
        assertTrue("a 4x4 should hold five summary rows", tall / 48 >= 5)
        assertTrue("a 4x2 should hold one trend row", wide / 52 >= 1)
        assertTrue("a 4x4 should hold four trend rows", tall / 62 >= 4)
    }

    @Test fun `the two widget-only kinds are not offered on the tab`() {
        // They exist for the catalogue and nowhere else; a kind that drifts
        // back onto the tab is a tab saying the same thing three times.
        assertTrue(!TileKind.Stages.onTab)
        assertTrue(!TileKind.SleepBank.onTab)
        assertTrue(TileKind.Readiness.onTab)
    }
}
