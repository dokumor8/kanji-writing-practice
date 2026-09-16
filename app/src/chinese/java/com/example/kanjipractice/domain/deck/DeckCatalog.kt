package com.example.kanjipractice.domain.deck

/**
 * The Chinese card sets: the 3033 simplified characters that appear in HSK 1-7
 * vocabulary, grouped by the level that introduces them.
 *
 * HSK levels are used rather than even-sized chunks because they are the unit a
 * learner actually thinks in, even though it makes the sets uneven.
 */
object DeckCatalog {

    const val DATA_VERSION = 1

    /** HSK levels present in the bundled data. */
    private val LEVELS = 1..7

    val ALL: List<DeckInfo> = LEVELS.map { DeckInfo("hsk-$it", "HSK $it", "HSK") }

    val byId: Map<String, DeckInfo> = ALL.associateBy { it.id }

    val groups: List<String> = ALL.map { it.group }.distinct()

    fun inGroup(group: String): List<DeckInfo> = ALL.filter { it.group == group }

    /**
     * Everything on by default. New cards are drawn commonest-first, and the
     * daily limit paces them, so a beginner still meets HSK 1 characters first.
     */
    val DEFAULT_SELECTED: Set<String> = ALL.map { it.id }.toSet()

    val KANJI_IDS: Set<String> = emptySet()

    fun nameOf(deckId: String): String = byId[deckId]?.name ?: deckId
}
