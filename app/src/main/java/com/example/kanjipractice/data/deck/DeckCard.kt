package com.example.kanjipractice.data.deck

/**
 * One entry of the bundled card data (assets/kanji.json and assets/kana.json).
 *
 * [exampleWord] already has the target character replaced by a blank, e.g.
 * "＿前" for 駅前, so the prompt never leaks the answer.
 */
data class DeckCard(
    val character: String,
    val meaning: String,
    val onyomi: String?,
    val kunyomi: String?,
    val exampleWord: String?,
    /** JLPT level, or 0 for kana, which the JLPT does not cover. */
    val jlpt: Int,
    val deckId: String,
    /** Position in the whole ordered set, so new cards arrive commonest-first. */
    val sortKey: Int,
)
