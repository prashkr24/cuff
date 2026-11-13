package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure halves of the health gate: what a first run of this version
 * decides about an install it has never seen, and which routes belong to
 * the half being gated.
 */
class HealthEnabledTest {

    // ----------------------------------------------------------- migration

    @Test
    fun `a fresh install comes up with the health half off`() {
        // Nothing granted, no layout stored: this is somebody who has just
        // installed the app, and the whole point of the change is that
        // they are not asked for thirteen health permissions.
        assertEquals(false, healthOnDecision(settled = false, grantedReads = 0, tilesConfigured = false))
    }

    @Test
    fun `an install that is already reading comes up with it on`() {
        // Switching off a working setup is not a default, it is a
        // regression -- so one granted reading is enough.
        assertEquals(true, healthOnDecision(settled = false, grantedReads = 1, tilesConfigured = false))
        assertEquals(true, healthOnDecision(settled = false, grantedReads = 13, tilesConfigured = false))
    }

    @Test
    fun `a stored tile layout is enough on its own`() {
        // Arranging tiles cannot happen by accident and cannot happen
        // outside the Health tab, so it is proof the half was in use --
        // and it is still proof on a phone whose Health Connect will not
        // answer this second.
        assertEquals(true, healthOnDecision(settled = false, grantedReads = 0, tilesConfigured = true))
        assertEquals(true, healthOnDecision(settled = false, grantedReads = null, tilesConfigured = true))
    }

    @Test
    fun `an answer that could not be got is not written down`() {
        // Health Connect mid-update answers nothing, and deciding "off"
        // on that and never looking again is exactly the regression this
        // whole decision exists to prevent. Null means ask again next
        // launch, which is a different thing from false.
        assertNull(healthOnDecision(settled = false, grantedReads = null, tilesConfigured = false))
    }

    @Test
    fun `once settled the question is never asked again`() {
        // Whatever the phone now says, and whichever way it was settled.
        // Without this the switch would be overruled on every launch by
        // the state of a permission the person deliberately left alone.
        assertNull(healthOnDecision(settled = true, grantedReads = 0, tilesConfigured = false))
        assertNull(healthOnDecision(settled = true, grantedReads = 13, tilesConfigured = true))
        assertNull(healthOnDecision(settled = true, grantedReads = null, tilesConfigured = false))
    }

    // -------------------------------------------------------------- routes

    /**
     * Every route the graph registers for the health half.
     *
     * Written out here and not in the app, deliberately: the app decides
     * by a rule and this is the list the rule has to get right, so a
     * health screen added without a thought for the gate shows up as a
     * line somebody has to add to a test rather than as a blank screen on
     * a phone.
     */
    private val healthRoutes = listOf(
        "health",
        "healthheart", "healthenergy", "healthsleep", "healthsleepbank", "healthsteps",
        "healthvitals", "healthactivity", "healthsessions", "healthsession/{start}",
        "healthtiles", "healthgoals", "healthhrv", "healthnutrition", "healthsavedfoods",
        "healthfoodlog", "healthlogfood", "healthbalance", "healthtrend", "healthweight",
        "healthbreathing", "healthskin",
        // The five that are not named after the half they belong to.
        "journal", "readiness", "fitnessage", "morning", "export",
    )

    /** Everything else the graph registers, which is the relay's side. */
    private val relayRoutes = listOf(
        "alerts", "device", "apps", "calls", "people", "activity", "schedule",
        "person/{id}", "contacts", "contacts/{source}", "callers/{group}",
        "callers/{group}/{scope}", "appearance", "widgets", "permissions", "about",
        "appcallers/{packageName}", "vibration/{target}",
    )

    @Test
    fun `every health route is recognised as one`() {
        healthRoutes.forEach { assertTrue(it, isHealthRoute(it)) }
    }

    @Test
    fun `nothing on the relay side is mistaken for health`() {
        // The Alerts tab and the Device tab in particular: those two are
        // where a refused health route lands, and a gate that turned them
        // away as well would be a loop rather than a redirect.
        relayRoutes.forEach { assertFalse(it, isHealthRoute(it)) }
    }

    @Test
    fun `an absent route is not a health route`() {
        assertFalse(isHealthRoute(null))
        assertFalse(isHealthRoute(""))
    }

    @Test
    fun `the tile kinds all open a route the gate knows`() {
        // The tiles carry their own routes, so a kind added with a screen
        // of its own would otherwise be reachable from a widget while the
        // half is off.
        TileKind.entries.forEach { assertTrue(it.name, isHealthRoute(it.route)) }
    }

    @Test
    fun `the journal is hidden and nothing else is`() {
        assertTrue(isHiddenRoute("journal"))
        assertTrue(isHiddenRoute(JournalReminder.ROUTE))
        assertFalse(isHiddenRoute(MorningMoment.ROUTE))
        assertFalse(isHiddenRoute("healthsleep"))
        assertFalse(isHiddenRoute(null))
    }

    @Test
    fun `the journal is hidden rather than deleted`() {
        // The flag is what the Health tab's missing row and the refused
        // route both answer to. If somebody turns it off, everything the
        // feature needs is still here and the two call sites named in its
        // comment are what bring it back.
        assertTrue(JournalReminder.hidden)
    }
}
