package com.srineere.airbridge.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MealGroupsTest {

    private fun entry(id: String, mealType: Int, source: String? = null) = FoodEntry(
        id = id,
        at = Instant.EPOCH,
        day = 0,
        name = id,
        kcal = 100.0,
        protein = null,
        carbs = null,
        fat = null,
        mealType = mealType,
        source = source,
    )

    @Test fun `every entry comes out exactly once, whatever its meal type`() {
        // The bug: an entry Health Connect filed under no meal matched none
        // of the four groups and vanished, while the day's total went on
        // counting it. MEAL_TYPE_UNKNOWN is zero; 99 is a type this version
        // has never heard of, which must behave the same way.
        val entries = listOf(
            entry("breakfast", Meal.Breakfast.type),
            entry("unknown", 0),
            entry("future", 99),
            entry("dinner", Meal.Dinner.type),
        )
        val grouped = mealGroups(entries).flatMap { it.second }
        assertEquals(entries.size, grouped.size)
        assertEquals(entries.map { it.id }.toSet(), grouped.map { it.id }.toSet())
    }

    @Test fun `the meals keep their own order, and the rest come last`() {
        val labels = mealGroups(emptyList()).map { it.first }
        assertEquals(Meal.entries.map { it.label } + "Other", labels)
    }

    @Test fun `an entry is ours only when we wrote it`() {
        val mine = entry("mine", 0, source = "com.srineere.airbridge")
        val theirs = entry("theirs", 0, source = "com.myfitnesspal.android")
        assertTrue(mine.isOurs("com.srineere.airbridge"))
        assertFalse(theirs.isOurs("com.srineere.airbridge"))
        // No source at all is treated as ours: it is what this app's own
        // writes looked like before the field existed, and refusing to
        // delete those would break the one case that certainly works.
        assertTrue(entry("old", 0).isOurs("com.srineere.airbridge"))
    }
}
