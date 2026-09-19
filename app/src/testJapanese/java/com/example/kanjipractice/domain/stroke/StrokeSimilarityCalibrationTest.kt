package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the default threshold comes from.
 *
 * Synthetic perturbations of a real reference diagram, so the number in
 * StudySettings is chosen from measurements rather than taste. The assertions
 * are about ordering and margins -- the score of a perfect copy is not
 * interesting on its own, but "a missing stroke must score clearly worse than a
 * wobbly one" is the whole feature.
 */
class StrokeSimilarityCalibrationTest {

    private fun diagram(character: String): StrokeDiagram {
        val code = character.codePointAt(0).toString(16).padStart(5, '0')
        val file = File("src/japanese/assets/kanjivg/$code.svg")
        assertTrue(file.exists(), "missing diagram for $character")
        return StrokeSvgParser.parse(file.readText())
    }

    private fun jitter(strokes: List<Stroke>, amount: Float, seed: Int = 1): List<Stroke> {
        val random = Random(seed)
        return strokes.map { stroke ->
            Stroke(
                stroke.points.map { p ->
                    StrokePoint(
                        p.x + (random.nextFloat() - 0.5f) * amount,
                        p.y + (random.nextFloat() - 0.5f) * amount,
                    )
                }
            )
        }
    }

    private fun shift(strokes: List<Stroke>, index: Int, dx: Float, dy: Float): List<Stroke> =
        strokes.mapIndexed { i, stroke ->
            if (i != index) stroke
            else Stroke(stroke.points.map { StrokePoint(it.x + dx, it.y + dy) })
        }

    @Test
    fun aPerfectCopyScoresHighest() {
        val reference = diagram("\u65E5")
        val copy = StrokeSimilarity.outline(reference)
        val result = StrokeSimilarity.compare(copy, reference)
        assertTrue(result != null)
        println("perfect copy: " + result!!.score)
        assertTrue(result.score > 0.95, "a perfect copy scored " + result.score)
    }

    @Test
    fun wobbleIsCheapButAMissingStrokeIsNot() {
        val reference = diagram("\u65E5")
        val copy = StrokeSimilarity.outline(reference)
        // 日 is 4 strokes; the whole box is about 100 units across.
        val wobbly = StrokeSimilarity.compare(jitter(copy, amount = 6f), reference)!!
        val missing = StrokeSimilarity.compare(copy.drop(1), reference)!!
        val halved = StrokeSimilarity.compare(copy.take(2), reference)!!
        val moved = StrokeSimilarity.compare(shift(copy, 1, 45f, 0f), reference)!!

        println("wobbly:  " + wobbly.score)
        println("missing: " + missing.score + "  (worst stroke " + missing.worstStroke + ")")
        println("halved:  " + halved.score)
        println("moved:   " + moved.score)

        assertTrue(wobbly.score > 0.90, "wobble should be nearly free, was " + wobbly.score)
        assertTrue(missing.score < wobbly.score - 0.15, "a missing stroke must cost clearly more")
        assertTrue(halved.score < 0.6, "half a character should not look like the whole one")
        assertTrue(moved.score < wobbly.score - 0.15, "a displaced stroke must cost clearly more")
    }

    @Test
    fun aSingleMissingStrokeIsCaughtEvenInALongCharacter() {
        // The case that started this: 日 drawn where 目 was wanted. Both are short,
        // but the same slip in a twenty-stroke character must also register, which
        // averaging alone would hide.
        val me = diagram("\u76EE")
        val hi = diagram("\u65E5")
        val drawnAsHi = StrokeSimilarity.compare(StrokeSimilarity.outline(hi), me)!!
        val correct = StrokeSimilarity.compare(StrokeSimilarity.outline(me), me)!!
        println("\u76EE correct: " + correct.score)
        println("\u76EE drawn as \u65E5: " + drawnAsHi.score + "  (strokes " + drawnAsHi.drawnStrokes + " of " + drawnAsHi.referenceStrokes + ")")

        // And a long character missing only its final stroke.
        val long = diagram("\u66DC")   // 曜, 18 strokes
        val withoutLast = StrokeSimilarity.outline(long).dropLast(1)
        val trimmed = StrokeSimilarity.compare(withoutLast, long)!!
        println("\u66DC minus its last stroke: " + trimmed.score)

        assertTrue(correct.score > 0.9)
        assertTrue(drawnAsHi.score < correct.score - 0.15, "日 for 目 scored " + drawnAsHi.score)
        assertTrue(trimmed.score < correct.score - 0.15, "a missing stroke in 曜 scored " + trimmed.score)
    }

    @Test
    fun aDifferentCharacterScoresLower() {
        val reference = diagram("\u65E5")
        val other = diagram("\u6708")
        val result = StrokeSimilarity.compare(StrokeSimilarity.outline(other), reference)!!
        println("different character: " + result.score)
        assertTrue(result.score < 0.75, "another character scored " + result.score)
    }

    @Test
    fun wrongStrokeOrderIsPunished() {
        // 主 and 玉 are the same ink in a different order and position of the dot,
        // which is exactly what a recogniser accepts and this must not.
        val main = diagram("\u4E3B")
        val jewel = diagram("\u7389")
        val drawnInJewelOrder = StrokeSimilarity.compare(StrokeSimilarity.outline(jewel), main)!!
        val drawnCorrectly = StrokeSimilarity.compare(StrokeSimilarity.outline(main), main)!!
        println("\u4E3B drawn correctly: " + drawnCorrectly.score)
        println("\u4E3B drawn as \u7389:     " + drawnInJewelOrder.score)
        assertTrue(
            drawnInJewelOrder.score < drawnCorrectly.score - 0.2,
            "order should matter: " + drawnInJewelOrder.score + " vs " + drawnCorrectly.score,
        )
    }
}
