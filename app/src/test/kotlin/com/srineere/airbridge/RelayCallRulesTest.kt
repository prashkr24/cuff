package com.srineere.airbridge

import org.junit.Assert.*
import org.junit.Test

class RelayCallRulesTest {
    private fun classify(
        category: String?,
        ongoing: Boolean,
        fullScreen: Boolean,
        channelId: String?,
    ) = RelayAlertClassifier.classify(category, ongoing, fullScreen, channelId)

    // Channel and category values below were captured from a real Pixel dialer
    // and WhatsApp on Android 15.
    @Test fun `a ringing call is recognised by category, full screen or channel`() {
        assertEquals(RelayAlertKind.CALL, classify("call", true, true, "phone_incoming_call"))
        assertEquals(RelayAlertKind.CALL, classify("call", true, true, "voip_notification_7"))
        assertEquals(RelayAlertKind.CALL, classify(null, true, true, null))
        assertEquals(RelayAlertKind.CALL, classify(null, true, false, "voip_notification"))
        assertEquals(RelayAlertKind.CALL, classify("call", true, false, "messages"))
    }

    @Test fun `a call that is no longer ringing never rings the wrist`() {
        // Answering leaves a sticky notification whose channel still says "call".
        assertEquals(RelayAlertKind.MESSAGE, classify(null, true, false, "phone_ongoing_call"))
        assertEquals(RelayAlertKind.MESSAGE, classify("call", true, true, "phone_ongoing_call"))
        assertEquals(RelayAlertKind.MESSAGE, classify("missed_call", false, false, "phone_missed_call"))
        assertEquals(RelayAlertKind.MESSAGE, classify(null, false, false, "phone_missed_call"))
    }

    @Test fun `a call that is not held up is an event, not a ringing phone`() {
        assertEquals(RelayAlertKind.MESSAGE, classify("call", false, false, null))
    }

    @Test fun `an ordinary sticky notification never rings`() {
        assertEquals(RelayAlertKind.MESSAGE, classify(null, true, false, "media_playback"))
        assertEquals(RelayAlertKind.MESSAGE, classify("msg", false, false, "whatsapp_calls"))
    }

    @Test fun `calls use the call preset and ignore message rules`() {
        val config = RelayRuleConfig(
            defaultPreset = RelayPreset.DOUBLE,
            defaultCallPreset = RelayPreset.RING,
            apps = mapOf(
                "com.whatsapp" to RelayAppRule(true, RelayPreset.LOW),
                "com.instagram" to RelayAppRule(true, RelayPreset.LOW, RelayPreset.HIGH),
            ),
            // A muted chat must not silence that person's incoming call.
            chats = mapOf("alice" to SavedChat("com.whatsapp", "Alice", preset = RelayPreset.MUTE)),
            everyoneElseSilent = true,
        )
        val call = RelayAlertKind.CALL
        assertEquals(RelayPreset.RING, config.match("com.whatsapp", setOf("alice"), call)?.preset)
        assertEquals(RelayPreset.HIGH, config.match("com.instagram", emptySet(), call)?.preset)
        assertEquals(RelayPreset.LOW, config.match("com.instagram", emptySet())?.preset)
        // The same unsaved WhatsApp contact is silenced as a message and still
        // rings as a call: "everyone else: silent" is a rule about messages.
        assertNull(config.match("com.whatsapp", emptySet()))
        assertEquals(RelayPreset.RING, config.match("com.whatsapp", emptySet(), call)?.preset)
    }

    @Test fun `an app can be silent for messages and still ring for calls`() {
        val config = RelayRuleConfig(
            apps = mapOf("com.whatsapp" to RelayAppRule(true, RelayPreset.MUTE, RelayPreset.RING)),
        )
        assertNull(config.match("com.whatsapp", emptySet()))
        assertEquals(
            RelayPreset.RING,
            config.match("com.whatsapp", emptySet(), RelayAlertKind.CALL)?.preset,
        )
    }

