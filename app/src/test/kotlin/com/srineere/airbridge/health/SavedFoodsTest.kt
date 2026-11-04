package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedFoodsTest {

    private fun food(name: String, kcal: Double? = 100.0) =
        SavedFood(name, kcal, 1.0, 2.0, 3.0)

    @Test
    fun `a round trip keeps every figure`() {
        val foods = listOf(food("Cooked rice", 325.0), food("Rasam", 15.0))
        assertEquals(foods, SavedFoods.decode(SavedFoods.encode(foods)))
    }

    @Test
    fun `a name with punctuation in it survives`() {
        // The reason the separators are control characters: a person names
        // a food, and "Rice, dal and curd" is an ordinary thing to call one.
        val foods = listOf(SavedFood("Rice, dal | curd (big)", 420.0, null, null, null))
        assertEquals(foods, SavedFoods.decode(SavedFoods.encode(foods)))
    }

    @Test
    fun `a blank figure stays blank`() {
        // A food with no protein recorded is not a food with no protein.
        val round = SavedFoods.decode(SavedFoods.encode(listOf(SavedFood("Tea", null, null, null, null))))
        assertNull(round.single().kcal)
        assertNull(round.single().protein)
    }

    @Test
    fun `nothing stored is an empty list, not a crash`() {
        assertTrue(SavedFoods.decode(null).isEmpty())
        assertTrue(SavedFoods.decode("").isEmpty())
    }

    @Test
    fun `a record it cannot read costs that food, not the list`() {
        val good = SavedFoods.encode(listOf(food("Rasam")))
        val mixed = "nonsense" + '\u001E' + good
        assertEquals(listOf(food("Rasam")), SavedFoods.decode(mixed))
    }

    @Test
    fun `the same food twice is one food`() {
        // Saving again is correcting the figures, not making a second entry.
        assertEquals("cooked rice", food("  Cooked Rice ").key)
        assertEquals(food("Cooked rice").key, food("COOKED RICE").key)
    }
}
