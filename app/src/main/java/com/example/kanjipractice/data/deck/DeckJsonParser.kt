package com.example.kanjipractice.data.deck

import org.json.JSONArray

/**
 * Parses the bundled deck.
 *
 * `org.json` is used rather than a serialization library because it ships with
 * Android and the schema is three fields wide; a JVM copy is on the unit-test
 * classpath so this parser is covered by ordinary tests.
 */
object DeckJsonParser {

    /** A blank standing in for the character being tested. */
    const val BLANK = "\uFF3F"

    fun parse(json: String): List<DeckCard> {
        val array = JSONArray(json)
        val cards = ArrayList<DeckCard>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val character = obj.getString("character")
            require(character.isNotEmpty()) { "card $i has an empty character" }
            cards += DeckCard(
                character = character,
                meaning = obj.getString("meaning"),
                onyomi = obj.optStringOrNull("onyomi"),
                kunyomi = obj.optStringOrNull("kunyomi"),
                exampleWord = obj.optStringOrNull("exampleWord"),
                jlpt = obj.optInt("jlpt", 0),
            )
        }
        return cards
    }

    private fun org.json.JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "")
        return value.ifBlank { null }
    }
}
