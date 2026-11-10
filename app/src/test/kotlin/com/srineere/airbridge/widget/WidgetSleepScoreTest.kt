package com.srineere.airbridge.widget

import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.TileKind
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.sampleGoals
import com.srineere.airbridge.ui.sampleHealthData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The score on the stages card, and the night it refuses to put one on.
 *
 * The figure itself belongs to `SleepScore` and is tested there. What is
 * worth pinning here is that the widget borrows it rather than working it
 * out again -- two sleep cards on one home screen disagreeing about last
 * night is the kind of fault nobody reports -- and that a night it cannot
 * be computed for carries no figure and no label, rather than a nought or
 * a lone word with nothing above it.
 */
class WidgetSleepScoreTest {

    private val health = sampleHealthData()
    private val snapshot = WidgetSnapshot(health = health, goals = sampleGoals)

    @Test
    fun `the stages card carries last night's score`() {
        val face = faceFor(TileKind.Stages, snapshot)!!
        assertEquals(health.sleepScore!!.total!!.toString(), face.trailingFigure)
        assertEquals("score", face.trailingLabel)
    }

    /**
     * Both cards score the same night against the same nights, because both
     * read it off the one [WidgetSnapshot] the home screen was built from.
     */
    @Test
    fun `the stages card and the sleep card agree about the score`() {
        assertEquals(
            faceFor(TileKind.Sleep, snapshot)!!.trailingFigure,
            faceFor(TileKind.Stages, snapshot)!!.trailingFigure,
        )
    }

    /**
     * A session of no length scores on nothing: duration, stages and
     * consistency all want nights behind them, and interruptions wants a
     * time in bed to divide by.
     */
    @Test
    fun `a night nothing can be measured on shows no score and no label`() {
        val at = Instant.now()
        val unmeasurable = Night(
            day = LocalDate.now().toEpochDay(),
            start = at,
            end = at,
            asleep = Duration.ZERO,
            awake = Duration.ZERO,
            light = Duration.ZERO,
            deep = Duration.ZERO,
            rem = Duration.ZERO,
        )
        val lonely = WidgetSnapshot(health = HealthData(nights = listOf(unmeasurable)))
        val face = faceFor(TileKind.Stages, lonely)
        // The card is still a card: it has the night, it simply has no
        // score for it.
        assertNotNull(face)
        assertNull(face!!.trailingFigure)
        assertNull(face.trailingLabel)
    }

    /** No night at all is the empty tile's job, not a dash where a score goes. */
    @Test
    fun `no night at all leaves no stages card to put a score on`() {
        assertNull(faceFor(TileKind.Stages, WidgetSnapshot(health = HealthData())))
    }
}
