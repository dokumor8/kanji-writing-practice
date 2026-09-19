package com.example.kanjipractice.data.deck

import com.example.kanjipractice.domain.deck.DeckCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled Chinese data, validated as a build artifact. */
class ChineseCardDataTest {

    private val cards: List<DeckCard> by lazy {
        DeckJsonParser.parse(File("src/chinese/assets/hanzi.json").readText())
    }

    @Test
    fun usesTheSimplifiedChineseRecognitionModel() {
        // ML Kit tags Chinese by script and region rather than zh-Hans.
        assertEquals(
            "zh-Hani-CN",
            com.example.kanjipractice.domain.AppScript.recognitionLanguageTag,
        )
    }

    @Test
    fun everyHskLevelIsPresent() {
        assertEquals(7, DeckCatalog.ALL.size)
        assertEquals(
            DeckCatalog.ALL.map { it.id }.toSet(),
            cards.map { it.deckId }.toSet(),
        )
    }

    @Test
    fun theDeckIsBigEnoughToBeUseful() {
        assertTrue(cards.size >= 2000, "only ${cards.size} characters")
    }

    @Test
    fun sortKeysRunInOrderAndAreUnique() {
        for (deck in DeckCatalog.ALL) {
            val keys = cards.filter { it.deckId == deck.id }.map { it.sortKey }
            assertEquals(keys.size, keys.toSet().size, "duplicate sort keys in ${deck.id}")
        }
        // Sets are contiguous ascending ranges, so new cards arrive HSK 1 first.
        val ranges = DeckCatalog.ALL.map { deck ->
            val keys = cards.filter { it.deckId == deck.id }.map { it.sortKey }
            keys.min() to keys.max()
        }
        ranges.zipWithNext().forEach { (lower, higher) ->
            assertTrue(lower.second < higher.first, "sets overlap: $lower then $higher")
        }
        assertEquals(0, ranges.first().first)
    }

    @Test
    fun everyCardIsASingleHanCharacterWithAMeaning() {
        for (card in cards) {
            assertEquals(
                1,
                card.character.codePointCount(0, card.character.length),
                "multi-character entry: ${card.character}",
            )
            assertTrue(
                card.character[0] in '\u4e00'..'\u9fff',
                "not a Han character: ${card.character}",
            )
            assertTrue(card.meaning.isNotBlank(), "no meaning for ${card.character}")
        }
    }

    @Test
    fun charactersAreUnique() {
        val duplicates = cards.groupBy { it.character }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "duplicate characters: $duplicates")
    }

    @Test
    fun everyCardHasPinyin() {
        val missing = cards.filter { it.reading1.isNullOrBlank() }
        assertTrue(missing.isEmpty(), "no pinyin for: ${missing.take(10).map { it.character }}")
    }

    @Test
    fun pinyinIsLatinLettersWithToneMarks() {
        // Unihan's kMandarin is tone-marked where the syllable has a tone, so
        // letters, precomposed tone vowels, combining marks and spaces only.
        val allowed = Regex("^[A-Za-z\u00C0-\u024F\u0300-\u036F ]+$")
        val odd = cards.mapNotNull { card ->
            val reading = card.reading1 ?: return@mapNotNull null
            if (allowed.matches(reading)) null else "${card.character}=$reading"
        }
        assertTrue(odd.isEmpty(), "unexpected pinyin: ${odd.take(10)}")
    }

    @Test
    fun thereIsNoSecondReading() {
        for (card in cards) {
            assertNull(card.reading2, "${card.character} has a second reading")
        }
    }

    @Test
    fun exampleWordsNeverLeakTheAnswer() {
        for (card in cards) {
            val example = card.exampleWord ?: continue
            assertTrue(
                card.character !in example,
                "${card.character} is visible in its own example word: $example",
            )
        }
    }

    @Test
    fun mostCardsHaveAnExampleWord() {
        val withExample = cards.count { it.exampleWord != null }
        assertTrue(
            withExample > cards.size / 2,
            "only $withExample of ${cards.size} have an example word",
        )
    }

    @Test
    fun everyCardCarriesAnHskLevel() {
        for (card in cards) {
            assertTrue(card.level in 1..7, "${card.character} has level ${card.level}")
        }
        assertEquals(
            cards.map { it.deckId }.toSet(),
            cards.map { "hsk-${it.level}" }.toSet(),
        )
    }

    @Test
    fun theArphicLicenceShipsWithTheDataItCovers() {
        // The APL requires its licence text to travel unaltered with any copy of
        // the data derived from the Arphic fonts.
        val licence = File("src/chinese/assets/ARPHICPL.TXT")
        assertTrue(licence.exists(), "ARPHICPL.TXT is missing from the bundled assets")
        assertTrue(
            licence.readText().contains("ARPHIC PUBLIC LICENSE"),
            "ARPHICPL.TXT does not look like the Arphic licence",
        )
    }
}
