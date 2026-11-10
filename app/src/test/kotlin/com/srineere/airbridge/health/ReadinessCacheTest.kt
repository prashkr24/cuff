package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * What the kept morning is keyed on, and what throws it away.
 *
 * This is the half of readiness that goes wrong quietly. A figure keyed a
 * day out serves yesterday's number under this morning's heading and
 * nothing about it looks broken; a figure kept before the night landed is
 * three quarters made and reads exactly like a whole one.
 */
class ReadinessCacheTest {

    /** An arbitrary epoch day, so none of this depends on when it is run. */
    private val today = 20_000L

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

    /** [nights] days of readiness ending on [day]. */
    private fun kept(day: Long, nights: Int, standing: Int = 4) = KeptReadiness(
        day = day,
        series = (0 until nights).map { Reading(day - nights + 1 + it, 48.0 + it % 5) },
        standing = standing,
    )

    @Test fun `a kept morning is this one or the one before, and nothing else`() {
        assertTrue(isCurrent(today, today))
        // The band has not synced yet, so the newest figure there is belongs
        // to yesterday -- which is the honest answer, not a stale one.
        assertTrue(isCurrent(today - 1, today))
        assertFalse(isCurrent(today - 2, today))
        // A phone carried west produces one of these, and a morning that
        // has not happened cannot have a figure.
        assertFalse(isCurrent(today + 1, today))
    }

    @Test fun `the first figure of a morning is always kept`() {
        assertTrue(supersedes(kept(today, 70), null, today))
        assertTrue(supersedes(kept(today - 1, 70), null, today))
        // Older than yesterday is somebody else's morning.
        assertFalse(supersedes(kept(today - 2, 70), null, today))
        assertFalse(supersedes(null, null, today))
    }

    @Test fun `the night landing late replaces the figure it was left out of`() {
        // Twenty past six: HRV has synced, the night has not, and the
        // figure is three quarters made. Keyed by the day alone -- which is
        // all that was kept before -- it would be served until midnight.
        val threeQuarters = kept(today, 118, standing = 3)
        val whole = kept(today, 118, standing = 4)
        assertTrue(supersedes(whole, threeQuarters, today))
        // And not the other way about. A part that has gone away does not
        // cost the morning the best answer anything had about it.
        assertFalse(supersedes(threeQuarters, whole, today))
    }

    @Test fun `the long read replaces the short one, and never the reverse`() {
        val short = kept(today, 30)
        val long = kept(today, 120)
        assertTrue(supersedes(long, short, today))
        assertFalse(supersedes(short, long, today))
        // Nothing has moved, so nothing is rewritten.
        assertFalse(supersedes(long, kept(today, 120), today))
    }

    @Test fun `a new morning does not replace a figure with no figure`() {
        // The widgets read thirty days unless a readiness card is on the
        // home screen, and thirty is below the sixty-reading gate. Without
        // this rule the half-hourly widget turn would replace a real figure
        // with "calibrating" every morning and leave it there until
        // something widened the window again.
        val yesterdays = kept(today - 1, 120)
        val calibrating = kept(today, 30)
        assertNotNull(yesterdays.score)
        assertNull(calibrating.score)
        assertFalse(supersedes(calibrating, yesterdays, today))
        // A real figure for today takes over the moment there is one.
        assertTrue(supersedes(kept(today, 120), yesterdays, today))
        // And somebody who is genuinely still calibrating is not held on
        // yesterday's morning for ever.
        assertTrue(supersedes(calibrating, kept(today - 1, 30), today))
    }

    @Test fun `a kept figure is withheld on the same sixty nights the screen is`() {
        assertNull(kept(today, Readiness.NEEDED - 1).score)
        assertNotNull(kept(today, Readiness.NEEDED).score)
    }

    @Test fun `the series survives the preferences file it is kept in`() {
        val original = listOf(
            Reading(19_990L, 48.3),
            Reading(19_991L, 51.0),
            Reading(19_992L, 50.75),
        )
        // One decimal, and a full stop wherever the phone is. A comma here
        // reads back as nothing at all, which is half of Europe.
        assertEquals("19990:48.3;19991:51.0;19992:50.8", encodeSeries(original))
        val back = decodeSeries(encodeSeries(original))
        assertEquals(3, back.size)
        assertEquals(19_990L, back[0].day)
        assertEquals(48.3, back[0].value, 0.0001)
        assertEquals(50.8, back[2].value, 0.0001)
    }

    @Test fun `a line that does not parse is thrown away whole`() {
        // Never half a series: a short one is a person with fewer nights,
        // so a silently truncated one would read as somebody calibrating
        // rather than as a store to throw away.
        assertTrue(decodeSeries("19990:48.3;rubbish;19992:50.0").isEmpty())
        assertTrue(decodeSeries("19990").isEmpty())
        assertTrue(decodeSeries("").isEmpty())
    }

    @Test fun `what is kept is the series, and how much stood behind it`() {
        val worked = readinessOf(ordinary(70))
        val stored = worked.asKept()!!
        assertEquals(worked.day, stored.day)
        assertEquals(worked.days, stored.series.size)
        assertEquals(worked.standing.size, stored.standing)
        assertEquals(worked.score, stored.score)
    }

    @Test fun `a morning with nothing behind it is not kept at all`() {
        assertNull(readinessOf(HealthSnapshot()).asKept())
        // Two readings can be averaged but cannot have a shape, so there is
        // no trend and nothing worth remembering.
        assertNull(readinessOf(ordinary(2)).asKept())
    }

    @Test fun `a kept morning rebuilds everything the long read decided`() {
        val series = (0 until 70).map { Reading(today - 69 + it, 48.0 + it % 5) }
        val stored = KeptReadiness(today, decodeSeries(encodeSeries(series)), 4)
        // Restored onto a morning nothing has been read for yet, which is
        // the first frame of the Readiness screen.
        val drawn = readinessOf(HealthSnapshot()).restoredFrom(stored)
        assertEquals(70, drawn.days)
        assertEquals(today, drawn.day)
        // 48 + (69 % 5), the newest point of the series.
        assertEquals(52, drawn.score)
        assertEquals(trendOf(series)!!.usual.low, drawn.trend!!.usual.low, 0.0001)
        // The four rows underneath are still this morning's own, and this
        // morning has nothing in it: a total is never shown without its
        // parts, and a part with nothing behind it says so.
        assertEquals(4, drawn.parts.size)
        assertFalse(drawn.parts.any { it.present })
    }

    @Test fun `the morning is not settled until the night and the vitals are both in`() {
        val vitals = listOf(Reading(today, 44.0))
        assertFalse(readinessInputsLanded(HealthSnapshot(hrv = vitals), today))
        assertFalse(
            readinessInputsLanded(HealthSnapshot(nights = listOf(night(today, 420))), today),
        )
        assertTrue(
            readinessInputsLanded(
                HealthSnapshot(hrv = vitals, nights = listOf(night(today, 420))),
                today,
            ),
        )
        // Yesterday's night is not this morning's. Health Connect files a
        // night under the day it ended, so last night is the one dated
        // today and a figure built on the one before is about a night its
        // reader has already got up from.
        assertFalse(
            readinessInputsLanded(
                HealthSnapshot(hrv = vitals, nights = listOf(night(today - 1, 420))),
                today,
            ),
        )
    }
}
