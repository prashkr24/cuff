package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReadinessTest {

    private fun night(day: Long, minutes: Long) = Night(
        day = day,
        start = Instant.EPOCH,
        end = Instant.EPOCH.plus(Duration.ofMinutes(minutes)),
        asleep = Duration.ofMinutes(minutes),
        awake = Duration.ZERO,
        light = Duration.ofMinutes(minutes),
        deep = Duration.ZERO,
        rem = Duration.ZERO,
    )

    /** A person with [days] of ordinary readings, varying a little. */
    private fun ordinary(days: Int): HealthSnapshot = HealthSnapshot(
        hrv = (0 until days).map { Reading(it.toLong(), 40.0 + it % 5) },
        restingHeartRate = (0 until days).map { Reading(it.toLong(), 56.0 + it % 3) },
        nights = (0 until days).map { night(it.toLong(), 420L + it % 40) },
        steps = (0 until days).map { Reading(it.toLong(), 8000.0 + (it % 7) * 400) },
    )

    @Test fun `the figure is withheld until there are sixty nights`() {
        val young = readinessOf(ordinary(20))
        assertNull(young.score)
        assertTrue(young.calibrating)
        assertEquals(20, young.days)
        // The parts are measurements either way, so they are still there.
        assertTrue(young.standing.isNotEmpty())
    }

    @Test fun `sixty nights earns a figure, and it sits near fifty`() {
        val grown = readinessOf(ordinary(70))
        val score = grown.score
        assertNotNull(score)
        // Fifty is "like you usually are", so an ordinary day lands near it.
        assertTrue("$score", score!! in 30..70)
        assertNotNull(grown.trend)
    }

    @Test fun `a morning with nothing read scores nothing and claims nothing`() {
        val empty = readinessOf(HealthSnapshot())
        assertNull(empty.score)
        assertEquals(0, empty.days)
        assertTrue(empty.standing.isEmpty())
        // Every part keeps its row and says it is missing.
        assertEquals(4, empty.parts.size)
        assertTrue(empty.parts.none { it.present })
    }

    @Test fun `a low HRV morning scores under an ordinary one`() {
        val base = ordinary(70)
        // The same person, whose last morning came back well below usual.
        val dip = base.copy(
            hrv = base.hrv.dropLast(1) + Reading(69L, 20.0),
        )
        val ordinaryScore = readinessOf(base).score!!
        val dipScore = readinessOf(dip).score!!
        assertTrue("$dipScore vs $ordinaryScore", dipScore < ordinaryScore)
    }

    @Test fun `walking today does not change this morning's figure`() {
        // The load ratio ends at yesterday on purpose. Today's steps grow
        // all day, and a figure about how you woke up must not climb
        // because of what you did after -- not least because the morning
        // report has already sent the earlier number.
        val morning = ordinary(70)
        val evening = morning.copy(
            steps = morning.steps.dropLast(1) + Reading(69L, 25_000.0),
        )
        assertEquals(readinessOf(morning).score, readinessOf(evening).score)
    }

    @Test fun `a part with no reading moves the figure by nothing`() {
        val noSleep = ordinary(70).copy(nights = emptyList())
        val readiness = readinessOf(noSleep)
        val sleep = readiness.parts.single { it.name == "Last night" }
        assertEquals(0.0, sleep.push, 0.0001)
        // And the total is still computed from what is left.
        assertNotNull(readiness.score)
    }
}
