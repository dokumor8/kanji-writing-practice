package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import com.example.kanjipractice.domain.settings.StudySettings
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the default threshold comes from, and what the metric is allowed to do.
 *
 * Synthetic perturbations of real reference diagrams, so the numbers in
 * StudySettings are chosen from measurements rather than taste. The assertions
 * are about ordering and margins -- the score of a perfect copy is not
 * interesting on its own, but "a missing stroke must cost clearly more than a
 * wobbly one" is the whole feature.
 *
 * Two fixtures are recordings of real failures rather than invented ones:
 *
 *  - **shortBottomStroke**: 二 with the bottom bar drawn 31% short. The previous
 *    metric scored this 26% and rejected it, because shortening the bar shrank
 *    the bounding box everything else was measured against.
 *  - **movedComponent**: 取 with 又 drawn a little low. The previous metric
 *    scored this 45%, because two of eight strokes fell outside tolerance and the
 *    worst-stroke weighting did the rest.
 *
 * Both are correct drawings with a real but small error. Neither should be
 * rejected, and both should still be visibly worse than a clean copy.
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

    private fun shift(strokes: List<Stroke>, indexes: Set<Int>, dx: Float, dy: Float): List<Stroke> =
        strokes.mapIndexed { i, stroke ->
            if (i !in indexes) stroke
            else Stroke(stroke.points.map { StrokePoint(it.x + dx, it.y + dy) })
        }

    /** Shortens a stroke about its own midpoint, so its position is unchanged. */
    private fun shorten(strokes: List<Stroke>, index: Int, factor: Float): List<Stroke> =
        strokes.mapIndexed { i, stroke ->
            if (i != index || stroke.points.size < 2) return@mapIndexed stroke
            val first = stroke.points.first()
            val last = stroke.points.last()
            val cx = (first.x + last.x) / 2f
            val cy = (first.y + last.y) / 2f
            Stroke(
                stroke.points.map {
                    StrokePoint(cx + (it.x - cx) * factor, cy + (it.y - cy) * factor)
                }
            )
        }

    /**
     * Cuts a stroke short *from its far end*, which is what "stopped before it
     * reached the bar" actually looks like. Shortening about the midpoint, which
     * `shorten` does, leaves the far half where it was.
     */
    private fun trimEnd(strokes: List<Stroke>, index: Int, keep: Float): List<Stroke> =
        strokes.mapIndexed { i, stroke ->
            if (i != index || stroke.points.size < 2) return@mapIndexed stroke
            var total = 0f
            for (k in 1 until stroke.points.size) {
                total += hypot(
                    (stroke.points[k].x - stroke.points[k - 1].x).toDouble(),
                    (stroke.points[k].y - stroke.points[k - 1].y).toDouble(),
                ).toFloat()
            }
            val target = total * keep
            val kept = ArrayList<StrokePoint>()
            kept += stroke.points.first()
            var walked = 0f
            for (k in 1 until stroke.points.size) {
                val previous = stroke.points[k - 1]
                val current = stroke.points[k]
                val step = hypot(
                    (current.x - previous.x).toDouble(),
                    (current.y - previous.y).toDouble(),
                ).toFloat()
                if (walked + step >= target) {
                    val f = if (step > 0f) (target - walked) / step else 0f
                    kept += StrokePoint(
                        previous.x + (current.x - previous.x) * f,
                        previous.y + (current.y - previous.y) * f,
                    )
                    break
                }
                walked += step
                kept += current
            }
            Stroke(kept)
        }

    /** Replaces every stroke with the straight line between its own two ends. */
    private fun straightened(strokes: List<Stroke>): List<Stroke> =
        strokes.map { stroke ->
            if (stroke.points.size < 3) stroke
            else Stroke(listOf(stroke.points.first(), stroke.points.last()))
        }

    private fun score(drawn: List<Stroke>, reference: StrokeDiagram): Double {
        val result = StrokeSimilarity.compare(drawn, reference)
        assertTrue(result != null, "no comparison was possible")
        return result!!.score
    }

    /** score, then the four term averages, so a surprise can be explained. */
    private fun describe(result: StrokeSimilarity.Result): String =
        "%.3f  [shape %.2f  length %.2f  position %.2f  topology %.2f]".format(
            result.score, result.shape, result.length, result.position, result.topology,
        )

    @Test
    fun aPerfectCopyScoresHighest() {
        val reference = diagram("\u65E5")
        val result = StrokeSimilarity.compare(StrokeSimilarity.outline(reference), reference)!!
        println("perfect copy: " + result.score)
        assertTrue(result.score > 0.95, "a perfect copy scored " + result.score)
    }

    @Test
    fun wobbleIsCheapButAMissingStrokeIsNot() {
        val reference = diagram("\u65E5")
        val copy = StrokeSimilarity.outline(reference)
        val wobbly = StrokeSimilarity.compare(jitter(copy, amount = 6f), reference)!!
        val missing = StrokeSimilarity.compare(copy.drop(1), reference)!!
        val halved = StrokeSimilarity.compare(copy.take(2), reference)!!
        val moved = StrokeSimilarity.compare(shift(copy, setOf(1), 45f, 0f), reference)!!

        println("wobbly:  " + describe(wobbly))
        println("missing: " + missing.score + "  (worst stroke " + missing.worstStroke + ")")
        println("halved:  " + describe(halved))
        println("moved:   " + describe(moved))

        assertTrue(wobbly.score > 0.90, "wobble should be nearly free, was " + wobbly.score)
        assertTrue(missing.score < wobbly.score - 0.15, "a missing stroke must cost clearly more")
        assertTrue(halved.score < 0.6, "half a character should not look like the whole one")
        assertTrue(moved.score < wobbly.score - 0.15, "a displaced stroke must cost clearly more")
    }

    @Test
    fun aSingleMissingStrokeIsCaughtEvenInALongCharacter() {
        // The case that started this: 日 drawn where 目 was wanted, and the same
        // slip in an eighteen-stroke character, which averaging alone would hide.
        val me = diagram("\u76EE")
        val hi = diagram("\u65E5")
        val drawnAsHi = StrokeSimilarity.compare(StrokeSimilarity.outline(hi), me)!!
        val correct = StrokeSimilarity.compare(StrokeSimilarity.outline(me), me)!!
        println("\u76EE correct: " + correct.score)
        println("\u76EE drawn as \u65E5: " + drawnAsHi.score)

        val long = diagram("\u66DC")   // 曜, 18 strokes
        val withoutLast = StrokeSimilarity.compare(StrokeSimilarity.outline(long).dropLast(1), long)!!
        println("\u66DC minus its last stroke: " + withoutLast.score)

        assertTrue(correct.score > 0.9)
        assertTrue(drawnAsHi.score < correct.score - 0.15, "日 for 目 scored " + drawnAsHi.score)
        assertTrue(withoutLast.score < correct.score - 0.15, "a missing stroke in 曜 scored " + withoutLast.score)
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
        // 主 and 玉 are nearly the same ink with the dot in a different place,
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

    /**
     * Recording of a real failure: 二 with the bottom bar drawn short.
     *
     * Measured from the screenshot, the drawing was within 0.05 of the reference
     * on both bar positions, the gap (0.442 against 0.440) and the top bar's
     * length; only the bottom bar was wrong, at 69% of its length. The old metric
     * turned that one error into 26% because the shorter bar shrank the frame the
     * gap and the top bar were measured in.
     */
    @Test
    fun aShortStrokeIsOneMistakeNotFour() {
        val two = diagram("\u4E8C")
        val copy = StrokeSimilarity.outline(two)
        val clean = score(copy, two)
        val shortBar = StrokeSimilarity.compare(shorten(copy, index = 1, factor = 0.69f), two)!!
        println("\u4E8C clean:                 " + clean)
        println("\u4E8C bottom bar 31% short: " + shortBar.score + "  (worst stroke " + shortBar.worstStroke + ")")

        // It is a real error, so it costs -- but it is a proportion error on a
        // recognisable, correctly ordered character, so it must not be a rejection.
        assertTrue(shortBar.score < clean - 0.03, "a 31% short bar should cost something")
        assertTrue(shortBar.score > 0.70, "a 31% short bar should not fail, was " + shortBar.score)
    }

    /** Recording of a real failure: 取 with 又 drawn a little low. */
    @Test
    fun movingAComponentIsNotACatastrophe() {
        val tori = diagram("\u53D6")   // 取: 耳 then 又
        val copy = StrokeSimilarity.outline(tori)
        val clean = score(copy, tori)
        // The last two strokes are 又; everything else is 耳.
        val moved = StrokeSimilarity.compare(shift(copy, setOf(copy.size - 2, copy.size - 1), 8f, 14f), tori)!!
        println("\u53D6 clean:        " + clean)
        println("\u53D6 \u53C8 moved low:  " + moved.score + "  (worst stroke " + moved.worstStroke + ")")

        assertTrue(
            moved.score > 0.85,
            "moving one component slightly must not wreck the score, was " + moved.score,
        )
        assertTrue(moved.score < clean, "it should still cost something")
    }

    /**
     * 場, the character that could not be accepted at all.
     *
     * Twelve strokes, so a single bad one is diluted by the average -- which is
     * why the assertions are about a wobble being accepted and a missing stroke
     * of the same character not being.
     */
    @Test
    fun aLongCharacterIsAcceptedWhenItIsRight() {
        val field = diagram("\u5834")
        val copy = StrokeSimilarity.outline(field)
        val clean = StrokeSimilarity.compare(copy, field)!!
        val wobbly = StrokeSimilarity.compare(jitter(copy, amount = 4f), field)!!
        val missing = StrokeSimilarity.compare(copy.dropLast(1), field)!!
        println("\u5834 clean:   " + describe(clean) + "  (" + clean.referenceStrokes + " strokes)")
        println("\u5834 wobbly:  " + describe(wobbly))
        println("\u5834 minus a stroke: " + describe(missing))

        assertTrue(clean.score > 0.95)
        // 0.88 measured; the bar is 0.85 rather than the 0.95 a clean copy gets
        // because twelve strokes average away one noisy one, and this is the
        // character that could not be accepted at all.
        assertTrue(wobbly.score > 0.85, "a correct 場 with a small wobble scored " + wobbly.score)
        assertTrue(missing.score < 0.70, "a missing stroke in 場 scored " + missing.score)
    }

    /**
     * The overlap case, which is what the topology term is for: 羊's vertical has
     * to run *through* the bottom bar, not stop at it.
     *
     * 羊's reference has exactly one proper crossing, between strokes 4 and 5, so
     * shortening 5 removes it. The point of the assertion is that this is more
     * than a length error -- a stroke that stops short of where it crosses is the
     * visible mistake the term exists to name.
     */
    @Test
    fun aStrokeThatStopsShortOfItsCrossingIsCaught() {
        val sheep = diagram("羊")
        val copy = StrokeSimilarity.outline(sheep)
        val clean = StrokeSimilarity.compare(copy, sheep)!!
        val stoppedShort = StrokeSimilarity.compare(trimEnd(copy, index = copy.size - 1, keep = 0.35f), sheep)!!
        println("羊 clean:             " + describe(clean))
        println("羊 tail stops early:  " + describe(stoppedShort))

        assertTrue(clean.score > 0.95, "a clean 羊 scored " + clean.score)
        assertTrue(
            stoppedShort.topology < 1.0,
            "a tail that stops at the bar should lose its crossing, topology was " + stoppedShort.topology,
        )
        assertTrue(
            stoppedShort.score < clean.score - 0.12,
            "a tail that stops early scored " + stoppedShort.score,
        )
    }

    /**
     * The hardest realistic case: a long character, written on a phone.
     *
     * 選 has fifteen strokes, so nothing averages away, and a fingertip on glass
     * is a couple of units imprecise on a character over a hundred units across.
     * This is the fixture the default threshold answers to: the wobble levels
     * here are the ones the threshold has to let through.
     */
    @Test
    fun aLongCharacterSurvivesAPhoneSizedWobble() {
        val sen = diagram("選")
        val copy = StrokeSimilarity.outline(sen)
        val clean = StrokeSimilarity.compare(copy, sen)!!
        // Amount is the full width of the random offset, so 5f is about +-2.5
        // units: roughly a fingertip.
        val phone = StrokeSimilarity.compare(jitter(copy, amount = 5f), sen)!!
        val heavy = StrokeSimilarity.compare(jitter(copy, amount = 9f), sen)!!
        println("選 clean:          " + describe(clean) + "  (" + clean.referenceStrokes + " strokes)")
        println("選 phone wobble:   " + describe(phone))
        println("選 heavy wobble:   " + describe(heavy))

        assertTrue(clean.score > 0.95)
        assertTrue(
            phone.score > StudySettings.DEFAULT_SIMILARITY_PERCENT / 100.0 + 0.20,
            "a phone-sized wobble on 選 scored " + phone.score,
        )
        // And it has to be worth something: the score must fall as the drawing
        // gets worse, or the term is not measuring anything.
        assertTrue(heavy.score < phone.score, "a heavier wobble should score lower")
    }

    /**
     * A character of one stroke has only its shape to get right.
     *
     * With no other strokes there is no layout to be wrong about, and the frame is
     * fitted to that one stroke, so length and position both read 1.00 for
     * anything at all -- a vertical line, a dot, a scribble. Scoring them let a
     * horizontal line pass for a diagonal one at 68%. So for a single stroke,
     * shape is the whole score.
     */
    @Test
    fun aSingleStrokeCharacterIsJudgedOnShapeAlone() {
        val one = diagram("一")
        val copy = StrokeSimilarity.outline(one)
        val clean = StrokeSimilarity.compare(copy, one)!!
        val turned = StrokeSimilarity.compare(
            copy.map { s -> Stroke(s.points.map { StrokePoint(it.y, it.x) }) },
            one,
        )!!
        println("一 clean:           " + describe(clean))
        println("一 drawn vertical:  " + describe(turned))

        assertTrue(clean.score > 0.95, "a clean 一 scored " + clean.score)
        assertTrue(turned.score < 0.50, "a vertical line for 一 scored " + turned.score)
    }

    /** Drawing a straight line where the reference is bent is a shape error. */
    @Test
    fun straighteningEveryStrokeIsPunished() {
        val reference = diagram("\u3053")   // こ, one bent stroke
        val copy = StrokeSimilarity.outline(reference)
        val straight = StrokeSimilarity.compare(straightened(copy), reference)!!
        println("\u3053 bent:     " + score(copy, reference))
        println("\u3053 straight: " + straight.score)
        assertTrue(straight.score < 0.9, "a straightened こ scored " + straight.score)
    }

    /** A drawing that is the right shape at the wrong size, or in the corner. */
    @Test
    fun sizeAndPlacementOnTheCanvasAreFree() {
        val reference = diagram("\u65E5")
        val copy = StrokeSimilarity.outline(reference)
        val clean = score(copy, reference)
        val tiny = score(copy.map { s -> Stroke(s.points.map { StrokePoint(it.x * 0.35f, it.y * 0.35f) }) }, reference)
        val corner = score(copy.map { s -> Stroke(s.points.map { StrokePoint(it.x + 400f, it.y + 250f) }) }, reference)
        println("clean:  " + clean)
        println("tiny:   " + tiny)
        println("corner: " + corner)
        assertTrue(tiny > clean - 0.02, "drawing smaller should be free, was " + tiny)
        assertTrue(corner > clean - 0.02, "drawing elsewhere on the canvas should be free, was " + corner)
    }
}
