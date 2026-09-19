package com.example.kanjipractice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every setting on [com.example.kanjipractice.domain.deck.ScriptProfile] has to be
 * read by something, or declaring it does nothing.
 *
 * This project has shipped that mistake twice, both times invisibly:
 *
 *  - `strokeAssetDir` was implemented per flavour but the loader kept a
 *    hard-coded directory, so every Chinese character fell back to being drawn in
 *    a system font.
 *  - `recognitionLanguageTag` was implemented per flavour but the recogniser kept
 *    a hard-coded "ja", so the Chinese app used the Japanese model and could not
 *    recognise a character that does not exist in Japanese.
 *
 * Neither showed up as a crash or a failing behaviour test: they looked like
 * quality problems. This test is the cheapest thing that would have caught both.
 */
class ScriptProfileUsageTest {

    @Test
    fun everyProfileSettingIsReadSomewhere() {
        val mainSrc = File("src/main/java")
        assertTrue(mainSrc.isDirectory, "expected ${mainSrc.absolutePath}")

        // The interface itself, where the members are declared rather than used.
        val declaration = File(
            mainSrc,
            "com/example/kanjipractice/domain/deck/DeckCatalog.kt",
        )
        assertTrue(declaration.isFile, "ScriptProfile declaration not found")

        val otherSources = mainSrc.walkTopDown()
            .filter { it.extension == "kt" && it != declaration }
            .map { it.readText() }
            .toList()

        val unused = MEMBERS.filter { member ->
            otherSources.none { source -> source.contains(member) }
        }

        assertTrue(
            unused.isEmpty(),
            "ScriptProfile settings that nothing reads: $unused. " +
                "Either wire them up or delete them -- a setting that is declared " +
                "and ignored silently makes one app behave like the other.",
        )
    }

    private companion object {
        /** Keep in step with the interface. */
        val MEMBERS = listOf(
            "recognitionLanguageTag",
            "readingLabels",
            "cardAssets",
            "strokeAssetDir",
            "levelPrefix",
            "licences",
        )
    }
}
