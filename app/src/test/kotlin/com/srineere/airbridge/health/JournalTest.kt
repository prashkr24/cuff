package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalTest {

    @Test fun `a round trip keeps both answers, and drops a tag it cannot read`() {
        val answers = mapOf(
            JournalTag.Caffeine to JournalAnswer.Yes,
            JournalTag.Alcohol to JournalAnswer.No,
        )
        assertEquals(answers, Journal.decode(Journal.encode(answers)))
        // A tag from a later version costs that answer, not the day.
        val mixed = Journal.decode("caffeine=1,moonphase=1,alcohol=0")
        assertEquals(2, mixed.size)
        assertEquals(JournalAnswer.Yes, mixed[JournalTag.Caffeine])
        assertTrue(Journal.decode(null).isEmpty())
    }

    /** Days 0..n with a score, so a finding has something to be about. */
    private fun scores(values: List<Int>): Map<Long, Int> =
        values.mapIndexed { index, value -> index.toLong() to value }.toMap()

    private fun tagged(yes: List<Int>, no: List<Int>): Map<Long, Map<JournalTag, JournalAnswer>> =
        (yes.associate { it.toLong() to mapOf(JournalTag.Caffeine to JournalAnswer.Yes) } +
            no.associate { it.toLong() to mapOf(JournalTag.Caffeine to JournalAnswer.No) })

    @Test fun `nothing is said until there are five nights each way`() {
        // Four and four, with an enormous gap: still counted, never said.
        val answers = tagged(yes = listOf(0, 1, 2, 3), no = listOf(4, 5, 6, 7))
        val (findings, counting) = analyseJournal(
            answers,
            scores(listOf(40, 40, 40, 40, 90, 90, 90, 90)),
        )
        assertTrue(findings.isEmpty())
        assertEquals(JournalTag.Caffeine, counting.single().tag)
        assertEquals(4, counting.single().yes)
        assertEquals(4, counting.single().no)
    }

    @Test fun `a gap inside the usual swing is not a finding`() {
        // Five each way, and the two means differ by less than the spread
        // of the scores themselves.
        val answers = tagged(yes = (0..4).toList(), no = (5..9).toList())
        val (findings, counting) = analyseJournal(
            answers,
            scores(listOf(60, 90, 50, 95, 55, 92, 58, 88, 62, 85)),
        )
        assertTrue(findings.isEmpty())
        assertEquals(1, counting.size)
    }

    @Test fun `a wide gap on five nights each way is said, with its lag`() {
        val answers = tagged(yes = (0..4).toList(), no = (5..9).toList())
        val (findings, _) = analyseJournal(
            answers,
            scores(listOf(62, 63, 61, 64, 62, 85, 86, 84, 87, 85)),
        )
        val found = findings.single()
        assertEquals(JournalTag.Caffeine, found.tag)
        assertEquals(0, found.lag)
        // The tagged nights scored worse, so the gap is negative.
        assertTrue(found.gap < 0)
        assertEquals(5, found.withCount)
        assertEquals(5, found.withoutCount)
    }

    @Test fun `an unanswered tag is not a no`() {
        // The whole reason answers are a map rather than a list of yeses:
        // every one of these days is a "yes", and none of them is a "no",
        // so there is nothing to compare and nothing is claimed.
        val answers = (0..9).associate {
            it.toLong() to mapOf(JournalTag.Caffeine to JournalAnswer.Yes)
        }
        val (findings, counting) = analyseJournal(answers, scores(List(10) { 60 + it }))
        assertTrue(findings.isEmpty())
        assertEquals(10, counting.single().yes)
        assertEquals(0, counting.single().no)
    }

    @Test fun `the evening answers about tonight and the morning about last night`() {
        val wednesday = java.time.LocalDate.of(2026, 11, 11).toEpochDay()
        // Half nine on Wednesday is about the night that starts in an hour,
        // which Health Connect will file under Thursday.
        assertEquals(
            wednesday + 1,
            Journal.nightFor(java.time.LocalDateTime.of(2026, 11, 11, 21, 30)),
        )
        // Eight on Thursday morning is about that same night.
        assertEquals(
            wednesday + 1,
            Journal.nightFor(java.time.LocalDateTime.of(2026, 11, 12, 8, 0)),
        )
        // Lunchtime still means the night behind you, not the one ahead.
        assertEquals(
            wednesday,
            Journal.nightFor(java.time.LocalDateTime.of(2026, 11, 11, 12, 0)),
        )
    }

    @Test fun `a tag nobody has answered is not even counted`() {
        val (findings, counting) = analyseJournal(emptyMap(), scores(List(10) { 70 }))
        assertTrue(findings.isEmpty())
        assertTrue(counting.isEmpty())
        assertNull(JournalTag.of("nothing"))
    }
}
