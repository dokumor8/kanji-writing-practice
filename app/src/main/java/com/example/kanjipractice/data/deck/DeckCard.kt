package com.example.kanjipractice.data.deck

/**
 * One entry of a bundled card set, in the script-neutral schema both flavours
 * use: `reading1` is on-yomi in Japanese and pinyin in Chinese.
 *
 * [exampleWord] already has the target character replaced by a blank.
 */
data class DeckCard(
    val character: String,
    val meaning: String,
    val reading1: String?,
    val reading2: String?,
    val exampleWord: String?,
    /** JLPT level, or HSK level, or 0 where the concept does not apply. */
    val level: Int,
    val deckId: String,
    /** Position in the whole ordered set, so new cards arrive commonest-first. */
    val sortKey: Int,
)
