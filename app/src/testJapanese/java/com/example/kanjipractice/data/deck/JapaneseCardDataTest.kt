package com.example.kanjipractice.data.deck

import com.example.kanjipractice.domain.deck.DeckCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled Japanese data, validated as a build artifact. */
class JapaneseCardDataTest {

    private val kanji: List<DeckCard> by lazy { parse("kanji.json") }
    private val kana: List<DeckCard> by lazy { parse("kana.json") }
    private val all: List<DeckCard> get() = kanji + kana

    private fun parse(name: String) = DeckJsonParser.parse(asset(name).readText())

    private fun asset(name: String) = File("src/japanese/assets/$name")

    @Test
    fun usesTheJapaneseRecognitionModel() {
        assertEquals("ja", com.example.kanjipractice.domain.AppScript.recognitionLanguageTag)
    }

    @Test
    fun onlyTheTopCandidateIsAccepted() {
        // The Japanese model is accurate enough that a wrong character coming top
        // is a real error; this is the setting that caught 玉 for 主.
        assertEquals(1, com.example.kanjipractice.domain.AppScript.acceptedRanks)
    }

    @Test
    fun theJoyoDeckIsComplete() {
        assertEquals(2136, kanji.size)
    }

    @Test
    fun kanjiAreSplitIntoSevenSetsWithALargerLastOne() {
        val sizes = DeckCatalog.KANJI.map { deck -> kanji.count { it.deckId == deck.id } }
        assertEquals(listOf(300, 300, 300, 300, 300, 300, 336), sizes)
    }

    @Test
    fun kanaCoverTheGojuonIncludingVoicedKana() {
        assertEquals(71, kana.count { it.deckId == "hiragana" })
        assertEquals(71, kana.count { it.deckId == "katakana" })
    }

    @Test
    fun everyCardBelongsToASetTheAppKnowsAbout() {
        val unknown = all.map { it.deckId }.filter { it !in DeckCatalog.byId }.toSet()
        assertTrue(unknown.isEmpty(), "unknown deck ids: $unknown")
        assertEquals(DeckCatalog.ALL.map { it.id }.toSet(), all.map { it.deckId }.toSet())
    }

    @Test
    fun sortKeysOrderTheSetsFromCommonestToRarest() {
        for (deck in DeckCatalog.ALL) {
            val keys = all.filter { it.deckId == deck.id }.map { it.sortKey }
            assertEquals(keys.size, keys.toSet().size, "duplicate sort keys in ${deck.id}")
        }
        val ranges = DeckCatalog.KANJI.map { deck ->
            val keys = kanji.filter { it.deckId == deck.id }.map { it.sortKey }
            keys.min() to keys.max()
        }
        ranges.zipWithNext().forEach { (lower, higher) ->
            assertTrue(lower.second < higher.first, "kanji sets overlap: $lower then $higher")
        }
        assertEquals(0, ranges.first().first)
    }

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
        assertTrue(withExample > kanji.size / 2, "only $withExample of ${kanji.size}")
    }

    @Test
    fun kanjiReadingsArePlausibleWhenPresent() {
        for (card in kanji) {
            card.reading1?.let {
                // KANJIDIC writes on-yomi in katakana; a leading '-' marks a
                // rendaku-only reading, and entries are comma separated.
                assertTrue(
                    it.all { c -> c in '\u30a0'..'\u30ff' || c == ',' || c == ' ' || c == '-' },
                    "reading1 is not katakana for ${card.character}: $it",
                )
            }
        }
    }

    @Test
    fun kanaAreRomanisedWithOneAnswerEach() {
        // じ/ぢ and ず/づ are therefore spelled ji/zu and di/du.
        for ((reading, cards) in kana.groupBy { it.meaning }) {
            assertEquals(
                cards.map { it.deckId }.toSet().size,
                cards.size,
                "romanisation '$reading' is ambiguous: ${cards.map { it.character }}",
            )
        }
    }

    @Test
    fun kanaHaveNoReadingsOrLevel() {
        for (card in kana) {
            assertNull(card.reading1)
            assertNull(card.reading2)
            assertNull(card.exampleWord)
            assertEquals(0, card.level)
        }
    }

    @Test
    fun everyKanjiCarriesAJlptLevelOrZero() {
        for (card in kanji) {
            assertTrue(card.level in 0..5, "${card.character} has level ${card.level}")
        }
    }

    @Test
    fun blankAppearsInSomeExamples() {
        assertNotNull(kanji.firstOrNull { it.exampleWord?.contains(DeckJsonParser.BLANK) == true })
    }
}
