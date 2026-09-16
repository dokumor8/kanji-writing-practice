package com.example.kanjipractice.domain.deck

/**
 * The Japanese card sets: hiragana, katakana, and the 2136 Joyo kanji split into
 * six sets of 300 with a seventh of 336.
 */
object DeckCatalog {

    /** Bumped whenever the bundled card data changes which set a card is in. */
    const val DATA_VERSION = 1

    val HIRAGANA = DeckInfo("hiragana", "Hiragana", "Kana")
    val KATAKANA = DeckInfo("katakana", "Katakana", "Kana")
    val KANJI: List<DeckInfo> = (1..7).map { DeckInfo("kanji-$it", "Kanji $it", "Kanji") }

    val ALL: List<DeckInfo> = listOf(HIRAGANA, KATAKANA) + KANJI

    val byId: Map<String, DeckInfo> = ALL.associateBy { it.id }

    /** Headings, in the order they should be shown. */
    val groups: List<String> = ALL.map { it.group }.distinct()

    fun inGroup(group: String): List<DeckInfo> = ALL.filter { it.group == group }

    /**
     * Kanji on, kana off, for a new install and for an upgrade alike: a user
     * upgrading from the days when there was a single deck keeps every card they
     * have already studied in play, and kana are opt-in.
     */
    val DEFAULT_SELECTED: Set<String> = KANJI.map { it.id }.toSet()

    val KANJI_IDS: Set<String> = KANJI.map { it.id }.toSet()

    fun nameOf(deckId: String): String = byId[deckId]?.name ?: deckId
}
