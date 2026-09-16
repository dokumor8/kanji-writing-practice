package com.example.kanjipractice.data.deck

import com.example.kanjipractice.domain.deck.DeckCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled card data is what the app cannot work without, so it is validated
 * as a build artifact rather than trusted.
 */
class DeckJsonParserTest {

    private val kanji: List<DeckCard> by lazy { parse("kanji.json") }
    private val kana: List<DeckCard> by lazy { parse("kana.json") }
    private val all: List<DeckCard> get() = kanji + kana

    private fun parse(name: String) = DeckJsonParser.parse(asset(name).readText())

    // ------------------------------------------------------------- the sets

    @Test
    fun theJoyoDeckIsComplete() {
        assertEquals(2136, kanji.size)
    }

    @Test
    fun kanjiAreSplitIntoSevenSetsWithALargerLastOne() {
        val sizes = DeckCatalog.KANJI.map { deck -> kanji.count { it.deckId == deck.id } }
        // Six sets of 300, then the remainder of the 2136 Joyo kanji.
        assertEquals(listOf(300, 300, 300, 300, 300, 300, 336), sizes)
    }

    @Test
    fun kanaCoverTheGojuonIncludingVoicedKana() {
        // 46 basic + 20 voiced + 5 semi-voiced, twice.
        assertEquals(71, kana.count { it.deckId == "hiragana" })
        assertEquals(71, kana.count { it.deckId == "katakana" })
    }

    @Test
    fun everyCardBelongsToASetTheAppKnowsAbout() {
        // The contract between the bundled data and DeckCatalog.
        val unknown = all.map { it.deckId }.filter { it !in DeckCatalog.byId }.toSet()
        assertTrue(unknown.isEmpty(), "unknown deck ids: $unknown")
        assertEquals(DeckCatalog.ALL.map { it.id }.toSet(), all.map { it.deckId }.toSet())
    }

    @Test
    fun sortKeysOrderTheSetsFromCommonestToRarest() {
        // sortKey is a global order index, so the sets must be contiguous and
        // ascending: that is what makes new cards arrive set 1 first.
        for (deck in DeckCatalog.ALL) {
            val keys = all.filter { it.deckId == deck.id }.map { it.sortKey }
            assertEquals(keys.size, keys.toSet().size, "duplicate sort keys in ${deck.id}")
        }
        val ranges = DeckCatalog.KANJI.map { deck ->
            val keys = kanji.filter { it.deckId == deck.id }.map { it.sortKey }
            keys.min() to keys.max()
        }
        ranges.zipWithNext().forEach { (lower, higher) ->
            assertTrue(
                lower.second < higher.first,
                "kanji sets overlap in the ordering: $lower then $higher",
            )
        }
        assertEquals(0, ranges.first().first)
    }

    // ------------------------------------------------------------ the cards

    @Test
    fun everyCardIsASingleCharacterWithAMeaning() {
        for (card in all) {
            assertEquals(
                1,
                card.character.codePointCount(0, card.character.length),
                "multi-character entry: ${card.character}",
            )
            assertTrue(card.meaning.isNotBlank(), "no meaning for ${card.character}")
        }
    }

    @Test
    fun charactersAreUniqueAcrossEverySet() {
        val duplicates = all.groupBy { it.character }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate characters: $duplicates")
    }

    @Test
    fun cardIdsAreTheCodePointSoSeedingIsIdempotent() {
        val ids = all.map { it.character.codePointAt(0).toLong() }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun exampleWordsNeverLeakTheAnswer() {
        for (card in kanji) {
            val example = card.exampleWord ?: continue
            assertTrue(
                card.character !in example,
                "${card.character} is visible in its own example word: $example",
            )
        }
    }

    @Test
    fun mostKanjiHaveAnExampleWord() {
        val withExample = kanji.count { it.exampleWord != null }
        assertTrue(
            withExample > kanji.size / 2,
            "only $withExample of ${kanji.size} kanji have an example word",
        )
    }

    @Test
    fun kanjiReadingsArePlausibleWhenPresent() {
        for (card in kanji) {
            card.onyomi?.let {
                // KANJIDIC writes on-yomi in katakana; a leading '-' marks a
                // rendaku-only reading, and entries are comma separated.
                assertTrue(
                    it.all { c -> c in '\u30a0'..'\u30ff' || c == ',' || c == ' ' || c == '-' },
                    "onyomi is not katakana for ${card.character}: $it",
                )
            }
        }
    }

    @Test
    fun kanaAreRomanisedWithOneAnswerEach() {
        val romaji = kana.groupBy { it.meaning }
        for ((reading, cards) in romaji) {
            // The prompt for a kana card is its romanisation, so two cards
            // sharing one would be unanswerable. じ/ぢ and ず/づ are therefore
            // spelled ji/zu and di/du.
            val scripts = cards.map { it.deckId }.toSet()
            assertEquals(
                scripts.size,
                cards.size,
                "romanisation '$reading' is ambiguous: ${cards.map { it.character }}",
            )
        }
    }

    @Test
    fun everyKanjiCarriesAJlptLevelOrZero() {
        for (card in kanji) {
            assertTrue(card.jlpt in 0..5, "${card.character} has jlpt ${card.jlpt}")
        }
    }

    @Test
    fun kanaHaveNoReadingsOrJlptLevel() {
        for (card in kana) {
            assertNull(card.onyomi)
            assertNull(card.kunyomi)
            assertNull(card.exampleWord)
            assertEquals(0, card.jlpt)
        }
    }

    // ------------------------------------------------------------ the parser

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
                    .put("deck", "kanji-1")
                    .put("sortKey", 0)
            )
            .toString()

        val card = DeckJsonParser.parse(json).single()
        assertNull(card.onyomi)
        assertNull(card.kunyomi)
        assertNull(card.exampleWord)
        assertEquals(5, card.jlpt)
        assertEquals("kanji-1", card.deckId)
    }

    @Test
    fun blankConstantMatchesWhatTheDeckUses() {
        assertTrue(DeckJsonParser.BLANK.isNotEmpty())
        assertNotNull(kanji.firstOrNull { it.exampleWord?.contains(DeckJsonParser.BLANK) == true })
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
