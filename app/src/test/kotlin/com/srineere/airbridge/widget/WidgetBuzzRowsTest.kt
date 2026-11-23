package com.srineere.airbridge.widget

import com.srineere.airbridge.RelayActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bigger buzz widgets list, and what they leave out.
 *
 * The count on those faces is arrivals; the list underneath is attempts,
 * so the two can disagree and that disagreement is the useful part. The
 * rows somebody puts a widget on a home screen for are the ones that did
 * not get through — a buzz that worked already told them.
 *
 * The leaving out is the half worth testing. "Not verified" is not the
 * same as "failed": a queued entry has not finished happening, and a
 * duplicate or a cancelled one is the relay deciding not to buzz. Drawn
 * as failures those would report trouble on a home screen where the app
 * did exactly its job.
 */
class WidgetBuzzRowsTest {

    private var at = 1_763_000_000_000L

    /** Each one a minute older than the last, newest made first. */
    private fun entry(stage: String, id: String = "c$stage$at") =
        RelayActivity(
            commandId = id,
            sourcePackage = "com.whatsapp",
            stage = stage,
            atMillis = at.also { at -= 60_000 },
        )

    @Test
    fun `arrivals and failures are both listed`() {
        val rows = buzzRows(listOf(entry("verified"), entry("failed")), limit = 6) { "WhatsApp" }

        assertEquals(2, rows.size)
        assertTrue(rows[0].arrived)
        assertTrue(!rows[1].arrived)
    }

    @Test
    fun `an alert too old to send counts as one that did not arrive`() {
        // CalendarRelayService records this with no code at all: nothing
        // was ever sent, and the wrist felt nothing, which is the only
        // thing a home screen is asking about.
        val rows = buzzRows(listOf(entry("expired")), limit = 6) { "WhatsApp" }

        assertEquals(1, rows.size)
        assertTrue(!rows.single().arrived)
    }

    @Test
    fun `the relay declining to buzz is not a failure to buzz`() {
        val rows = buzzRows(
            listOf(entry("queued"), entry("duplicate"), entry("cancelled"), entry("queue_full")),
            limit = 6,
        ) { "WhatsApp" }

        assertEquals(emptyList<BuzzRow>(), rows)
    }

    @Test
    fun `newest first, whatever order they were stored in`() {
        val newest = entry("verified")
        val middle = entry("failed")
        val oldest = entry("verified")

        val rows = buzzRows(listOf(middle, oldest, newest), limit = 6) { "WhatsApp" }

        assertEquals(
            listOf(newest.atMillis, middle.atMillis, oldest.atMillis),
            rows.map { it.atMillis },
        )
    }

    /**
     * Resolving a name is a package-manager lookup, and the history holds
     * a hundred entries. Six rows must not cost a hundred of them.
     */
    @Test
    fun `no more names are resolved than there are rows`() {
        var asked = 0
        val history = (1..100).map { entry("verified") }

        val rows = buzzRows(history, limit = 6) { asked++; "WhatsApp" }

        assertEquals(6, rows.size)
        assertEquals(6, asked)
    }

    @Test
    fun `nothing stored is an empty list, not a row saying so`() {
        // The face decides what to draw for an empty day; this only
        // reports that there is nothing.
        assertEquals(emptyList<BuzzRow>(), buzzRows(emptyList(), limit = 6) { "WhatsApp" })
    }
}
