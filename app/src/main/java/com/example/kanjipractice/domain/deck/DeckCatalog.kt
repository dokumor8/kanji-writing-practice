package com.example.kanjipractice.domain.deck

/** What kind of material a set contains, which is really "kanji" or "kana". */
enum class DeckKind { KANA, KANJI }

data class DeckInfo(
    val id: String,
    val name: String,
    val kind: DeckKind,
)

/**
 * The card sets the app ships, and how they are named.
 *
 * The ids are the contract between this list, the bundled card data and whatever
 * the user has selected; the *contents* come from assets, so a set's size is a
 * property of the data rather than of this file.
 */
object DeckCatalog {

    /**
     * Bumped whenever the bundled card data changes in a way that alters which
     * set a card belongs to. The seeder re-assigns set membership when the stored
     * version differs, which is also what migrates cards created by an older
     * build that had no sets at all.
     */
    const val DATA_VERSION = 1

    val HIRAGANA = DeckInfo("hiragana", "Hiragana", DeckKind.KANA)
    val KATAKANA = DeckInfo("katakana", "Katakana", DeckKind.KANA)

    /**
     * The 2136 Joyo kanji, commonest first: six sets of 300, then a seventh with
     * everything that is left.
     */
    val KANJI: List<DeckInfo> = (1..7).map {
        DeckInfo("kanji-$it", "Kanji $it", DeckKind.KANJI)
    }

    val ALL: List<DeckInfo> = listOf(HIRAGANA, KATAKANA) + KANJI

    val byId: Map<String, DeckInfo> = ALL.associateBy { it.id }

    val KANJI_IDS: Set<String> = KANJI.map { it.id }.toSet()

    /**
     * Kanji on, kana off, for a new install and for an upgrade alike. A user
     * upgrading from the days when there was a single deck keeps every card they
     * have already studied in play; kana are a different kind of practice, so they
     * are opt-in.
     */
    val DEFAULT_SELECTED: Set<String> = KANJI_IDS

    fun nameOf(deckId: String): String = byId[deckId]?.name ?: deckId

    fun isKanji(deckId: String): Boolean = byId[deckId]?.kind == DeckKind.KANJI
}
