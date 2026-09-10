package com.example.kanjipractice.data.deck

/**
 * One entry of assets/kanji.json (see the plan, section 8).
 *
 * [exampleWord] already has the target character replaced by a blank, e.g. "＿前"
 * for 駅前, so the prompt never leaks the answer.
 */
data class DeckCard(
    val character: String,
    val meaning: String,
    val onyomi: String?,
    val kunyomi: String?,
    val exampleWord: String?,
    val jlpt: Int,
)