    @Test fun `and the other way round, messages without calls`() {
        val config = RelayRuleConfig(
            apps = mapOf("com.whatsapp" to RelayAppRule(true, RelayPreset.HIGH, RelayPreset.MUTE)),
        )
        assertEquals(RelayPreset.HIGH, config.match("com.whatsapp", emptySet())?.preset)
        assertNull(config.match("com.whatsapp", emptySet(), RelayAlertKind.CALL))
    }

    @Test fun `a muted call preset drops the alert rather than ringing silently`() {
        // Each default mutes only what it covers: muting the phone leaves an
        // app's calls alone, and muting apps leaves the phone's alone.
        val quietApps = RelayRuleConfig(
            defaultAppCallPreset = RelayPreset.MUTE,
            apps = mapOf("com.whatsapp" to RelayAppRule(true), dialer to RelayAppRule(true)),
        )
        assertNull(quietApps.match("com.whatsapp", emptySet(), RelayAlertKind.CALL, dialer = dialer))
        assertNotNull(quietApps.match(dialer, emptySet(), RelayAlertKind.CALL, dialer = dialer))

        val quietPhone = quietApps.copy(
            defaultCallPreset = RelayPreset.MUTE,
            defaultAppCallPreset = RelayPreset.RING,
        )
        assertNull(quietPhone.match(dialer, emptySet(), RelayAlertKind.CALL, dialer = dialer))
        assertNotNull(quietPhone.match("com.whatsapp", emptySet(), RelayAlertKind.CALL, dialer = dialer))
    }

    @Test fun `a disabled app stays silent for calls too`() {
        val config = RelayRuleConfig(apps = mapOf("com.whatsapp" to RelayAppRule(false)))
        assertNull(config.match("com.whatsapp", emptySet(), RelayAlertKind.CALL))
    }

    @Test fun `a call grouped under a summary still rings`() {
        // WhatsApp posts its VOIP notification inside a notification group;
        // rejecting group summaries silenced every WhatsApp call.
        val filter = CalendarAlertFilter(0, emptyMap(), setOf("com.whatsapp"))
        assertTrue(filter.accept("com.whatsapp", "voip", 10, 20, true, true, null, RelayAlertKind.CALL))
        // A grouped message summary is still noise.
        assertFalse(filter.accept("com.whatsapp", "chat", 10, 20, false, true, null))
    }

    @Test fun `the ring filter accepts an ongoing call but still drops other sticky alerts`() {
        val filter = CalendarAlertFilter(0, emptyMap(), setOf("com.whatsapp"))
        assertTrue(filter.accept("com.whatsapp", "call-key", 10, 20, true, false, null, RelayAlertKind.CALL))
        assertFalse(filter.accept("com.whatsapp", "sticky-key", 10, 20, true, false, null, RelayAlertKind.MESSAGE))
    }

    @Test fun `a dialer reusing one key still rings on every later call`() {
        // The regression that silenced every call after the first: a dialer
        // posts each incoming call under the same key, with no timestamp to
        // tell the calls apart.
        val filter = CalendarAlertFilter(0, emptyMap(), setOf("com.google.android.dialer"))
        repeat(4) { attempt ->
            val postedAt = 10L + attempt * 1_000
            assertTrue(
                "call ${attempt + 1} was swallowed",
                filter.accept("com.google.android.dialer", "call-key", postedAt, postedAt + 10,
                    true, false, null, RelayAlertKind.CALL),
            )
        }
        // Messages keep their dedupe: the same chat key cannot buzz twice.
        assertTrue(filter.accept("com.google.android.dialer", "chat", 10, 20, false, false, null))
        assertFalse(filter.accept("com.google.android.dialer", "chat", 12, 22, false, false, null))
    }

