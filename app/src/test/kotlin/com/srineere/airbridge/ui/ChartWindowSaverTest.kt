package com.srineere.airbridge.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A window has to come back the way it was left.
 *
 * It is the one piece of state on the chart screens that is genuinely
 * expensive to lose: three taps to get back to the week before last, and a
 * process can die while the phone is in a pocket. Rotation was never the
 * problem -- MainActivity handles its own config changes -- so the only
 * thing that exercises this path on a phone is "Don't keep activities" or a
 * kill nobody sees coming, which is to say nobody will notice it broken.
 *
 * Both numbers matter and they are easy to confuse: [ChartWindow.length] is
 * how much you see, [ChartWindow.offset] is how far back it sits. A saver
 * that kept only the length would restore a window of the right size,
 * sitting on today, and look entirely correct while being about the wrong
 * fortnight.
 */
class ChartWindowSaverTest {

    /**
     * Everything is saveable here. What a real registry will and will not
     * take is Android's question; this is about the two numbers.
     */
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun roundTrip(window: ChartWindow): ChartWindow {
        val saved = with(ChartWindowSaver) { scope.save(window) }
        return requireNotNull(ChartWindowSaver.restore(requireNotNull(saved))) {
            "the saver refused to restore what it had just saved"
        }
    }

    @Test fun `a window on today comes back on today`() {
        val restored = roundTrip(ChartWindow(30))
        assertEquals(30, restored.length)
        assertEquals(0, restored.offset)
        assertEquals(true, restored.isNow)
    }

    @Test fun `a window stepped back comes back where it was`() {
        val window = ChartWindow(7)
        window.back()
        window.back()
        val restored = roundTrip(window)
        assertEquals(7, restored.length)
        assertEquals(14, restored.offset)
        assertEquals(false, restored.isNow)
        // The three answers everything on the screen is drawn from.
        assertEquals(window.daysNeeded, restored.daysNeeded)
        assertEquals(window.from, restored.from)
        assertEquals(window.until, restored.until)
    }

    @Test fun `resizing before stepping back survives too`() {
        val window = ChartWindow(7)
        window.resize(30)
        window.back()
        val restored = roundTrip(window)
        assertEquals(30, restored.length)
        assertEquals(30, restored.offset)
    }

    @Test fun `a restored window still moves`() {
        val window = ChartWindow(7)
        window.back()
        val restored = roundTrip(window)
        restored.forward()
        assertEquals(0, restored.offset)
        // And still cannot be walked into the future.
        restored.forward()
        assertEquals(0, restored.offset)
    }
}
