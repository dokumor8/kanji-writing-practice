package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.data.deck.DeckCard
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
 * also proves the app can always show a diagram for a card, which is what the
 * hint and the post-answer self-check both rely on.
 */
class BundledKanjiVgTest {

    private val assetsDir = File("src/main/assets")

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
                val diagram = KanjiVgParser.parse(file.readText())
                if (diagram.strokeCount == 0) {
                    failures += "${file.name}: no strokes"
                    continue
                }
                // Kana have stroke numbers too, so the invariant holds throughout.
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
    fun everyCardHasAStrokeDiagram() {
        val present = kanjivgDir.listFiles { f -> f.extension == "svg" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .toHashSet()

        val missing = cards.filter { codePointOf(it.character) !in present }
        assertTrue(missing.isEmpty(), "cards without a diagram: ${missing.take(20)}")
    }

    @Test
    fun thereAreNoOrphanDiagrams() {
        // Every shipped SVG should belong to a card; otherwise the assets carry
        // dead weight.
        val wanted = cards.map { codePointOf(it.character) }.toHashSet()
        val orphans = kanjivgDir.listFiles { f -> f.extension == "svg" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .filter { it !in wanted }
        assertEquals(emptyList(), orphans.take(20))
    }

    @Test
    fun kanaHaveStrokeOrderToo() {
        // KanjiVG covers kana as well as kanji, which is what lets the kana sets
        // use the same hint and self-check as everything else.
        val hiragana = cards.first { it.character == "\u3042" }
        val katakana = cards.first { it.character == "\u30A2" }
        for (card in listOf(hiragana, katakana)) {
            val file = File(kanjivgDir, codePointOf(card.character) + ".svg")
            assertTrue(file.exists(), "no diagram for ${card.character}")
            assertTrue(KanjiVgParser.parse(file.readText()).strokeCount > 0)
        }
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
