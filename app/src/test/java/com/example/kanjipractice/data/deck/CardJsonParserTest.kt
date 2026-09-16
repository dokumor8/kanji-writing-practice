package com.example.kanjipractice.data.deck

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parser itself. The bundled data is validated per flavour. */
class CardJsonParserTest {

    @Test
    fun readsEveryField() {
        val card = DeckJsonParser.parse(CARD).single()
        assertEquals("\u65E5", card.character)
        assertEquals("day, sun", card.meaning)
        assertEquals("\u30CB\u30C1", card.reading1)
        assertEquals("hi", card.reading2)
        assertEquals("\uFF3F\u8A18", card.exampleWord)
        assertEquals(5, card.level)
        assertEquals("kanji-1", card.deckId)
        assertEquals(0, card.sortKey)
    }

    @Test
    fun optionalFieldsBecomeNullRatherThanEmptyStrings() {
        val json = JSONArray().put(
            JSONObject()
                .put("character", "\u4E00")
                .put("meaning", "one")
                .put("reading1", JSONObject.NULL)
                .put("reading2", "")
                .put("exampleWord", JSONObject.NULL)
                .put("level", 0)
                .put("deck", "hsk-1")
                .put("sortKey", 3)
        ).toString()

        val card = DeckJsonParser.parse(json).single()
        assertNull(card.reading1)
        assertNull(card.reading2)
        assertNull(card.exampleWord)
        assertEquals(0, card.level)
    }

    @Test
    fun anEmptyCharacterIsRejected() {
        val json = JSONArray().put(
            JSONObject()
                .put("character", "")
                .put("meaning", "x")
                .put("deck", "d")
                .put("sortKey", 0)
        ).toString()
        assertFailsWith<IllegalArgumentException> { DeckJsonParser.parse(json) }
    }

    @Test
    fun theBlankConstantIsWhatTheDataUses() {
        assertTrue(DeckJsonParser.BLANK.isNotEmpty())
        assertEquals("\uFF3F", DeckJsonParser.BLANK)
    }

    private companion object {
        val CARD = JSONArray().put(
            JSONObject()
                .put("character", "\u65E5")
                .put("meaning", "day, sun")
                .put("reading1", "\u30CB\u30C1")
                .put("reading2", "hi")
                .put("exampleWord", "\uFF3F\u8A18")
                .put("level", 5)
                .put("deck", "kanji-1")
                .put("sortKey", 0)
        ).toString()
    }
}
