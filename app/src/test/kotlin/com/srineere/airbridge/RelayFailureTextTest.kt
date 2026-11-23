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
            "Failed · Cuff wasn't connected to the band",
            describeRelayFailure("failed", "WORKER_UNAVAILABLE"),
        )
        // dispatch again: the band acknowledged fewer pulses than asked
        // for. The only line here that describes something the wrist
        // actually felt, so it must not read like the others.
        assertEquals(
            "Failed · only part of the buzz played",
            describeRelayFailure("failed", "PARTIAL"),
        )
    }

    @Test fun `AirLink's own codes keep AirLink's meaning`() {
        // AirLink's operation deadline, forty-five seconds.
        assertTrue("didn't answer" in describeRelayFailure("failed", "TIMEOUT"))
        // retryHandshake, after its last attempt: "Google Health may be using it".
        assertTrue("Google Health" in describeRelayFailure("failed", "HANDSHAKE_TIMEOUT"))
        // connectFresh, and onConnectionStateChange with a bad status.
        // This is the one that sent somebody here: it had no sentence, so
        // the list showed the code and a row read "Failed · gatt connect".
        assertEquals(
            "Failed · couldn't connect over Bluetooth",
            describeRelayFailure("failed", "GATT_CONNECT"),
        )
    }

    /**
     * Short enough to sit on one line of a row's subtext.
     *
     * The list is scanned, not read. A sentence that wrapped onto a
     * third line pushed the row below it off the screen, which is how a
     * helpful explanation turned into a worse list. Forty-eight
     * characters is the longest of these as written; the limit is here
     * so the next one added has to earn its length.
     */
    @Test fun `every sentence fits on a line`() {
        FAILURE_NOTES.forEach { (code, note) ->
            assertTrue(
                "$code says \"$note\", ${note.length} characters",
                note.length <= 48,
            )
        }
    }

    /**
     * Two failures that are not the same failure do not read the same.
     *
     * Shortening these lost "couldn't connect — the Google Health app
     * may have been using the band" down to "couldn't connect", which is
     * word for word what a plain Bluetooth failure says. The phrase that
     * distinguishes the two is the one worth the room.
     */
    @Test fun `a stalled handshake does not read as a failed connection`() {
        assertTrue(
            FAILURE_NOTES.getValue("HANDSHAKE_TIMEOUT") !=
                FAILURE_NOTES.getValue("GATT_CONNECT"),
        )
        assertTrue("Google Health" in FAILURE_NOTES.getValue("HANDSHAKE_TIMEOUT"))
    }

    /**
     * The words are for whoever is holding the phone.
     *
     * Every sentence is read by somebody wondering why their wrist stayed
     * still, and none of these words means anything to them. "Air" is the
     * band's model name, which the app never shows; the rest are this
     * codebase talking to itself.
     */
    @Test fun `no sentence uses a word from inside the codebase`() {
        val jargon = listOf("relay", "gatt", "secure session", "handshake", "worker", "Air ")
        FAILURE_NOTES.forEach { (code, note) ->
            jargon.forEach { word ->
                assertTrue(
                    "$code says \"$note\", which uses \"${word.trim()}\"",
                    !note.contains(word, ignoreCase = true),
                )
            }
        }
    }

    /**
     * Every code a buzz can fail with has a sentence.
     *
     * The fallback humanises the code, which is honest and unreadable --
     * "Failed · gatt connect". It is a backstop for a code a future
     * AirLink raises, not somewhere the codes already known should land.
     * A code added to the send path without a sentence fails here.
     */
    @Test fun `every code a buzz can raise is spelled out`() {
        val reachable = listOf(
            // CalendarRelayService and RelayWorkerResult.decode.
            "WORKER_UNAVAILABLE", "SERVICE_STOPPED", "PARTIAL", "WORKER_ERROR", "WORKER_PROTOCOL",
            // AirLink, on the path from connect to a played pattern.
            "TIMEOUT", "HANDSHAKE_TIMEOUT", "BUSY", "AIR_NOT_FOUND", "BLUETOOTH_PERMISSION",
            "SCAN_FAILED", "GATT_CONNECT", "GATT_DISCONNECTED", "GATT_DISCOVERY",
            "HAPTIC_TAP_FAILED", "HAPTIC_PULSE_FAILED", "HAPTIC_FAILED", "RING_FAILED",
        )
        reachable.forEach { code ->
            assertTrue("$code has no sentence", code in FAILURE_NOTES)
        }
    }

    @Test fun `a code nobody has traced keeps the words it always had`() {
        // Not invented: the humanised code. HAPTIC_READ_FAILED is raised
        // by a read of the band's intensity setting, not by sending a
        // buzz, so it cannot appear in this list -- and guessing at a
        // sentence for a code nobody has read is how a list of facts
        // starts lying.
        assertEquals(
            "Failed · haptic read failed",
            describeRelayFailure("failed", "HAPTIC_READ_FAILED"),
        )
    }
}
