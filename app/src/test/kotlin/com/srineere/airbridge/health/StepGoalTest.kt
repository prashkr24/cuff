package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepGoalTest {

    private val on = StepGoalSettings(goal = 10_000, buzzOnGoal = true)
    private val fresh = StepGoalHistory()
    private val today = 20_000L

    // ---------------------------------------------------------- daily goal

    @Test
    fun `crossing the goal buzzes once`() {
        assertTrue(shouldBuzzForGoal(on, fresh, 10_000, today, silenced = false))
    }

    @Test
    fun `short of the goal does not buzz`() {
        assertFalse(shouldBuzzForGoal(on, fresh, 9_999, today, silenced = false))
    }

    @Test
    fun `it does not buzz twice in a day`() {
        val fired = StepGoalHistory(goalFiredDay = today)
        assertFalse(shouldBuzzForGoal(on, fired, 12_000, today, silenced = false))
        // but tomorrow is a new day
        assertTrue(shouldBuzzForGoal(on, fired, 12_000, today + 1, silenced = false))
    }

    @Test
    fun `silence beats the goal`() {
        // Quiet hours and pause outrank a step count, exactly as they
        // outrank a person's own vibration.
        assertFalse(shouldBuzzForGoal(on, fresh, 12_000, today, silenced = true))
    }

    @Test
    fun `a missed crossing is missed, not queued`() {
        // Crossed while silenced, still silenced later in the day: nothing
        // fires, and when the quiet ends the news is stale rather than owed.
        assertFalse(shouldBuzzForGoal(on, fresh, 12_000, today, silenced = true))
        // Once the quiet lifts on the same day it does fire, which is the
        // one case worth having: the goal still stands.
        assertTrue(shouldBuzzForGoal(on, fresh, 12_000, today, silenced = false))
    }

    @Test
    fun `switched off buzzes for nothing`() {
        assertFalse(shouldBuzzForGoal(on.copy(buzzOnGoal = false), fresh, 50_000, today, false))
    }

    @Test
    fun `a goal of zero is not a goal`() {
        assertFalse(shouldBuzzForGoal(on.copy(goal = 0), fresh, 0, today, silenced = false))
    }

    // -------------------------------------------------------- hourly nudge

    private val nudging = StepGoalSettings(
        hourlyNudge = true,
        hourlyGoal = 250,
        activeFrom = 9,
        activeUntil = 21,
    )

    @Test
    fun `nudges in the last ten minutes of a short hour`() {
        assertTrue(shouldNudgeForHour(nudging, fresh, 100, today, 14, 50, silenced = false))
        assertTrue(shouldNudgeForHour(nudging, fresh, 100, today, 14, 59, silenced = false))
    }

    @Test
    fun `does not nudge earlier in the hour`() {
        // The hour is still fixable, so there is nothing to say yet.
        assertFalse(shouldNudgeForHour(nudging, fresh, 100, today, 14, 49, silenced = false))
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 14, 5, silenced = false))
    }

    @Test
    fun `a met hour is left alone`() {
        assertFalse(shouldNudgeForHour(nudging, fresh, 250, today, 14, 55, silenced = false))
        assertFalse(shouldNudgeForHour(nudging, fresh, 900, today, 14, 55, silenced = false))
    }

    @Test
    fun `the active window is respected at both ends`() {
        // The worst version of this feature wakes you at 3am to say you have
        // not walked.
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 3, 55, silenced = false))
        // The start is inclusive: 09:50 is inside "09:00-21:00".
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 8, 55, silenced = false))
        assertTrue(shouldNudgeForHour(nudging, fresh, 0, today, 9, 55, silenced = false))
        // The end is exclusive, and this is the half that was wrong. Hour 21
        // used to pass, so with activeUntil = 21 the band buzzed at 21:50 --
        // fifty minutes after the end of the window the Alerts tab prints
        // and the person chose. The last nudged hour is 20, at 20:50.
        assertTrue(shouldNudgeForHour(nudging, fresh, 0, today, 20, 55, silenced = false))
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 21, 55, silenced = false))
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 22, 55, silenced = false))
    }

    @Test
    fun `the narrowest window the picker allows still has one hour in it`() {
        // The Goals screen clamps activeUntil to at least activeFrom + 1, so
        // the tightest pair is an hour apart. Exclusive at the end, that is
        // exactly one nudge, at 09:50 -- not none, which is what an
        // exclusive end would mean for a window of zero width.
        val narrow = nudging.copy(activeFrom = 9, activeUntil = 10)
        assertTrue(shouldNudgeForHour(narrow, fresh, 0, today, 9, 55, silenced = false))
        assertFalse(shouldNudgeForHour(narrow, fresh, 0, today, 10, 55, silenced = false))
    }

    @Test
    fun `one nudge an hour`() {
        val fired = StepGoalHistory(nudgeFiredDay = today, nudgeFiredHour = 14)
        assertFalse(shouldNudgeForHour(nudging, fired, 0, today, 14, 55, silenced = false))
        // the next hour is fair game
        assertTrue(shouldNudgeForHour(nudging, fired, 0, today, 15, 55, silenced = false))
        // and so is the same hour tomorrow
        assertTrue(shouldNudgeForHour(nudging, fired, 0, today + 1, 14, 55, silenced = false))
    }

    @Test
    fun `silence beats the nudge too`() {
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 14, 55, silenced = true))
    }

    @Test
    fun `switched off nudges for nothing`() {
        assertFalse(
            shouldNudgeForHour(nudging.copy(hourlyNudge = false), fresh, 0, today, 14, 55, false),
        )
    }

    // ------------------------------------------------- can the band be hit

    @Test
    fun `everything in place means a pulse can be sent`() {
        assertTrue(canPulseBand(ready = true, stopping = false, bandLinked = true, measuring = false))
    }

    @Test
    fun `a live reading holds the band, so nothing else may pulse it`() {
        // The bug this rule exists for. The band has one secure session; a
        // heart rate reading holds it for up to two minutes and the step
        // goal is checked every fifteen, so crossing the goal mid-reading is
        // not a rare race.
        assertFalse(canPulseBand(ready = true, stopping = false, bandLinked = true, measuring = true))
    }

    @Test
    fun `no link, not started, or shutting down all mean no`() {
        assertFalse(canPulseBand(ready = true, stopping = false, bandLinked = false, measuring = false))
        assertFalse(canPulseBand(ready = false, stopping = false, bandLinked = true, measuring = false))
        assertFalse(canPulseBand(ready = true, stopping = true, bandLinked = true, measuring = false))
    }

    @Test
    fun `the goal is still owed while it cannot be sent`() {
        // The invariant the fix is about, spelled out: a day on which the
        // pulse could never be sent must not look like a day it fired. The
        // service writes the "fired" record only when canPulseBand was true,
        // so an unsendable goal stays unfired and is tried again -- and
        // shouldBuzzForGoal keeps saying yes until it is.
        val history = StepGoalHistory()
        assertFalse(canPulseBand(ready = true, stopping = false, bandLinked = true, measuring = true))
        assertTrue(shouldBuzzForGoal(on, history, 12_000, today, silenced = false))
    }

    // ------------------------------------------------- when the next check is

    private val quarter = 15 * 60 * 1000L

    @Test
    fun `every starting minute reaches the nudge window within the hour`() {
        // The bug: a flat quarter-hour timer samples the same four minutes
        // of every hour -- whichever it started on, plus 15, 30 and 45 --
        // and the nudge only answers yes from minute 50. For a third of the
        // starting phases none of those four is ever inside the window, so
        // the nudge never fired once. Not rarely. Never, and silently.
        for (start in 0..59) {
            var minute = start
            var reached = false
            // Four hours of checks is far more than an hour's worth.
            repeat(32) {
                if (minute % 60 >= NUDGE_FROM_MINUTE) reached = true
                val wait = nextStepCheckMillis(hourlyNudge = true, minute = minute % 60, second = 0, periodMillis = quarter)
                minute += (wait / 60_000L).toInt()
            }
            assertTrue("starting at :$start never reaches the nudge window", reached)
        }
    }

    @Test
    fun `the wait lands on the window when the window comes first`() {
        // Forty past: ten minutes to :50, nearer than the quarter hour, so
        // the wait is shortened onto the window.
        assertEquals(10 * 60 * 1000L, nextStepCheckMillis(true, minute = 40, second = 0, periodMillis = quarter))
        // Twelve past: thirty-eight minutes away, so the quarter hour comes
        // first and is kept -- the daily goal wants checking whether or not
        // the hour does.
        assertEquals(quarter, nextStepCheckMillis(true, minute = 12, second = 0, periodMillis = quarter))
        // Seconds count, so a check does not land a moment early.
        assertEquals(9 * 60 * 1000L + 30_000L, nextStepCheckMillis(true, minute = 40, second = 30, periodMillis = quarter))
    }

    @Test
    fun `inside the window the next one is an hour on, and the goal keeps its own period`() {
        // At :55 the window is already open; the next one starts at :50 of
        // the following hour, which is further off than the quarter hour.
        assertEquals(quarter, nextStepCheckMillis(true, minute = 55, second = 0, periodMillis = quarter))
        // With the nudge off nothing is shortened at all.
        assertEquals(quarter, nextStepCheckMillis(false, minute = 12, second = 0, periodMillis = quarter))
    }
}
