package com.srineere.airbridge.health

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
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 8, 55, silenced = false))
        assertTrue(shouldNudgeForHour(nudging, fresh, 0, today, 9, 55, silenced = false))
        assertTrue(shouldNudgeForHour(nudging, fresh, 0, today, 21, 55, silenced = false))
        assertFalse(shouldNudgeForHour(nudging, fresh, 0, today, 22, 55, silenced = false))
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
}
