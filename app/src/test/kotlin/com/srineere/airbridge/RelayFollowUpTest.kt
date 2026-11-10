package com.srineere.airbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a held burst is actually sent.
 *
 * Worth a test of its own because the failure it describes is invisible on a
 * phone that is being watched: it needs the band to be slow, and the band is
 * only slow when nobody is looking at the phone -- when Google Health has the
 * Air's one secure session and the screen has been off long enough for it to
 * want it. Everything the wearer notices happens fifteen seconds later, on a
 * wrist, with nothing on screen to say an alert was dropped.
 */
class RelayFollowUpTest {

    /** The posted-at of the oldest alert in a burst, in these tests. */
    private val posted = 100_000L

    /** What RelayCoalescer waits after a command before sending the burst. */
    private val cooldown = 15_000L

    private fun cooldownEndsAfterAWriteOf(millis: Long) = posted + millis + cooldown

    @Test fun `a band that answers quickly leaves the cooldown alone`() {
        // Two seconds of tap, then the cooldown: the alert is seventeen
        // seconds old when the follow-up runs, well inside its thirty, so
        // there is nothing to bring forward.
        val ends = cooldownEndsAfterAWriteOf(2_000)
        assertEquals(ends, followUpAt(ends, posted))
    }

    @Test fun `a slow write no longer takes the burst down with it`() {
        // Around fifteen seconds is the figure the handover gives for a read
        // while something else holds the band's session. Under the old
        // thirty-second lifetime this write plus the cooldown put the burst
        // past its expiry and the clamp was the only thing saving it; at
        // ninety it is comfortably inside, and the cooldown simply stands.
        // Both halves of the fix are in that sentence, which is why the
        // assertion is that nothing had to be brought forward.
        val ends = cooldownEndsAfterAWriteOf(20_000)
        assertTrue(ends < posted + ALERT_LIFETIME_MILLIS)
        assertEquals(ends, followUpAt(ends, posted))
    }

    @Test fun `what is sent is still fresh when dispatch tests it again`() {
        // dispatch asks the same question a looper turn later, against
        // postedAt + ALERT_LIFETIME_MILLIS. The margin is what keeps the
        // answer yes.
        val at = followUpAt(cooldownEndsAfterAWriteOf(20_000), posted)
        assertTrue(at < posted + ALERT_LIFETIME_MILLIS)
    }

    @Test fun `even a write that runs to AirLink's timeout is now saved`() {
        // The case the lifetime was raised for. AirLink waits out forty-five
        // seconds before it calls an operation timed out, and the coalescer
        // adds fifteen: sixty, which under the old thirty-second lifetime
        // meant the whole burst was already dead when the write came back,
        // and nothing in this function could have helped it. At ninety the
        // burst outlives the worst the band can do on its own, and the
        // cooldown stands untouched.
        val ends = cooldownEndsAfterAWriteOf(45_000)
        assertTrue(ends < posted + ALERT_LIFETIME_MILLIS)
        assertEquals(ends, followUpAt(ends, posted))
    }

    @Test fun `the clamp still bites when something holds the band far longer`() {
        // The clamp has not been made dead code by the raise -- it is now
        // reached by a band held past anything AirLink would wait for, which
        // is contention rather than a timeout. Eighty seconds plus the
        // cooldown is past ninety, so the follow-up is pulled back to the
        // oldest alert's expiry less the margin.
        val ends = cooldownEndsAfterAWriteOf(80_000)
        assertTrue(ends > posted + ALERT_LIFETIME_MILLIS)
        assertEquals(
            posted + ALERT_LIFETIME_MILLIS - FOLLOW_UP_MARGIN_MILLIS,
            followUpAt(ends, posted),
        )
    }

    @Test fun `nothing waiting means the cooldown stands`() {
        assertEquals(50_000L, followUpAt(50_000, null))
    }

    @Test fun `the oldest alert in the burst is the one that sets the deadline`() {
        // sendFollowUp judges each member separately, so a burst dies one
        // alert at a time: the deadline has to be the first one to go, not
        // the last.
        // The write has to be long enough to reach the clamp at all -- past
        // ninety seconds once the cooldown is added -- or this asserts
        // nothing about which alert was chosen, only that the cooldown stood.
        val oldest = minOf(posted, posted + 4_000, posted + 9_000)
        assertEquals(
            posted + ALERT_LIFETIME_MILLIS - FOLLOW_UP_MARGIN_MILLIS,
            followUpAt(cooldownEndsAfterAWriteOf(80_000), oldest),
        )
    }
}
