package com.srineere.airbridge

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class RelayRulePolicyTest {
    private val whatsapp = "com.whatsapp"
    private val config = RelayRuleConfig(apps = mapOf(whatsapp to RelayAppRule(true), "calendar" to RelayAppRule(true, RelayPreset.DOUBLE)),
        chats = mapOf("alice" to SavedChat(whatsapp, "Alice", preset = RelayPreset.HIGH),
            "muted" to SavedChat(whatsapp, "Muted", preset = RelayPreset.MUTE), "bob" to SavedChat(whatsapp, "Bob")))

    @Test fun `migration preserves selection and style but backup restore does not reenable stale apps`() {
        val migrated = migrateRelayRules(setOf("com.whatsapp"), "double", false)
        assertEquals(setOf("com.whatsapp"), migrated.selectedPackages)
        assertEquals(RelayPreset.DOUBLE, migrated.defaultPreset)
        assertNull(migrated.copy(everyoneElseSilent = true).match("com.whatsapp", emptySet()))
        assertTrue(migrateRelayRules(setOf("com.whatsapp"), "double", true).selectedPackages.isEmpty())
    }
    @Test fun `chat overrides app and mute wins across identity forms`() {
        assertEquals(RelayMatch(RelayPreset.HIGH, 1), config.match(whatsapp, setOf("alice")))
        assertNull(config.match(whatsapp, setOf("alice", "muted")))
        assertEquals(RelayMatch(RelayPreset.DOUBLE, 0), config.match("calendar", setOf("alice")))
        assertNull(config.match("unknown", emptySet()))
    }
    @Test fun `everyone else silent lets only saved chats through`() {
        val silent = config.copy(everyoneElseSilent = true)
        assertNull(silent.match(whatsapp, emptySet()))
        assertNull(silent.match(whatsapp, setOf("carol")))
        assertNull(silent.copy(chats = emptyMap()).match(whatsapp, setOf("alice")))
        assertEquals(RelayMatch(RelayPreset.HIGH, 1), silent.match(whatsapp, setOf("alice")))
        // A saved chat without its own vibration uses the app vibration, which
        // falls through to the shipped default of the stronger strength.
        assertEquals(RelayMatch(RelayPreset.HIGH, 0), silent.match(whatsapp, setOf("bob")))
        assertEquals(RelayMatch(RelayPreset.HIGH, 0), config.match(whatsapp, setOf("carol")))
        assertNull(config.copy(apps = mapOf(whatsapp to RelayAppRule(false))).match(whatsapp, setOf("alice")))
    }
    @Test fun `a person rule wins a burst over an app rule`() {
        val person = RelayBurstChoice(config.match(whatsapp, setOf("alice"))!!, 2)
        val app = RelayBurstChoice(config.match(whatsapp, emptySet())!!, 1)
        assertEquals(person, app.prefer(person))
    }
    private fun time(hour: Int, minute: Int = 0): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(2026, Calendar.OCTOBER, 17, hour, minute, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    @Test fun `overnight quiet hours honor inclusive start exclusive end and timezone`() {
        val silence = RelaySilence(quietEnabled = true)
        val utc = TimeZone.getTimeZone("UTC")
        assertNull(silence.reason(time(21, 59), utc))
        assertEquals("quiet_hours", silence.reason(time(22), utc))
        assertEquals("quiet_hours", silence.reason(time(6, 59), utc))
        assertNull(silence.reason(time(7), utc))
        assertEquals("quiet_hours", silence.reason(time(20), TimeZone.getTimeZone("GMT+03:00")))
    }
    @Test fun `pause expires without replay and takes precedence over quiet hours`() {
        val now = time(23)
        assertEquals("paused", RelaySilence(-1).reason(now))
        assertEquals("paused", RelaySilence(now + 1).reason(now))
        assertNull(RelaySilence(now).reason(now))
        assertEquals("quiet_hours", RelaySilence(now, true).reason(now, TimeZone.getTimeZone("UTC")))
    }
    @Test fun `daytime quiet window and disabled equal times`() {
        val silence = RelaySilence(quietEnabled = true, startMinute = 540, endMinute = 1020)
        assertEquals("quiet_hours", silence.reason(time(12), TimeZone.getTimeZone("UTC")))
        assertNull(silence.reason(time(18), TimeZone.getTimeZone("UTC")))
        assertNull(RelaySilence(startMinute = 0, endMinute = 0).reason(time(12)))
    }
    @Test fun `calls cross the quiet window only when asked and never a pause`() {
        val utc = TimeZone.getTimeZone("UTC")
        val strict = RelaySilence(quietEnabled = true)
        // Off by default: the window silences a call exactly as it always did.
        assertEquals("quiet_hours", strict.reason(time(23), utc, isCall = true))

        val open = RelaySilence(quietEnabled = true, callsStillRing = true)
        assertNull(open.reason(time(23), utc, isCall = true))
        assertEquals("quiet_hours", open.reason(time(23), utc, isCall = false))
        // Outside the window nothing is silenced either way.
        assertNull(open.reason(time(12), utc, isCall = false))

        // A pause is by hand and recent; the window is neither, so it cannot
        // overrule one.
        val paused = RelaySilence(pauseUntil = -1, quietEnabled = true, callsStillRing = true)
        assertEquals("paused", paused.reason(time(23), utc, isCall = true))
        assertEquals("paused", paused.reason(time(12), utc, isCall = true))
    }
    @Test(expected = IllegalArgumentException::class) fun `equal enabled times rejected`() { RelaySilence(quietEnabled = true, startMinute = 0, endMinute = 0) }

    // The relay schedules one wake-up for this instead of looking every
    // minute, so anything it fails to see coming is a notification that keeps
    // saying the wrong thing until something else happens to disturb it.
    @Test fun `nothing to wait for without a pause or a quiet window`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertNull(RelaySilence().nextChange(time(12), utc))
        // An indefinite pause ends when someone ends it, which comes through
        // the rules and not through a clock.
        assertNull(RelaySilence(-1).nextChange(time(12), utc))
    }

    @Test fun `a running pause waits for its own end and nothing else`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = time(12)
        // Quiet hours are on, but paused outranks them, so the next thing
        // that can change the answer is the pause running out.
        val silence = RelaySilence(pauseUntil = now + 900_000, quietEnabled = true)
        assertEquals(now + 900_000, silence.nextChange(now, utc))
    }

    @Test fun `a quiet window waits for whichever edge comes first`() {
        val utc = TimeZone.getTimeZone("UTC")
        // The shipped window: 22:00 to 07:00.
        val silence = RelaySilence(quietEnabled = true)
        assertEquals(time(22), silence.nextChange(time(12), utc))
        // Inside the quiet, the next edge is the end -- tomorrow morning.
        assertEquals(time(7) + 86_400_000, silence.nextChange(time(23), utc))
        // And just before it ends, the end today.
        assertEquals(time(7), silence.nextChange(time(6, 59), utc))
    }

    @Test fun `an expired pause falls through to the quiet window`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = time(12)
        assertEquals(time(22), RelaySilence(now, quietEnabled = true).nextChange(now, utc))
        assertNull(RelaySilence(now).nextChange(now, utc))
    }
    @Test fun `silenced notifications advance duplicate baseline and are not replayed`() {
        val filter = CalendarAlertFilter(100, selectedPackages = setOf(whatsapp))
        assertTrue(filter.accept(whatsapp, "id", 101, 102, false, false, 101))
        // Caller suppresses the accepted alert during pause, but freshness is retained.
        assertFalse(filter.accept(whatsapp, "id", 101, 105, false, false, 101))
        assertTrue(filter.accept(whatsapp, "id", 106, 107, false, false, 106))
    }
}