    @Test fun `ring timing is bounded so pulses stay distinguishable`() {
        assertEquals(700, RelayRingSettings().gapMillis)
        assertEquals(45_000L, RelayRingSettings().maxMillis)
        val tooFast = RelayRingSettings.MINIMUM_GAP_MILLIS - 1
        assertThrows(IllegalArgumentException::class.java) { RelayRingSettings(tooFast, 45) }
        assertThrows(IllegalArgumentException::class.java) { RelayRingSettings(5_001, 45) }
        // Bounded below the relay's 240 s watchdog.
        assertThrows(IllegalArgumentException::class.java) { RelayRingSettings(700, 181) }
        assertThrows(IllegalArgumentException::class.java) { RelayRingSettings(700, 9) }
    }

    @Test fun `a ring always pulses at the stronger of the two strengths`() {
        assertEquals(3, RelayPreset.RING.intensity)
        assertEquals(3, RelayPreset.HIGH.intensity)
        assertEquals(RelayPreset.HIGH, RelayRuleConfig().defaultPreset)
    }

    @Test fun `a replaced ringing notification does not end the ring`() {
        // A locked phone swaps the ringing notification for its full-screen
        // call UI; the dialer ring used to die ~680 ms in because of it.
        assertFalse(RelayRingWatch.stopOnRemoval(RelayRingWatch.MODE_RINGTONE))
        assertTrue(RelayRingWatch.stopOnRemoval(RelayRingWatch.MODE_NORMAL))
        assertTrue(RelayRingWatch.stopOnRemoval(RelayRingWatch.MODE_IN_CALL))
    }

    /**
     * The bug a reporter on a Pixel 9a found the shape of before we did:
     * with the ringer silenced, no call ever buzzed the wrist, and the
     * one buzz that did arrive came after the call was over. Setting an
     * audible ringtone fixed it, which is what named the cause.
     *
     * Telecom takes ringing audio focus to play a ringtone. With nothing
     * to play it never asks, so the mode sits at MODE_NORMAL through a
     * ringing call -- and MODE_NORMAL is exactly what this reads as "the
     * phone has stopped ringing". The ring died on the first swap of the
     * notification, every time.
     */
    @Test fun `with the ringer silenced the audio mode is not a witness`() {
        val silent = false
        // The failing case: ringing, notification replaced, mode never
        // reached MODE_RINGTONE because there was no ringtone to play.
        assertFalse(RelayRingWatch.stopOnRemoval(RelayRingWatch.MODE_NORMAL, silent))
        // Answered is still answered: that one the mode does report.
        assertTrue(RelayRingWatch.stopOnRemoval(RelayRingWatch.MODE_IN_CALL, silent))
        assertTrue(RelayRingWatch.stopOnRemoval(RelayRingWatch.MODE_IN_COMMUNICATION, silent))
    }

    @Test fun `an audible ringer behaves exactly as it always has`() {
        // The half that works is not being changed, and this says so.
        RelayRingWatch.run {
            listOf(MODE_NORMAL, MODE_RINGTONE, MODE_IN_CALL, MODE_IN_COMMUNICATION).forEach { mode ->
                assertEquals(stopOnRemoval(mode), stopOnRemoval(mode, ringerAudible = true))
            }
        }
    }

    /**
     * What ends a ring on a silent phone that nobody picked up.
     *
     * Without a second witness the ring would run to its full forty-five
     * seconds after a phone that had already given up, which is a worse
     * bug than the one being fixed. Telecom posts the missed call on its
     * own channel the moment it stops ringing.
     */
    @Test fun `a missed call ends the ring it belongs to`() {
        assertTrue(RelayRingWatch.endsRing("com.google.android.dialer", "com.google.android.dialer", "phone_missed_call"))
        // Answered elsewhere, or picked up: the ongoing-call channel.
        assertTrue(RelayRingWatch.endsRing("com.whatsapp", "com.whatsapp", "ongoing_call"))
    }

    @Test fun `somebody else's notification does not end the ring`() {
        // A message arriving mid-call is not the call ending, and nor is
        // another app's missed call.
        assertFalse(RelayRingWatch.endsRing("com.whatsapp", "com.google.android.dialer", "phone_missed_call"))
        assertFalse(RelayRingWatch.endsRing("com.whatsapp", "com.whatsapp", "messages"))
        // The ringing channel itself must never end its own ring: that is
        // the notification being re-posted, which is how it stays up.
        assertFalse(RelayRingWatch.endsRing("com.whatsapp", "com.whatsapp", "incoming_call"))
        assertFalse(RelayRingWatch.endsRing("com.whatsapp", "com.whatsapp", null))
    }

