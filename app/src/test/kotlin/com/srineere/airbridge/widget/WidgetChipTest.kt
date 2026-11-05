package com.srineere.airbridge.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The change chip at each width, against `ChangeChip`'s rule.
 *
 * The tab drops the unit at M and keeps it everywhere wider, because the
 * head already names the reading and the figure under it carries the unit.
 * The widget has to make that cut from a string the unit has already been
 * baked into, so the cut is worth pinning down.
 */
class WidgetChipTest {

    @Test
    fun `the unit goes at square and stays everywhere else`() {
        assertEquals("↑ 2", chipAt("↑ 2 bpm", WidgetSize.Square))
        assertEquals("↑ 2 bpm", chipAt("↑ 2 bpm", WidgetSize.Wide))
        assertEquals("↑ 2 bpm", chipAt("↑ 2 bpm", WidgetSize.Tall))
        assertEquals("↑ 2 bpm", chipAt("↑ 2 bpm", WidgetSize.Row))
    }

    @Test
    fun `a per cent sign is a unit like any other`() {
        assertEquals("↓ 12", chipAt("↓ 12 %", WidgetSize.Square))
    }

    /** Sleep's chip is "↑ 1h 20m", where the last word is the figure. */
    @Test
    fun `a chip whose last word holds a digit keeps all of it`() {
        assertEquals("↑ 1h 20m", chipAt("↑ 1h 20m", WidgetSize.Square))
        assertEquals("↑ 40m", chipAt("↑ 40m", WidgetSize.Square))
    }

    @Test
    fun `a chip with no unit to drop is left alone`() {
        assertEquals("Steady", chipAt("Steady", WidgetSize.Square))
        assertEquals("↑ 3", chipAt("↑ 3", WidgetSize.Square))
    }
}
