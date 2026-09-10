package com.example.kanjipractice.data.deck

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled deck is data the app cannot work without, so it is validated as a
 * build artifact rather than trusted.
 */
class DeckJsonParserTest {

    private val deck: List<DeckCard> by lazy {
        DeckJsonParser.parse(asset("kanji.json").readText())
    }

    @Test
    fun bundledDeckIsBigEnoughToBeUseful() {
        assertTrue(deck.size >= 200, "deck has only ${deck.size} cards")
    }

    @Test
    fun everyCardIsASingleCharacterWithAMeaning() {
        for (card in deck) {
            assertEquals(
                1,
                card.character.codePointCount(0, card.character.length),
                "multi-character entry: ${card.character}",
            )
            assertTrue(card.meaning.isNotBlank(), "no meaning for ${card.character}")
        }
    }

    @Test
    fun charactersAreUnique() {
        val duplicates = deck.groupBy { it.character }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate characters: $duplicates")
    }

    @Test
    fun cardIdsAreTheCodePointSoSeedingIsIdempotent() {
        val ids = deck.map { it.character.codePointAt(0).toLong() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun exampleWordsNeverLeakTheAnswer() {
        // Section 6.1: the prompt shows the example word with the target blanked.
        for (card in deck) {
            val example = card.exampleWord ?: continue
            assertTrue(
                card.character !in example,
                "${card.character} is visible in its own example word: $example",
            )
        }
    }

    @Test
    fun readingsArePlausibleWhenPresent() {
        for (card in deck) {
            card.onyomi?.let {
                // KANJIDIC writes on-yomi in katakana; a leading '-' marks a
                // rendaku-only reading (e.g. 音 -> "-ノン"), and entries are
                // comma separated.
                assertTrue(
                    it.all { c -> c in '\u30a0'..'\u30ff' || c == ',' || c == ' ' || c == '-' },
                    "onyomi is not katakana for ${card.character}: $it",
                )
            }
        }
    }

    @Test
    fun optionalFieldsBecomeNullRatherThanEmptyStrings() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("character", "\u4E00")
                    .put("meaning", "one")
                    .put("onyomi", JSONObject.NULL)
                    .put("kunyomi", "")
                    .put("exampleWord", JSONObject.NULL)
                    .put("jlpt", 5)
            )
            .toString()

        val card = DeckJsonParser.parse(json).single()
        assertNull(card.onyomi)
        assertNull(card.kunyomi)
        assertNull(card.exampleWord)
        assertEquals(5, card.jlpt)
    }

    @Test
    fun everyCardCarriesAJlptLevel() {
        val missing = deck.filter { it.jlpt !in 3..5 }
        assertTrue(missing.isEmpty(), "cards without a JLPT level: ${missing.take(5)}")
    }

    @Test
    fun mostCardsHaveAnExampleWord() {
        val withExample = deck.count { it.exampleWord != null }
        assertTrue(
            withExample > deck.size / 2,
            "only $withExample of ${deck.size} cards have an example word",
        )
    }

    @Test
    fun blankConstantMatchesWhatTheDeckUses() {
        assertTrue(DeckJsonParser.BLANK.isNotEmpty())
        assertNotNull(deck.firstOrNull { it.exampleWord?.contains(DeckJsonParser.BLANK) == true })
    }

    private fun asset(name: String): File {
        val file = File("src/main/assets/$name")
        assertTrue(
            file.exists(),
            "expected $name at ${file.absolutePath} (unit tests run from the module dir)",
        )
        return file
    }
}
