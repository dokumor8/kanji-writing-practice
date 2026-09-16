package com.example.kanjipractice.domain.deck

import com.example.kanjipractice.domain.LicenceEntry

/**
 * One card set.
 *
 * @param group the heading this set is listed under in settings, e.g. "Kana" or
 *   "HSK". Script-neutral on purpose: the same code drives a Japanese deck
 *   grouped into kana and kanji, and a Chinese one grouped by HSK level.
 */
data class DeckInfo(
    val id: String,
    val name: String,
    val group: String,
)

/**
 * Where the app's script-specific decisions live.
 *
 * Implemented once per product flavour (`AppScript` in the japanese and chinese
 * source sets), so the rest of the code never asks what language it is teaching.
 */
interface ScriptProfile {
    /** BCP-47 tag of the ML Kit digital-ink model to download. */
    val recognitionLanguageTag: String

    /** Labels for the two reading fields, in order. A null entry is not shown. */
    val readingLabels: List<String?>

    /** Assets holding the card data. */
    val cardAssets: List<String>

    /** Assets directory holding the stroke-order SVGs. */
    val strokeAssetDir: String

    /** Prefix for a card's level number, e.g. "N" or "HSK", or null to hide it. */
    val levelPrefix: String?

    /**
     * Who to credit for the bundled material. Flavour-specific because the two
     * apps ship entirely different character data under entirely different
     * licences, and crediting KanjiVG in the Chinese app would be both wrong and
     * a licence breach.
     */
    val licences: List<LicenceEntry>
}
