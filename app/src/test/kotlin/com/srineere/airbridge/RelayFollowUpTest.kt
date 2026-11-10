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
        // while something else holds the band's session, and this is the case
        // the clamp exists for: the write itself leaves the alert inside its
        // lifetime, and the cooldown on top of it is what pushed it out.
        val ends = cooldownEndsAfterAWriteOf(20_000)
        assertTrue(ends > posted + ALERT_LIFETIME_MILLIS)
        assertEquals(
            posted + ALERT_LIFETIME_MILLIS - FOLLOW_UP_MARGIN_MILLIS,
            followUpAt(ends, posted),
        )
    }

    @Test fun `what is sent is still fresh when dispatch tests it again`() {
        // dispatch asks the same question a looper turn later, against
        // postedAt + ALERT_LIFETIME_MILLIS. The margin is what keeps the
        // answer yes.
        val at = followUpAt(cooldownEndsAfterAWriteOf(20_000), posted)
        assertTrue(at < posted + ALERT_LIFETIME_MILLIS)
    }

    @Test fun `a write longer than the lifetime is past saving, and says so`() {
        // AirLink waits out forty-five seconds before it calls an operation
        // timed out. The alert was already too old before that write
        // returned, so the deadline lands in the past: the caller turns it
        // into a delay of zero and sendFollowUp drops it. Nothing here can
        // change that -- only the lifetime can.
        val at = followUpAt(cooldownEndsAfterAWriteOf(45_000), posted)
        assertEquals(posted + ALERT_LIFETIME_MILLIS - FOLLOW_UP_MARGIN_MILLIS, at)
        assertTrue(at < posted + 45_000)
    }

    @Test fun `nothing waiting means the cooldown stands`() {
        assertEquals(50_000L, followUpAt(50_000, null))
    }

    @Test fun `the oldest alert in the burst is the one that sets the deadline`() {
        // sendFollowUp judges each member separately, so a burst dies one
        // alert at a time: the deadline has to be the first one to go, not
        // the last.
        val oldest = minOf(posted, posted + 4_000, posted + 9_000)
        assertEquals(
            posted + ALERT_LIFETIME_MILLIS - FOLLOW_UP_MARGIN_MILLIS,
            followUpAt(cooldownEndsAfterAWriteOf(25_000), oldest),
        )
    }
}
