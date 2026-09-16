package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.data.deck.DeckCard
import com.example.kanjipractice.data.deck.DeckJsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parses every bundled stroke SVG for the Chinese flavour.
 *
 * These are generated from Make Me a Hanzi's stroke medians rather than taken
 * from KanjiVG, so the corpus check matters at least as much as it does for the
 * Japanese sets.
 */
class ChineseStrokeDataTest {

    private val assetsDir = File("src/chinese/assets")

    private val cards: List<DeckCard> by lazy {
        DeckJsonParser.parse(File(assetsDir, "hanzi.json").readText())
    }

    private fun codePointOf(character: String) =
        character.codePointAt(0).toString(16).padStart(5, '0')

    @Test
    fun everyBundledDiagramParsesAndIsWellFormed() {
        val files = strokesDir.listFiles { f -> f.extension == "svg" }.orEmpty()
        assertTrue(files.size >= 2000, "expected the full corpus, found ${files.size}")

        val failures = mutableListOf<String>()
        var viewBoxes = mutableSetOf<String>()
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
                viewBoxes += "${diagram.viewBoxWidth}x${diagram.viewBoxHeight}"
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
        // One shared view box is what keeps characters in proportion to each
        // other, so a stray box means a generation bug.
        assertEquals(1, viewBoxes.size, "more than one view box: $viewBoxes")
    }

    @Test
    fun everyCardHasAStrokeDiagram() {
        val present = strokesDir.listFiles { f -> f.extension == "svg" }
            .orEmpty().map { it.nameWithoutExtension }.toHashSet()
        val missing = cards.filter { codePointOf(it.character) !in present }
        assertTrue(missing.isEmpty(), "cards without a diagram: ${missing.take(20)}")
    }

    @Test
    fun thereAreNoOrphanDiagrams() {
        val wanted = cards.map { codePointOf(it.character) }.toHashSet()
        val orphans = strokesDir.listFiles { f -> f.extension == "svg" }
            .orEmpty().map { it.nameWithoutExtension }.filter { it !in wanted }
        assertEquals(emptyList(), orphans.take(20))
    }

    @Test
    fun strokesAreCurvesNotSinglePoints() {
        // A median reduced to one point would mean the generator lost the stroke.
        val degenerate = mutableListOf<String>()
        for (file in strokesDir.listFiles { f -> f.extension == "svg" }.orEmpty()) {
            val diagram = StrokeSvgParser.parse(file.readText())
            if (diagram.strokes.any { it.count { c -> c !is SvgPathCommand.Close } < 2 }) {
                degenerate += file.name
            }
        }
        assertTrue(degenerate.isEmpty(), "degenerate strokes: ${degenerate.take(10)}")
    }

    private val strokesDir: File
        get() {
            val dir = File(assetsDir, "strokes")
            assertTrue(dir.isDirectory, "expected ${dir.absolutePath}")
            return dir
        }
}
