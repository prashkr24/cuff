package com.srineere.airbridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The memo that keeps a ringing dialer from re-querying contacts once a
 * second on the thread that draws the UI.
 *
 * The provider round trips themselves need a device, but the thing that
 * can actually go wrong here does not: whether a second ask is free,
 * whether a null answer is remembered, whether the bound holds, and above
 * all whether forgetting really forgets. That last one is the whole
 * design -- a memo that quietly outlived the ring would be a cache of
 * stale phone numbers.
 */
class RelayNumberMemoTest {

    private var asked = 0

    private fun ask(handle: String, answer: String? = "+15550101"): String? =
        RelayNumberMemo.of(handle) { asked++; answer }

    @Before fun clean() {
        RelayNumberMemo.forget()
        asked = 0
    }

    @After fun tidy() = RelayNumberMemo.forget()

    @Test fun `the second ask costs nothing`() {
        assertEquals("+15550101", ask("content://contacts/1"))
        assertEquals("+15550101", ask("content://contacts/1"))
        assertEquals("+15550101", ask("content://contacts/1"))
        assertEquals(1, asked)
    }

    @Test fun `two callers are two lookups`() {
        ask("content://contacts/1")
        ask("content://contacts/2")
        assertEquals(2, asked)
    }

    @Test fun `nothing found is remembered too`() {
        // The expensive answer, and the ordinary one for somebody who is
        // not in contacts: both queries run to arrive at it.
        assertNull(ask("content://contacts/9", answer = null))
        assertNull(ask("content://contacts/9", answer = null))
        assertEquals(1, asked)
    }

    @Test fun `forgetting really forgets`() {
        ask("content://contacts/1")
        RelayNumberMemo.forget()
        ask("content://contacts/1")
        assertEquals(2, asked)
    }

    @Test fun `it is bounded, oldest out first`() {
        // One past the bound, so the first handle is the one evicted.
        repeat(RelayNumberMemo.LIMIT + 1) { ask("content://contacts/$it") }
        assertEquals(RelayNumberMemo.LIMIT + 1, asked)
        // The newest is still there...
        ask("content://contacts/${RelayNumberMemo.LIMIT}")
        assertEquals(RelayNumberMemo.LIMIT + 1, asked)
        // ...and the oldest has gone.
        ask("content://contacts/0")
        assertEquals(RelayNumberMemo.LIMIT + 2, asked)
    }
}
