package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSourcesTest {

    private val band = HealthSource("com.google.android.apps.fitness", "Google Health", isPhone = false)
    private val garmin = HealthSource("com.garmin.android.apps.connectmobile", "Garmin Connect", isPhone = false)
    private val phone = HealthSource("com.android.health.synthetic.ABC123", "This phone", isPhone = true)

    // ------------------------------------------------------------- storing

    @Test
    fun `nothing stored means wearables, not everything`() {
        // The whole point of the change: an app that has never been told
        // counts the band and not the phone.
        assertEquals(SourceChoice.Wearables, SourceChoice.decode(null))
        assertEquals(SourceChoice.Wearables, SourceChoice.decode(""))
    }

    @Test
    fun `every choice survives a round trip`() {
        listOf(
            SourceChoice.Wearables,
            SourceChoice.Everything,
            SourceChoice.Only("com.garmin.android.apps.connectmobile"),
        ).forEach { choice ->
            assertEquals(choice, SourceChoice.decode(SourceChoice.encode(choice)))
        }
    }

    @Test
    fun `the sentinels cannot be mistaken for a package`() {
        // Neither is a legal package name, so no real writer can decode as
        // one of the two blanket choices.
        val sentinels = listOf(
            SourceChoice.encode(SourceChoice.Wearables),
            SourceChoice.encode(SourceChoice.Everything),
        )
        sentinels.forEach { assertTrue("'$it' looks like a package", it.startsWith("*")) }
    }

    // ----------------------------------------------------------- resolving

    @Test
    fun `everything does not filter at all`() {
        assertNull(resolveSources(SourceChoice.Everything, listOf(band, phone)))
    }

    @Test
    fun `only names its one package, whatever is present`() {
        assertEquals(
            setOf(garmin.packageName),
            resolveSources(SourceChoice.Only(garmin.packageName), listOf(band, phone)),
        )
    }

    @Test
    fun `wearables drops the phone and keeps the rest`() {
        assertEquals(
            setOf(band.packageName, garmin.packageName),
            resolveSources(SourceChoice.Wearables, listOf(band, garmin, phone)),
        )
    }

    @Test
    fun `wearables does not filter when no phone is writing`() {
        // Nothing to exclude, so the read stays on the path it has always
        // been on rather than paying for a filter that changes nothing.
        assertNull(resolveSources(SourceChoice.Wearables, listOf(band, garmin)))
        assertNull(resolveSources(SourceChoice.Wearables, emptyList()))
    }

    @Test
    fun `a phone on its own resolves to nothing, not to everything`() {
        // The trap this whole shape exists for. Health Connect reads an empty
        // dataOriginFilter as EVERY origin, so "exclude the only writer there
        // is" must come back as an empty set the reader short-circuits on --
        // never as null, which would hand back the phone's steps as the
        // band's.
        val resolved = resolveSources(SourceChoice.Wearables, listOf(phone))
        assertEquals(emptySet<String>(), resolved)
        assertTrue("an empty filter must not be null", resolved != null)
    }
}
