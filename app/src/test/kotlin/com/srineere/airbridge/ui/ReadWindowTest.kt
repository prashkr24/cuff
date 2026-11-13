package com.srineere.airbridge.ui

import com.srineere.airbridge.health.Readiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far back a read actually goes, when a screen asks for an odd number
 * of days.
 *
 * The screens ask for whatever their window bar needs, and a window bar
 * stepped backwards asks for a different number every tap. Health Connect
 * charges per round trip rather than per day and rate-limits callers, so
 * three reads of 35, 42 and 49 days are three waits for almost exactly the
 * same readings.
 */
class ReadWindowTest {

    @Test fun `the windows screens ask for by name are read exactly`() {
        // Nothing here may cost a day more than it did before this
        // rounding existed: thirty is the tab, ninety is every chart's
        // widest pill, and a hundred and twenty is readiness and fitness
        // age. All three are whole months already.
        assertEquals(30, readWindow(30))
        assertEquals(90, readWindow(90))
        assertEquals(120, readWindow(Readiness.WINDOW_DAYS))
    }

    @Test fun `a window shorter than the tab's own still reads the tab's own`() {
        // The tab has read a month before any of these screens open, so
        // asking for a fortnight can only ever mean a second read of
        // ground already covered.
        assertEquals(30, readWindow(1))
        assertEquals(30, readWindow(7))
        assertEquals(30, readWindow(29))
    }

    @Test fun `stepping a seven-day chart back costs one read, not four`() {
        // 35, 42, 49 and 56 are four windows over the same two months.
        assertEquals(60, readWindow(35))
        assertEquals(60, readWindow(42))
        assertEquals(60, readWindow(49))
        assertEquals(60, readWindow(56))
        // And the next step past two months is the next read, not the
        // fifth: rounding buys a month at a time, never more.
        assertEquals(90, readWindow(63))
    }

    @Test fun `nothing is ever read short of what was asked for`() {
        // The one property that must hold for every input, because a
        // window read short is a chart with its oldest end missing and no
        // way to tell that from a quiet fortnight.
        (1..400).forEach { asked ->
            val read = readWindow(asked)
            assertTrue("$asked rounds below itself", read >= asked)
            assertEquals("$asked is not a whole month", 0, read % 30)
            assertTrue("$asked over-reads by a month or more", read - asked < 30)
        }
    }
}
