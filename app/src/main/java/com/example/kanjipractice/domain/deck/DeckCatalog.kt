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
     * One means only its first choice is accepted. That is the right setting for
     * the Japanese model, which is accurate enough that a wrong character coming
     * top is a real error worth catching. The Chinese model is a different model
     * over a much larger, denser character set and does not earn the same trust,
     * so it gets the original top-three rule; a false accept costs the user one
     * tap on "Again", whereas a false reject blocks the card entirely.
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
