package com.srineere.airbridge.health

import android.content.Context

/**
 * A food worth typing once.
 *
 * Health Connect has no such thing. Its only food record is a
 * [androidx.health.connect.client.records.NutritionRecord], which is a thing
 * eaten at a time — there is no library, no favourites, nothing to pick from.
 * So this list is Air Bridge's own, and the screens say so rather than
 * leaving anyone to wonder why their saved foods are missing from Google
 * Health.
 */
data class SavedFood(
    val name: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
) {
    /** Two entries of the same food are the same food, whatever the figures. */
    val key: String get() = name.trim().lowercase()
}

/**
 * The saved list, as one string.
 *
 * Separated by control characters rather than commas or pipes, because a
 * food is named by a person: "Rice, dal and curd" is an ordinary name and
 * any printable delimiter would eventually be inside one. Unit and record
 * separators cannot be typed on a phone keyboard.
 */
object SavedFoods {

    private const val PREFS = "air_health"
    private const val KEY = "saved_foods"
    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    /** Enough to be useful, few enough to stay a list rather than a search. */
    const val LIMIT = 40

    fun read(context: Context): List<SavedFood> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    private fun write(context: Context, foods: List<SavedFood>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, encode(foods)).apply()
    }

    /**
     * Adds one, or replaces the one of the same name.
     *
     * Newest first: the thing you just saved is the thing you are most
     * likely to want next, and a list that only ever grows downward buries
     * it under everything you saved last month.
     */
    fun save(context: Context, food: SavedFood): List<SavedFood> {
        if (food.name.isBlank()) return read(context)
        val next = (listOf(food) + read(context).filterNot { it.key == food.key }).take(LIMIT)
        write(context, next)
        return next
    }

    fun remove(context: Context, food: SavedFood): List<SavedFood> {
        val next = read(context).filterNot { it.key == food.key }
        write(context, next)
        return next
    }

    fun has(context: Context, name: String): Boolean {
        val key = name.trim().lowercase()
        return key.isNotEmpty() && read(context).any { it.key == key }
    }

    internal fun encode(foods: List<SavedFood>): String = foods.joinToString(RECORD.toString()) {
        listOf(
            it.name.replace(FIELD, ' ').replace(RECORD, ' '),
            it.kcal.text(),
            it.protein.text(),
            it.carbs.text(),
            it.fat.text(),
        ).joinToString(FIELD.toString())
    }

    /**
     * Fail soft: a record this version cannot read costs that one food, not
     * the list. A blank figure stays blank rather than becoming zero — a
     * food with no protein recorded is not a food with no protein.
     */
    internal fun decode(stored: String?): List<SavedFood> {
        if (stored.isNullOrEmpty()) return emptyList()
        return stored.split(RECORD).mapNotNull { part ->
            val bits = part.split(FIELD)
            if (bits.size < 5) return@mapNotNull null
            val name = bits[0].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SavedFood(
                name = name,
                kcal = bits[1].number(),
                protein = bits[2].number(),
                carbs = bits[3].number(),
                fat = bits[4].number(),
            )
        }
    }

    private fun Double?.text(): String = this?.toString() ?: ""

    private fun String.number(): Double? = takeIf { it.isNotBlank() }?.toDoubleOrNull()
}
