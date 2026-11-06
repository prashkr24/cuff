package com.srineere.airbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Today is two different things depending on what is being counted.
 *
 * A sleep session is filed under the day it ended, so a night filed under
 * today is last night. A day's steps, a resting rate or a VO2 max read
 * under today is today. The one label said "Last night" to everybody, and
 * it is shared by every chart's readout -- so scrubbing to today's bar on
 * the Steps screen printed "Last night 8,412 steps" three lines under an
 * axis tick that said "Today".
 */
class DayLabelTest {

    private val today = LocalDate.now().toEpochDay()

    @Test fun `today is today unless the caller is counting nights`() {
        assertEquals("Today", dayLabel(today))
        assertEquals("Last night", dayLabel(today, "Last night"))
    }

    @Test fun `any other day is dated, whatever it was asked to call today`() {
        val dated = dayLabel(today - 3)
        assertEquals(dated, dayLabel(today - 3, "Last night"))
        assertEquals(LocalDate.now().minusDays(3).dayOfMonth.toString(), dated.split(" ")[1])
    }
}
