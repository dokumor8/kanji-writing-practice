package com.example.kanjipractice.domain

import com.example.kanjipractice.domain.deck.ScriptProfile

/** The Japanese flavour: kana and Joyo kanji, recognised with ML Kit's ja model. */
object AppScript : ScriptProfile {
    override val recognitionLanguageTag = "ja"
    override val readingLabels: List<String?> = listOf("On", "Kun")
    override val cardAssets = listOf("kanji.json", "kana.json")
    override val strokeAssetDir = "kanjivg"
    override val levelPrefix: String? = "N"

    override val acceptedRanks = 1

    override val licences: List<LicenceEntry> = listOf(
        LicenceEntry(
            title = "KanjiVG - stroke order diagrams",
            body = "Copyright 2009-2024 Ulrich Apel and contributors. Licensed " +
                "CC BY-SA 3.0, redistributed unmodified.",
            link = "https://kanjivg.tagaini.net/",
        ),
        LicenceEntry(
            title = "KANJIDIC2 - meanings, readings, JLPT levels",
            body = "Copyright James William Breen and the Electronic Dictionary " +
                "Research and Development Group. Licensed CC BY-SA 4.0.",
            link = "https://www.edrdg.org/edrdg/licence.html",
        ),
        LicenceEntry(
            title = "JLPT vocabulary - example words",
            body = "open-anki-jlpt-decks, copyright 2020 Jamie Sinclair. Licensed MIT.",
            link = "https://github.com/jamsinclair/open-anki-jlpt-decks",
        ),
        LicenceEntry(
            title = "FSRS v6 - scheduling",
            body = "Algorithm and default parameters from the open-spaced-repetition " +
                "project, MIT. This app's Kotlin implementation is original code.",
            link = "https://github.com/open-spaced-repetition/fsrs-rs",
        ),
    )
}
