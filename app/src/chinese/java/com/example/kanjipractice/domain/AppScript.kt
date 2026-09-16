package com.example.kanjipractice.domain

import com.example.kanjipractice.domain.deck.ScriptProfile

/**
 * The Chinese flavour: simplified characters, recognised with ML Kit's
 * zh-Hani-CN model. Note that ML Kit tags Chinese by script and region rather
 * than with zh-Hans/zh-Hant.
 */
object AppScript : ScriptProfile {
    override val recognitionLanguageTag = "zh-Hani-CN"
    override val readingLabels: List<String?> = listOf("Pinyin", null)
    override val cardAssets = listOf("hanzi.json")
    override val strokeAssetDir = "strokes"
    override val levelPrefix: String? = "HSK "

    override val licences: List<LicenceEntry> = listOf(
        LicenceEntry(
            title = "Stroke order data",
            body = "Generated from Make Me a Hanzi, whose stroke graphics derive " +
                "from the Arphic PL KaitiM GB and Arphic PL UKai fonts. Copyright " +
                "1999 Arphic Technology Co., Ltd. Distributed under the Arphic " +
                "Public License, included in the app as ARPHICPL.TXT.",
            link = "https://github.com/skishore/makemeahanzi",
        ),
        LicenceEntry(
            title = "Meanings and pinyin",
            body = "Derived from Unihan, copyright Unicode, Inc. Distributed under " +
                "the Unicode License v3.",
            link = "https://www.unicode.org/charts/unihan.html",
        ),
        LicenceEntry(
            title = "HSK levels and word list",
            body = "complete-hsk-vocabulary, copyright 2026 Yanis Zafirópulos. " +
                "Licensed MIT.",
            link = "https://github.com/drkameleon/complete-hsk-vocabulary",
        ),
        LicenceEntry(
            title = "FSRS v6 - scheduling",
            body = "Algorithm and default parameters from the open-spaced-repetition " +
                "project, MIT. This app's Kotlin implementation is original code.",
            link = "https://github.com/open-spaced-repetition/fsrs-rs",
        ),
    )
}