    @Test fun `the ring ends when the phone stops ringing, however it ended`() {
        val ringing = RelayRingWatch.MODE_RINGTONE
        // Still ringing.
        assertNull(RelayRingWatch.reason(ringing, true, ringing))
        // Picked up, on the phone and in a VoIP app.
        assertEquals("answered", RelayRingWatch.reason(ringing, true, RelayRingWatch.MODE_IN_CALL))
        assertEquals("answered", RelayRingWatch.reason(ringing, true, RelayRingWatch.MODE_IN_COMMUNICATION))
        // Declined, missed, or cancelled by the caller.
        assertEquals("ended", RelayRingWatch.reason(ringing, true, RelayRingWatch.MODE_NORMAL))
        // Any other state is still "no longer ringing", and must not hang.
        assertEquals("ended", RelayRingWatch.reason(ringing, true, 4))
    }

    @Test fun `an app already holding the audio session is not treated as answered`() {
        val inComm = RelayRingWatch.MODE_IN_COMMUNICATION
        assertNull(RelayRingWatch.reason(inComm, false, inComm))
        // It has never been heard ringing, so silence cannot mean it ended.
        assertNull(RelayRingWatch.reason(inComm, false, RelayRingWatch.MODE_NORMAL))
    }

    @Test fun `a vibration is a pattern and a strength, chosen separately`() {
        val twoLow = RelayPreset(RelayPattern.TWO, RelayPreset.LOW_STRENGTH)
        assertEquals("two:2", twoLow.id)
        assertEquals(twoLow, RelayPreset.parse("two:2"))
        assertEquals(2, twoLow.intensity)
        assertEquals(2, twoLow.pattern.pulses)
        // Every pattern can take either strength.
        assertEquals("ring:2", RelayPreset(RelayPattern.RING, RelayPreset.LOW_STRENGTH).id)
        assertEquals("one:3", RelayPreset.HIGH.id)
        // Silent sends nothing, so it has no strength to speak of.
        assertTrue(RelayPreset.MUTE.silent)
        assertNull(RelayPreset.MUTE.intensity)
        assertEquals("silent", RelayPreset.MUTE.id)
    }

    @Test fun `settings written before strength was separate still read back`() {
        assertEquals(RelayPreset.HIGH, RelayPreset.parse("single"))
        assertEquals(RelayPreset.LOW, RelayPreset.parse("low"))
        assertEquals(RelayPreset.HIGH, RelayPreset.parse("high"))
        assertEquals(RelayPreset.DOUBLE, RelayPreset.parse("double"))
        assertEquals(RelayPreset.RING, RelayPreset.parse("ring"))
        assertEquals(RelayPreset.MUTE, RelayPreset.parse("mute"))
        assertThrows(IllegalArgumentException::class.java) { RelayPreset.parse("nonsense") }
        assertThrows(IllegalArgumentException::class.java) { RelayPreset.parse("one:9") }
    }

    private val dialer = "com.google.android.dialer"

    private fun withCallers(
        callers: Map<String, SavedCaller>,
        onlySaved: Boolean = false,
    ) = RelayRuleConfig(
        apps = mapOf(dialer to RelayAppRule(true)),
        callers = callers,
        onlySavedCallers = onlySaved,
    )

