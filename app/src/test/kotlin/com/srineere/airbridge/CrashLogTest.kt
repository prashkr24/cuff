package com.srineere.airbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file handling needs a phone; the arithmetic that decides what survives
 * does not, and it is the half that can lose somebody's crash.
 */
class CrashLogTest {

    private val sep = '\u001e'

    private fun entriesOf(text: String) =
        text.split(sep).map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `the first entry is kept as it is`() {
        assertEquals("boom", CrashLog.merge(emptyList(), "boom"))
    }

    @Test
    fun `entries are separated by the record character`() {
        val text = CrashLog.merge(listOf("one"), "two")
        assertEquals(listOf("one", "two"), entriesOf(text))
    }

    @Test
    fun `a stack trace with blank lines in it survives a round trip`() {
        // The reason the separator is a record character and not a blank
        // line: a trace contains blank lines, and a delimiter that appears
        // inside the thing it delimits is not a delimiter.
        val trace = "java.lang.IllegalStateException: no\n\n\tat A.kt:1\n\n\tat B.kt:2"
        val text = CrashLog.merge(emptyList(), trace)
        assertEquals(listOf(trace), entriesOf(text))
    }

    @Test
    fun `the newest entry is never the one dropped`() {
        // A crash loop writes the same trace over and over. Whatever else is
        // lost, the entry being written is the one somebody is asking about.
        val huge = "x".repeat(200_000)
        val text = CrashLog.merge(listOf(huge), "the one that matters")
        assertTrue(text.endsWith("the one that matters"))
    }

    @Test
    fun `an oversized history is trimmed from the front`() {
        val big = (1..40).map { "entry $it " + "y".repeat(10_000) }
        val text = CrashLog.merge(big, "newest")
        val kept = entriesOf(text)
        assertTrue("should have dropped some", kept.size < big.size + 1)
        assertEquals("newest", kept.last())
        // What survives is a tail of the input, in order -- never a shuffle.
        val expectedTail = (big + "newest").takeLast(kept.size)
        assertEquals(expectedTail, kept)
    }

    @Test
    fun `the count cap holds before the size cap has to`() {
        val many = (1..100).map { "small $it" }
        val kept = entriesOf(CrashLog.merge(many, "newest"))
        assertTrue("kept ${kept.size}", kept.size <= 20)
        assertEquals("newest", kept.last())
    }
}
