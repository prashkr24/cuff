package com.srineere.airbridge.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the band card calls the last buzz.
 *
 * The card showed the word "Message" for months, because the only name it
 * would accept was a saved caller's -- and an ordinary app notification
 * carries no caller at all. These pin the whole fall-through, including the
 * three buzzes the app raises itself, which arrive with no package and used
 * to be indistinguishable from a message on the card.
 *
 * `RelayState.label(entry)` makes the same decision for the Alerts screen
 * and is deliberately a second copy; if these words change, they change
 * there too.
 */
class WidgetBuzzWhoTest {

    /** Stands in for the package manager: two apps installed, nothing else. */
    private val installed = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.google.android.calendar" to "Calendar",
    )

    private fun label(packageName: String): String =
        installed[packageName] ?: packageName.substringAfterLast('.')

    private fun who(
        callerLabel: String? = null,
        sourcePackage: String? = null,
        commandId: String = "cmd-1",
    ): String = buzzWho(callerLabel, sourcePackage, commandId, ::label)

    @Test
    fun `a saved caller is named before the app that carried them`() {
        assertEquals("Priya", who(callerLabel = "Priya", sourcePackage = "com.whatsapp"))
    }

    @Test
    fun `an app notification with nobody named falls back to the app`() {
        assertEquals("WhatsApp", who(sourcePackage = "com.whatsapp"))
    }

    /** The bug as reported: this used to be null, and the card said "Message". */
    @Test
    fun `an app notification is never left without a name`() {
        assertEquals("Calendar", who(sourcePackage = "com.google.android.calendar"))
    }

    /**
     * The fallback is the last dot-segment, and that is all it is.
     *
     * `ch.threema.app` therefore reads "app", not "threema", which is a poor
     * name for a chat app. It is asserted here rather than quietly improved
     * because `RelayState.label` degrades exactly this way for an app it
     * cannot find, and the whole point of this function is that the card and
     * the Alerts list never disagree. Making this cleverer is a change to
     * both, on purpose, not a change to one.
     */
    @Test
    fun `an app that is no longer installed degrades to its last segment`() {
        assertEquals("app", who(sourcePackage = "ch.threema.app"))
        assertEquals("threema", who(sourcePackage = "ch.threema"))
    }

    @Test
    fun `the app's own two scheduled buzzes are named from the command id`() {
        assertEquals("Step goal", who(commandId = "goal-1762000000000"))
        assertEquals("Hourly nudge", who(commandId = "nudge-1762000000000"))
    }

    /** `Band.record` writes "band-$at", which is the Buzz button and nothing else. */
    @Test
    fun `anything else with no package at all is the test buzz`() {
        assertEquals("Test vibration", who(commandId = "band-1762000000000"))
    }

    /**
     * A prefix is a prefix of the id, not a word inside it. An app whose
     * package happened to end "goal" was never the risk; a command id that
     * merely contains one is.
     */
    @Test
    fun `a prefix only counts at the front of the id`() {
        assertEquals("Test vibration", who(commandId = "band-goal-1762000000000"))
    }
}