    @Test fun `an app's call falls back to the app call default, not the phone's`() {
        val config = RelayRuleConfig(
            defaultCallPreset = RelayPreset.RING,
            defaultAppCallPreset = RelayPreset.DOUBLE,
            apps = mapOf(dialer to RelayAppRule(true), "com.whatsapp" to RelayAppRule(true)),
        )
        // The phone keeps its own ring; an app that has said nothing takes the
        // app default instead of borrowing the phone's.
        assertEquals(
            RelayPreset.RING,
            config.match(dialer, emptySet(), RelayAlertKind.CALL, dialer = dialer)?.preset,
        )
        assertEquals(
            RelayPreset.DOUBLE,
            config.match("com.whatsapp", emptySet(), RelayAlertKind.CALL, dialer = dialer)?.preset,
        )
        // An app's own call rule still beats both.
        val set = config.copy(apps = config.apps + ("com.whatsapp" to RelayAppRule(true, callPreset = RelayPreset.LOW)))
        assertEquals(
            RelayPreset.LOW,
            set.match("com.whatsapp", emptySet(), RelayAlertKind.CALL, dialer = dialer)?.preset,
        )
    }

    @Test fun `a name an app wrote because it had none is not an identity`() {
        // Telegram's own sentinel for a caller it cannot name.
        assertTrue(RelayCallerIdentity.isPlaceholderName("___"))
        assertTrue(RelayCallerIdentity.isPlaceholderName("_"))
        // Real names, in any script, are left alone.
        assertFalse(RelayCallerIdentity.isPlaceholderName("Priya"))
        assertFalse(RelayCallerIdentity.isPlaceholderName("\u0905\u092E\u0932"))
        assertFalse(RelayCallerIdentity.isPlaceholderName("a_b"))
        assertFalse(RelayCallerIdentity.isPlaceholderName(""))
    }

    @Test fun `a saved caller's own vibration outranks the app's`() {
        val config = withCallers(mapOf("alice" to SavedCaller(dialer, "Alice", RelayPreset.DOUBLE)))
        val match = config.match(dialer, emptySet(), RelayAlertKind.CALL, "alice")
        assertEquals(RelayPreset.DOUBLE, match?.preset)
        // More specific than an app rule, so it wins a burst.
        assertEquals(3, match?.specificity)
    }

    @Test fun `a caller can be silenced without silencing the app`() {
        val config = withCallers(mapOf("spam" to SavedCaller(dialer, "Spam", RelayPreset.MUTE)))
        assertNull(config.match(dialer, emptySet(), RelayAlertKind.CALL, "spam"))
        // Everyone else still rings.
        assertEquals(
            RelayPreset.RING,
            config.match(dialer, emptySet(), RelayAlertKind.CALL, "someone")?.preset,
        )
    }

    @Test fun `only saved callers silences the rest`() {
        val config = withCallers(
            mapOf("alice" to SavedCaller(dialer, "Alice")),
            onlySaved = true,
        )
        assertEquals(
            RelayPreset.RING,
            config.match(dialer, emptySet(), RelayAlertKind.CALL, "alice")?.preset,
        )
        assertNull(config.match(dialer, emptySet(), RelayAlertKind.CALL, "bob"))
    }

    @Test fun `a call nobody can be attributed to still rings`() {
        // Silencing calls an app never described would silence that app
        // entirely, which is not what "only saved callers" promises.
        val config = withCallers(
            mapOf("alice" to SavedCaller(dialer, "Alice")),
            onlySaved = true,
        )
        assertEquals(
            RelayPreset.RING,
            config.match(dialer, emptySet(), RelayAlertKind.CALL, null)?.preset,
        )
    }

    @Test fun `caller rules never touch messages`() {
        val config = withCallers(mapOf("alice" to SavedCaller(dialer, "Alice", RelayPreset.MUTE)))
        assertEquals(
            RelayPreset.HIGH,
            config.match(dialer, emptySet(), RelayAlertKind.MESSAGE, "alice")?.preset,
        )
    }

    @Test fun `one number written many ways is one caller`() {
        val id = RelayCallerIdentity.identityOf(dialer, "tel:+919876543210")
        assertNotNull(id)
        assertEquals(id, RelayCallerIdentity.identityOf(dialer, "+91 98765 43210"))
        assertEquals(id, RelayCallerIdentity.identityOf(dialer, "098765 43210"))
        assertEquals(id, RelayCallerIdentity.identityOf(dialer, "(098765) 43210"))
        // The app is left out, so the same person through another app matches.
        assertEquals(id, RelayCallerIdentity.identityOf("com.whatsapp", "tel:+919876543210"))
        assertNotEquals(id, RelayCallerIdentity.identityOf(dialer, "tel:+919876543211"))
    }

