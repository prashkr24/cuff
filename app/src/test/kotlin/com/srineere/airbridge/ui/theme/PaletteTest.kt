package com.srineere.airbridge.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    // ------------------------------------------------------- the arithmetic

    @Test
    fun `contrast matches the WCAG reference points`() {
        assertEquals(21.0f, contrast(Color.Black, Color.White), 0.01f)
        assertEquals(1.0f, contrast(Color.Red, Color.Red), 0.001f)
    }

    @Test
    fun `the accent that was failing is measurably failing`() {
        // The old light green, on the old light ground. This is the bug the
        // whole derivation exists to stop recurring: 4.26:1, under the 4.5
        // an 11px label needs.
        val oldGreen = Color(0xFF11875F)
        assertEquals(4.26f, contrast(oldGreen, Ground.Paper.base), 0.02f)
        assertFalse(passes(oldGreen, Ground.Paper.base))
    }

    // ----------------------------------------------------------- the floor

    @Test
    fun `every accent clears 4 and a half on every ground in its mode`() {
        // The promise the Appearance screen makes in words. If a swatch is
        // ever added that cannot keep it, this fails rather than shipping.
        accentSwatches.forEach { accent ->
            Ground.darks.forEach { ground ->
                assertTrue(
                    "${accent.label} on ${ground.label}: ${contrast(accent.dark, ground.base)}",
                    passes(accent.dark, ground.base),
                )
            }
            Ground.lights.forEach { ground ->
                assertTrue(
                    "${accent.label} on ${ground.label}: ${contrast(accent.light, ground.base)}",
                    passes(accent.light, ground.base),
                )
            }
        }
    }

    @Test
    fun `the dimmest text clears it on every ground too`() {
        // The constant it replaced was 4.57:1 on its own ground and 3.95:1
        // on Dusk -- under AA on a ground the app now offers.
        Ground.entries.forEach { ground ->
            val dim = mixToContrast(ground.base, ground.ink, 4.5f)
            assertTrue(
                "${ground.label}: ${contrast(dim, ground.base)}",
                contrast(dim, ground.base) >= 4.49f,
            )
        }
    }

    @Test
    fun `the awake band stays visible on every ground`() {
        // Fixed at 2E2E42 it was 1.45:1 on Ink and 1.25:1 on Dusk. Derived,
        // it holds a usable separation wherever the ground goes.
        Ground.entries.forEach { ground ->
            val extras = extrasFor(ground, Accent.Azure.colour(ground.dark), Edges.None)
            val container = if (ground.liftsTiles) lift(ground.base, ground.ink, 0.055f) else ground.base
            assertTrue(
                "${ground.label}: ${contrast(extras.sleepAwake, container)}",
                contrast(extras.sleepAwake, container) > 1.3f,
            )
        }
    }

    // ----------------------------------------------------------- the clamp

    @Test
    fun `a colour that cannot pass is lifted until it does`() {
        val tooDark = Color(0xFF7A3BE0)
        assertFalse(passes(tooDark, Ground.Dusk.base))
        val safe = clampToGround(tooDark, Ground.Dusk.base)
        assertTrue(contrast(safe, Ground.Dusk.base) >= 4.49f)
    }

    @Test
    fun `the clamp keeps the hue it was given`() {
        // The point of lifting rather than rejecting: you still get the
        // colour you asked for, only lighter.
        val picked = Color(0xFF7A3BE0)
        val safe = clampToGround(picked, Ground.Dusk.base)
        assertTrue("moved ${hueGap(picked, safe)} degrees", hueGap(picked, safe) < 6f)
    }

    @Test
    fun `a colour that already passes is left exactly alone`() {
        val fine = Accent.Azure.dark
        assertEquals(fine, clampToGround(fine, Ground.Ink.base))
    }

    @Test
    fun `clamping works in both directions`() {
        // On a light ground the fix is downward, not upward.
        val tooPale = Color(0xFFBFE8FF)
        val safe = clampToGround(tooPale, Ground.White.base)
        assertTrue(luminance(safe) < luminance(tooPale))
        assertTrue(contrast(safe, Ground.White.base) >= 4.49f)
    }

    // ------------------------------------------------------------ the rules

    @Test
    fun `no accent lands on a health colour`() {
        // Three of the six it replaced were the same hex as a chart colour.
        val health = listOf(
            DarkExtras.heart, DarkExtras.steps, DarkExtras.activity,
            DarkExtras.hrv, DarkExtras.energy,
        )
        accentSwatches.forEach { accent ->
            health.forEach { colour ->
                assertTrue(
                    "${accent.label} is the same colour as a reading",
                    accent.dark != colour,
                )
            }
        }
    }

    @Test
    fun `true black never lifts a tile`() {
        assertFalse(Ground.Pitch.liftsTiles)
        val scheme = schemeFor(Ground.Pitch, Accent.Azure.dark, Edges.None)
        assertEquals(Ground.Pitch.base, scheme.surface)
        assertEquals(Ground.Pitch.base, scheme.surfaceContainer)
    }

    @Test
    fun `a groundless card is never left without a border`() {
        // No fill and no border is no card at all, so None is lifted to
        // Defined on a ground that cannot lift its tiles.
        assertTrue(Edges.None.on(Ground.Pitch) > Edges.None.weight)
        assertEquals(Edges.None.weight, Edges.None.on(Ground.Ink), 0.0001f)
    }

    @Test
    fun `every preset names both modes`() {
        ThemePreset.entries.forEach {
            assertTrue(it.label, it.darkGround.dark)
            assertFalse(it.label, it.lightGround.dark)
        }
    }
}
