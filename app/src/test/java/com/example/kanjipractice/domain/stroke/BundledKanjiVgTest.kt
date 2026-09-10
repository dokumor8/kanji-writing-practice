package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.data.deck.DeckJsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parses every SVG that actually ships with the app.
 *
 * The parser is written against the KanjiVG format, and this is what proves the
 * assumption holds for the whole bundled corpus rather than for one sample. It
 * also proves the app can always show a diagram in RELEARN, where the plan
 * requires one.
 */
class BundledKanjiVgTest {

    private val assetsDir = File("src/main/assets")

    @Test
    fun everyBundledDiagramParsesAndIsWellFormed() {
        val files = kanjivgDir.listFiles { f -> f.extension == "svg" }.orEmpty()
        assertTrue(files.size >= 200, "expected a bundled corpus, found ${files.size}")

        val failures = mutableListOf<String>()
        for (file in files) {
            try {
                val diagram = KanjiVgParser.parse(file.readText())
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
    fun everyCardInTheDeckHasAStrokeDiagram() {
        val deck = DeckJsonParser.parse(File(assetsDir, "kanji.json").readText())
        val present = kanjivgDir.listFiles { f -> f.extension == "svg" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .toHashSet()

        val missing = deck.mapNotNull { card ->
            val file = card.character.codePointAt(0).toString(16).padStart(5, '0')
            if (file in present) null else card.character
        }
        assertTrue(missing.isEmpty(), "cards without a stroke diagram: ${missing.take(20)}")
    }

    @Test
    fun thereAreNoOrphanDiagrams() {
        // Every shipped SVG should belong to a card; otherwise the assets carry
        // dead weight.
        val deck = DeckJsonParser.parse(File(assetsDir, "kanji.json").readText())
        val wanted = deck.map { it.character.codePointAt(0).toString(16).padStart(5, '0') }.toHashSet()
        val orphans = kanjivgDir.listFiles { f -> f.extension == "svg" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .filter { it !in wanted }
        assertEquals(emptyList(), orphans.take(20))
    }

    private val kanjivgDir: File
        get() {
            val dir = File(assetsDir, "kanjivg")
            assertTrue(
                dir.isDirectory,
                "expected ${dir.absolutePath} (unit tests run from the module dir)",
            )
            return dir
        }
}
