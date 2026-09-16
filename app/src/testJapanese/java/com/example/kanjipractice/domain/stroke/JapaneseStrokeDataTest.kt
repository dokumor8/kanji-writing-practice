package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.data.deck.DeckCard
import com.example.kanjipractice.data.deck.DeckJsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parses every SVG the Japanese flavour ships. This is what proves the parser
 * assumption holds for the whole corpus rather than for one sample, and that the
 * app can always show a diagram for a card.
 */
class JapaneseStrokeDataTest {

    private val assetsDir = File("src/japanese/assets")

    private val cards: List<DeckCard> by lazy {
        listOf("kanji.json", "kana.json").flatMap { name ->
            DeckJsonParser.parse(File(assetsDir, name).readText())
        }
    }

    private fun codePointOf(character: String) =
        character.codePointAt(0).toString(16).padStart(5, '0')

    @Test
    fun everyBundledDiagramParsesAndIsWellFormed() {
        val files = kanjivgDir.listFiles { f -> f.extension == "svg" }.orEmpty()
        assertTrue(files.size >= 2000, "expected the full corpus, found ${files.size}")

        val failures = mutableListOf<String>()
        for (file in files) {
            try {
                val diagram = StrokeSvgParser.parse(file.readText())
                if (diagram.strokeCount == 0) {
                    failures += "${file.name}: no strokes"
                    continue
                }
                if (diagram.strokeCount != diagram.numbers.size) {
                    failures += "${file.name}: ${diagram.strokeCount} strokes but " +
                        "${diagram.numbers.size} numbers"
                }
                if (diagram.viewBoxWidth != 109f || diagram.viewBoxHeight != 109f) {
                    failures += "${file.name}: unexpected viewBox " +
                        "${diagram.viewBoxWidth}x${diagram.viewBoxHeight}"
                }
                for (stroke in diagram.strokes) {
                    if (stroke.first() !is SvgPathCommand.MoveTo) {
                        failures += "${file.name}: a stroke does not start with a move"
                    }
                }
            } catch (e: Exception) {
                failures += "${file.name}: ${e.message}"
            }
        }
        assertTrue(failures.isEmpty(), "malformed diagrams: ${failures.take(10)}")
    }


    @Test
    fun everyCardResolvesToABundledDiagram() {
        // Regression: the loader hard-coded one flavour's asset directory, so
        // every Chinese character silently fell back to being drawn in a system
        // font. This walks the path the app actually builds.
        val missing = cards.mapNotNull { card ->
            val path = StrokeAssets.pathFor(card.character) ?: return@mapNotNull card.character
            if (File(assetsDir, path).exists()) null else "${card.character} -> $path"
        }
        assertTrue(missing.isEmpty(), "cards whose asset path does not exist: ${missing.take(10)}")
    }

    @Test
    fun everyCardHasAStrokeDiagram() {
        val present = kanjivgDir.listFiles { f -> f.extension == "svg" }
            .orEmpty().map { it.nameWithoutExtension }.toHashSet()
        val missing = cards.filter { codePointOf(it.character) !in present }
        assertTrue(missing.isEmpty(), "cards without a diagram: ${missing.take(20)}")
    }

    @Test
    fun thereAreNoOrphanDiagrams() {
        val wanted = cards.map { codePointOf(it.character) }.toHashSet()
        val orphans = kanjivgDir.listFiles { f -> f.extension == "svg" }
            .orEmpty().map { it.nameWithoutExtension }.filter { it !in wanted }
        assertEquals(emptyList(), orphans.take(20))
    }

    @Test
    fun kanaHaveStrokeOrderToo() {
        // KanjiVG covers kana as well as kanji, which is what lets the kana sets
        // use the same hint and self-check as everything else.
        for (character in listOf("\u3042", "\u30A2")) {
            val file = File(kanjivgDir, codePointOf(character) + ".svg")
            assertTrue(file.exists(), "no diagram for $character")
            assertTrue(StrokeSvgParser.parse(file.readText()).strokeCount > 0)
        }
    }

    private val kanjivgDir: File
        get() {
            val dir = File(assetsDir, "kanjivg")
            assertTrue(dir.isDirectory, "expected ${dir.absolutePath}")
            return dir
        }
}