    @Test fun `a lookup URI is never mistaken for a phone number`() {
        // The regression: a dialer names the caller with a contacts lookup
        // URI, whose digits are not a number. Hashing it as one made the same
        // person two callers, one from the call and one from contacts.
        val lookup = "content://com.android.contacts/contacts/lookup/2059i123456789/1234"
        val id = RelayCallerIdentity.identityOf(dialer, lookup)
        assertNotNull(id)
        assertNotEquals(RelayCallerIdentity.identityOf(dialer, "tel:1234567891234"), id)
        // An opaque handle is scoped to the app that issued it.
        assertNotEquals(RelayCallerIdentity.identityOf("com.whatsapp", lookup), id)
    }

    @Test fun `handles that are not numbers at all are still usable`() {
        assertNotNull(RelayCallerIdentity.identityOf(dialer, "Alice"))
        assertNull(RelayCallerIdentity.identityOf(dialer, ""))
        assertNull(RelayCallerIdentity.identityOf(dialer, "x".repeat(513)))
        // Too few digits to be a number, so not treated as one.
        assertNotEquals(
            RelayCallerIdentity.identityOf(dialer, "12345"),
            RelayCallerIdentity.identityOf("com.whatsapp", "12345"),
        )
    }

    @Test fun `an app can ring at its own cadence`() {
        val own = RelayRingSettings(300, 30)
        val config = RelayRuleConfig(
            apps = mapOf(
                "com.whatsapp" to RelayAppRule(true, ring = own),
                dialer to RelayAppRule(true),
            ),
            ring = RelayRingSettings(900, 60),
        )
        // Telling one app from another by feel is the whole point.
        assertEquals(own, config.ringFor("com.whatsapp"))
        assertEquals(RelayRingSettings(900, 60), config.ringFor(dialer))
        // An app nobody has a rule for still uses the shared cadence.
        assertEquals(RelayRingSettings(900, 60), config.ringFor("com.unknown"))
    }

    @Test fun `a caller's cadence outranks the app's`() {
        val urgent = RelayRingSettings(300, 120)
        val appPace = RelayRingSettings(900, 30)
        val config = RelayRuleConfig(
            apps = mapOf(dialer to RelayAppRule(true, ring = appPace)),
            callers = mapOf("alice" to SavedCaller(dialer, "Alice", ring = urgent)),
            ring = RelayRingSettings(700, 45),
        )
        // The whole point: a slow ring for everyone, a fast one for her.
        assertEquals(urgent, config.ringFor(dialer, "alice"))
        assertEquals(appPace, config.ringFor(dialer, "bob"))
        assertEquals(appPace, config.ringFor(dialer, null))
        // A saved caller without a cadence of their own falls back to the app.
        val plain = config.copy(
            callers = mapOf("alice" to SavedCaller(dialer, "Alice")),
        )
        assertEquals(appPace, plain.ringFor(dialer, "alice"))
    }

    @Test fun `a caller belongs to the app they call through`() {
        // Names are hashed with the app that reported them, so the same
        // person on WhatsApp and on the phone are two callers with two rules.
        val viaApp = RelayCallerIdentity.identityOf("com.whatsapp", "Alice")
        val viaDialer = RelayCallerIdentity.identityOf(dialer, "Alice")
        assertNotEquals(viaApp, viaDialer)
        // A number is the same person whichever app carried the call.
        assertEquals(
            RelayCallerIdentity.identityOf("com.whatsapp", "+91 98765 43210"),
            RelayCallerIdentity.identityOf(dialer, "tel:+919876543210"),
        )
    }

    @Test fun `only calls may repeat`() {
        assertTrue(RelayPreset.RING.callsOnly)
        assertFalse(RelayPreset.DOUBLE.callsOnly)
        assertFalse(RelayPreset.MUTE.callsOnly)
    }
}
