package com.srineere.airbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a failed row shows.
 *
 * Tested because the thing that makes it worth writing is also the thing a
 * compiler cannot see: that the sentence matches the line which raises the
 * code. The assertions below name the raise site in each case, so a code
 * that is renamed or repurposed has somewhere to be caught.
 */
class RelayFailureTextTest {

    @Test fun `every failure says so before it says why`() {
        assertTrue(describeRelayFailure("failed", "TIMEOUT").startsWith("Failed"))
        assertTrue(describeRelayFailure("failed", "SOMETHING_NEW").startsWith("Failed"))
        assertTrue(describeRelayFailure("expired", null).startsWith("Failed"))
    }

    @Test fun `a failure with nothing to add is just the word`() {
        assertEquals("Failed", describeRelayFailure("failed", null))
    }

    @Test fun `an expired alert says it was held too long, not that the band refused`() {
        // CalendarRelayService.sendFollowUp records this with no code at all:
        // nothing was ever sent, so the band has no opinion about it.
        assertEquals("Failed · it was already too old to send", describeRelayFailure("expired", null))
    }

    @Test fun `the codes this app raises itself are spelled out`() {
        // CalendarRelayService.dispatch, when onAir answers false.
        assertEquals(
            "Failed · the relay had no link to the band",
            describeRelayFailure("failed", "WORKER_UNAVAILABLE"),
        )
        // dispatch again: the band acknowledged fewer pulses than asked for.
        assertEquals(
            "Failed · only part of the pattern played",
            describeRelayFailure("failed", "PARTIAL"),
        )
    }

    @Test fun `AirLink's own codes keep AirLink's meaning`() {
        // AirLink's operation deadline, forty-five seconds.
        assertEquals("Failed · the band did not answer", describeRelayFailure("failed", "TIMEOUT"))
        // retryHandshake, after its last attempt: "Google Health may be using it".
        assertTrue("Google Health" in describeRelayFailure("failed", "HANDSHAKE_TIMEOUT"))
    }

    @Test fun `a code nobody has traced keeps the words it always had`() {
        // Not invented: the humanised code, exactly as the list showed it
        // before any of this, with the word Failed in front of it.
        assertEquals(
            "Failed · haptic read failed",
            describeRelayFailure("failed", "HAPTIC_READ_FAILED"),
        )
    }
}
