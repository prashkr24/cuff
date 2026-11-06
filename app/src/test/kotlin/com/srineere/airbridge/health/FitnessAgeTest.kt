package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FitnessAgeTest {

    private fun snapshot(vo2: Double?) = HealthSnapshot(
        vo2Max = vo2?.let { listOf(Reading(100L, it)) } ?: emptyList(),
    )

    private fun born(yearsAgo: Int) = FitnessProfile(
        birthYear = LocalDate.now().year - yearsAgo,
        sex = Sex.Male,
    )

    @Test fun `a VO2 max at the middle of the table reads as that age`() {
        // The reference is anchored at 25, so a 25-year-old's middle value
        // has to come back as 25 or the line is wrong at its own anchor.
        val middle = referenceVo2(25.0, Sex.Male)
        assertEquals(25, fitnessAgeOf(snapshot(middle), born(40)).years)
        val forty = referenceVo2(40.0, Sex.Male)
        assertEquals(40, fitnessAgeOf(snapshot(forty), born(25)).years)
    }

    @Test fun `the two reference tables are not the same table`() {
        // Eight or so ml per kg apart at the same age. Without the right
        // one the figure is wrong by about twenty years, which is the whole
        // reason this app asks.
        assertTrue(referenceVo2(30.0, Sex.Male) - referenceVo2(30.0, Sex.Female) > 5.0)
    }

    @Test fun `a fitter figure reads younger, and the gap is reported`() {
        val fit = fitnessAgeOf(snapshot(referenceVo2(25.0, Sex.Male) + 2.4), born(40))
        assertTrue("${fit.years}", fit.years!! < 25)
        assertTrue(fit.word.endsWith("under"))
        assertEquals(40 - fit.years!!, fit.under)
    }

    @Test fun `no VO2 max means no figure, not a guess from the other rows`() {
        val nothing = fitnessAgeOf(snapshot(null), born(31))
        assertNull(nothing.years)
        assertNull(nothing.vo2)
        assertEquals(0, nothing.points)
        assertEquals("no figure yet", nothing.word)
    }

    @Test fun `no reference chosen means no figure either`() {
        // A VO2 max with nobody to compare it to is a number, not an age.
        val anonymous = fitnessAgeOf(snapshot(44.0), FitnessProfile(birthYear = 1995))
        assertNull(anonymous.years)
        assertEquals(44.0, anonymous.vo2!!, 0.0001)
    }

    @Test fun `the line stops before it gets silly`() {
        // An extraordinary figure does not produce a fitness age of nine.
        assertTrue(fitnessAgeOf(snapshot(90.0), born(31)).years!! >= 18)
        assertTrue(fitnessAgeOf(snapshot(5.0), born(31)).years!! <= 85)
    }
}
