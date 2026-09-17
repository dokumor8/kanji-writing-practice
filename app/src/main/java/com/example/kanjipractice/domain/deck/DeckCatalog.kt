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
     * How many of the recogniser's candidates count as correct.
     *
     * One means only the recogniser's first choice is accepted, which is what
     * both apps use: a wrong character coming top is a real error worth catching,
     * and a wider window is what let a character ranked below the model's first
     * choice pass.
     *
     * It stays a per-flavour setting because the two apps run different models
     * over character sets of very different size and density, so the tolerance
     * that suits one need not suit the other.
     */
    val acceptedRanks: Int

    /**
     * Who to credit for the bundled material. Flavour-specific because the two
     * apps ship entirely different character data under entirely different
     * licences, and crediting KanjiVG in the Chinese app would be both wrong and
     * a licence breach.
     */
    val licences: List<LicenceEntry>
}
