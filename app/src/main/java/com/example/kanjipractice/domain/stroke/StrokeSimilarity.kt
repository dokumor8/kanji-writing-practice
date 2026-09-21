package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How closely a drawing reproduces a reference stroke diagram.
 *
 * The recogniser answers "which character is this?" -- a different question from
 * "did you draw *this* character correctly?". It picks the nearest character, so
 * it will accept a drawing with strokes missing, and it can reject a correct
 * drawing that resembles a neighbour. This answers the second question.
 *
 * **What went wrong before.** The first version scored a stroke purely by how
 * close its points were to the reference's points, in a frame normalised from the
 * drawing's own bounding box. That made one mistake in one stroke move the frame
 * and therefore change every other stroke's score:
 *
 *  - 取 with 又 a little low: 又's two strokes fell outside tolerance, and with
 *    two of eight strokes at zero the worst-stroke weighting took the total to
 *    45% for a drawing that was correct in shape.
 *  - 二 with the bottom bar drawn short: shortening it shrank the bounding box
 *    width, so a gap that was 0.442 of the drawing area (the reference is 0.440)
 *    was measured as 0.82 against a reference of 0.57, and a top bar of correct
 *    length was measured as too long. One error in one stroke became four
 *    apparent errors in the other.
 *
 * **What it does now.** Four separate questions, each measured where it is
 * meaningful, and each with its own tolerance:
 *
 *  - **shape** -- is this the right stroke? Measured per stroke after removing
 *    the stroke's own position and size, so a stroke of the right shape in the
 *    wrong place keeps its shape score and pays only on position. This is where a
 *    straight line drawn for an L-shaped stroke fails.
 *  - **length** -- is it as long as it should be? Catches a stroke that stops
 *    short of where it should reach, which is how a missing overlap shows up.
 *  - **position** -- is it in the right place relative to the others? Deliberately
 *    forgiving, because handwriting moves components around, but kept because for
 *    大 against 犬 the dot's position *is* the character.
 *  - **topology** -- do the strokes the reference crosses still cross? This is the
 *    one that is purely about overlap rather than distance.
 *
 * Three things keep the measurement itself from inventing errors:
 *
 *  - the drawing is **smoothed** first, because raw finger samples zigzag and a
 *    zigzag is longer than the line it was meant to be;
 *  - **shape is normalised per stroke with a floor**, so a two-unit dot does not
 *    have its finger noise magnified into a completely different shape;
 *  - the **frame is chosen by search** rather than computed once. Any single
 *    formula for "how big was this drawing" moves when one stroke changes, and a
 *    frame that moves rescales every other stroke. So the scale is picked by
 *    trying a range and keeping whichever makes the drawing look best.
 *
 * KanjiVG carries no stroke width, so this cannot tell a confident stroke from a
 * tentative one, and it says nothing about where on the paper you wrote or how
 * large.
 */
object StrokeSimilarity {

    /** Points per stroke after resampling, so a long stroke does not outweigh a dot. */
    private const val SAMPLES_PER_STROKE = 32

    private const val CURVE_SEGMENTS = 8

    /** Passes of a 1-2-1 kernel over the strokes. Finger input is noisy. */
    private const val SMOOTHING_PASSES = 4

    /**
     * How far a point may sit from its counterpart before it counts as wrong, in
     * units of the stroke's own radius. Generous: this asks "the right stroke?",
     * not "the same wobble?".
     */
    private const val SHAPE_TOLERANCE = 0.55

    /**
     * A stroke whose own radius is below this share of the character is measured
     * against this instead, so finger noise on a short stroke is not magnified.
     */
    private const val RADIUS_FLOOR_FRACTION = 0.07

    /** A length error this large, as a fraction of the reference, scores zero. */
    private const val LENGTH_TOLERANCE = 0.35

    /**
     * A displacement this large, as a fraction of the character, scores zero.
     *
     * Generous on purpose. Moving a component a little is the most common thing
     * handwriting does -- 取 with 又 a few units low is still 取 -- and it was the
     * least forgiving term in the version that failed people.
     */
    private const val POSITION_TOLERANCE = 0.35

    /** Below this fraction of the character, a stroke is a dot, not a line. */
    private const val DOT_FRACTION = 0.10

    private const val SHAPE_WEIGHT = 0.45
    private const val LENGTH_WEIGHT = 0.20
    private const val POSITION_WEIGHT = 0.35

    /**
     * How much each term counts, which is not the same for every character.
     *
     * For a character of one stroke there is no layout to get right: there are no
     * other strokes to be in the wrong place relative to, and the frame is fitted
     * to that stroke alone, so length and position both read 1.00 *whatever* was
     * drawn. Scoring them would only dilute the one term that carries any
     * information -- which let a horizontal line pass for a diagonal one. So for
     * a single stroke, shape is everything.
     */
    private data class Weights(val shape: Double, val length: Double, val position: Double)

    private fun weightsFor(reference: List<List<Vec2>>): Weights =
        if (reference.size < 2) Weights(1.0, 0.0, 0.0)
        else Weights(SHAPE_WEIGHT, LENGTH_WEIGHT, POSITION_WEIGHT)

    /**
     * How much a broken crossing pattern can cost.
     *
     * Only a correction, never the verdict. Crossings are derived geometry, and
     * on real characters the reference often has none at all, so an invented
     * crossing must not be treated as equal to a missing one -- that made ordinary
     * handwriting fail. Only a crossing the reference *has* and the drawing does
     * not costs anything.
     */
    private const val TOPOLOGY_WEIGHT = 0.10

    /**
     * How much the single worst stroke counts, against the average.
     *
     * Lower than it was. The old value of 0.4 was chosen when a stroke's score
     * collapsed to zero for a small positional slip; now that shape, length and
     * position are separate, a stroke has to be genuinely wrong to fall far, so a
     * smaller weight is enough to keep one bad stroke visible without letting it
     * decide the whole character.
     */
    private const val WORST_STROKE_WEIGHT = 0.25

    /** Cost per stroke of drawing too few or too many. */
    private const val COUNT_PENALTY_PER_STROKE = 0.4

    private const val MAX_COUNT_PENALTY = 0.9

    /** The frame search: multiples of the first estimate, and how finely it steps. */
    private const val SCALE_SEARCH_MIN = 0.5
    private const val SCALE_SEARCH_MAX = 2.0
    private const val SCALE_SEARCH_STEPS = 24

    /**
     * How far from a segment's ends an intersection must be to count.
     *
     * Most crossings in real diagrams are between a stroke and the middle of
     * another, but a few sit a quarter of the way along a segment. Below that the
     * answer flips with a millimetre of finger noise, and a term that flips is
     * worse than no term, so the bar is raised and the weight kept low.
     */
    private const val EDGE_FRACTION = 0.15f

    private const val EPSILON = 1e-6

    data class Result(
        /** 0..1; 1 is a perfect reproduction. */
        val score: Double,
        /** The lowest-scoring stroke, so one bad stroke is visible. */
        val worstStroke: Double,
        /** Strokes the drawing was missing, or had in excess. */
        val unmatchedStrokes: Int,
        val referenceStrokes: Int,
        val drawnStrokes: Int,
        /** Which of the four questions the drawing did worst on, for messages. */
        val weakest: Term,
        /** Term averages, so a surprising score can be explained. */
        val shape: Double,
        val length: Double,
        val position: Double,
        val topology: Double,
    ) {
        val percent: Int get() = Math.round(score * 100).toInt()
    }

    enum class Term { SHAPE, LENGTH, POSITION, TOPOLOGY }

    /** The reference diagram as drawable strokes, in stroke order. */
    fun outline(diagram: StrokeDiagram): List<Stroke> =
        diagram.strokes
            .map { flatten(it) }
            .filter { it.size >= 2 }
            .map { points -> Stroke(points.map { StrokePoint(it.x, it.y) }) }

    /**
     * @return null when either side has no geometry to compare, in which case the
     *   caller should fall back to the recogniser alone rather than guess.
     */
    fun compare(drawn: List<Stroke>, reference: StrokeDiagram): Result? {
        if (reference.strokes.isEmpty()) return null

        // Smoothing happens *before* resampling, and on both sides. The order is
        // not cosmetic: a raw finger trace zigzags, a zigzag is longer than the
        // line it was meant to be, and resampling by arc length distributes its
        // samples according to that inflated length. Smoothing afterwards cannot
        // undo it -- the samples are already in the wrong places.
        //
        // Both sides are treated alike because smoothing shortens a curved stroke
        // slightly, and doing it to the drawing only made a perfect copy of 取
        // score 0.92.
        val ref = reference.strokes
            .map { flatten(it) }
            .filter { it.size >= 2 }
            .map { resample(smooth(it, SMOOTHING_PASSES), SAMPLES_PER_STROKE) }
        if (ref.isEmpty()) return null

        val ink = drawn
            .filter { it.points.isNotEmpty() }
            .map { stroke ->
                val raw = stroke.points.map { Vec2(it.x, it.y) }
                resample(smooth(raw, SMOOTHING_PASSES), SAMPLES_PER_STROKE)
            }
        if (ink.isEmpty()) return null

        val extent = extentOf(ref)
        val dotLength = extent * DOT_FRACTION
        val radiusFloor = extent * RADIUS_FLOOR_FRACTION

        val weights = weightsFor(ref)
        val placed = bestFrame(ink, ref, extent, dotLength, radiusFloor, weights)

        val total = max(ref.size, placed.size)
        val shape = DoubleArray(total)
        val length = DoubleArray(total)
        val position = DoubleArray(total)
        val combined = DoubleArray(total)

        for (i in 0 until total) {
            if (i >= ref.size || i >= placed.size) {
                // A stroke that is not there, or is there and should not be.
                combined[i] = 0.0
                continue
            }
            shape[i] = shapeScore(placed[i], ref[i], radiusFloor)
            length[i] = lengthScore(placed[i], ref[i], dotLength)
            position[i] = positionScore(placed[i], ref[i], extent)
            combined[i] = weights.shape * shape[i] +
                weights.length * length[i] +
                weights.position * position[i]
        }

        val mean = combined.average()
        val worst = combined.minOrNull() ?: 0.0
        val base = (1.0 - WORST_STROKE_WEIGHT) * mean + WORST_STROKE_WEIGHT * worst

        val missing = abs(ref.size - placed.size)
        val countPenalty = 1.0 - min(MAX_COUNT_PENALTY, COUNT_PENALTY_PER_STROKE * missing)

        val topology = topologyScore(placed, ref)

        val score = (base * countPenalty * (1.0 - TOPOLOGY_WEIGHT * (1.0 - topology)))
            .coerceIn(0.0, 1.0)

        return Result(
            score = score,
            worstStroke = worst,
            unmatchedStrokes = missing,
            referenceStrokes = ref.size,
            drawnStrokes = placed.size,
            weakest = weakestTerm(shape, length, position, topology),
            shape = shape.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            length = length.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            position = position.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            topology = topology,
        )
    }

    // ---- the four questions -------------------------------------------------

    /**
     * The share of the stroke that reproduces the reference's shape, with both
     * sides moved to a common centre and scaled to a common size first.
     *
     * Removing position and size here is the whole point: it is what lets a
     * correctly drawn 又 in the wrong place score on shape and pay on position,
     * instead of scoring nothing at all. The floor stops that removal from
     * magnifying finger noise on a stroke too short to have a meaningful shape.
     */
    private fun shapeScore(drawn: List<Vec2>, reference: List<Vec2>, radiusFloor: Double): Double {
        val a = normaliseStroke(drawn, radiusFloor)
        val b = normaliseStroke(reference, radiusFloor)
        var total = 0.0
        for (i in a.indices) {
            val d = distance(a[i], b[i])
            total += (1.0 - d / SHAPE_TOLERANCE).coerceIn(0.0, 1.0)
        }
        return total / a.size
    }

    /** How close the stroke's length is to the reference's. */
    private fun lengthScore(drawn: List<Vec2>, reference: List<Vec2>, dotLength: Double): Double {
        val d = pathLength(drawn)
        val r = pathLength(reference)
        // A dot is a dot however long the finger lingered; only compare lengths
        // once at least one of them is a line.
        if (d < dotLength && r < dotLength) return 1.0
        if (r < EPSILON) return if (d < dotLength) 1.0 else 0.0
        return (1.0 - abs(d - r) / (LENGTH_TOLERANCE * r)).coerceIn(0.0, 1.0)
    }

    /** How far the stroke's centre is from the reference's, as a fraction of the character. */
    private fun positionScore(drawn: List<Vec2>, reference: List<Vec2>, extent: Double): Double {
        if (extent < EPSILON) return 1.0
        val d = distance(centroid(drawn), centroid(reference)) / extent
        return (1.0 - d / POSITION_TOLERANCE).coerceIn(0.0, 1.0)
    }

    /**
     * Whether the crossings the reference has are still there.
     *
     * Only the reference's crossings are required: an invented one is not
     * penalised, because on a character whose strokes meet end-to-end a little
     * wobble turns a touch into a crossing and charging for that failed correct
     * drawings.
     *
     * The test for "still there" is deliberately loose about *how*. Asking for a
     * strict crossing was wrong on a phone: a stroke told to run through another
     * often stops a hair short, because there is not enough precision in a
     * fingertip to guarantee a through-crossing every time. That is not the
     * mistake this term is for, and with the strict test it flipped on and off
     * between attempts -- 選 measured *worse* with less wobble. A pair now counts
     * as preserved if the strokes cross **or** come within a hair of each other,
     * so what fails is a real gap: the tail that stops well short.
     */
    private fun topologyScore(drawn: List<List<Vec2>>, reference: List<List<Vec2>>): Double {
        val required = crossingPairs(reference)
        if (required.isEmpty()) return 1.0
        val slack = extent(reference) * CROSSING_SLACK_FRACTION
        var kept = 0
        for (key in required) {
            val i = (key / KEY_STRIDE).toInt()
            val j = (key % KEY_STRIDE).toInt()
            if (i < drawn.size && j < drawn.size && strokesMeet(drawn[i], drawn[j], slack)) kept++
        }
        return kept.toDouble() / required.size
    }

    /** True when two strokes cross, or pass within [slack] of each other. */
    private fun strokesMeet(a: List<Vec2>, b: List<Vec2>, slack: Double): Boolean {
        for (k in 0 until a.size - 1) {
            for (l in 0 until b.size - 1) {
                if (segmentDistance(a[k], a[k + 1], b[l], b[l + 1]) <= slack) return true
            }
        }
        return false
    }

    /** Shortest distance between two segments; zero when they cross. */
    private fun segmentDistance(p1: Vec2, p2: Vec2, p3: Vec2, p4: Vec2): Double {
        if (segmentsCross(p1, p2, p3, p4)) return 0.0
        return minOf(
            pointToSegment(p1, p3, p4),
            pointToSegment(p2, p3, p4),
            pointToSegment(p3, p1, p2),
            pointToSegment(p4, p1, p2),
        )
    }

    private fun pointToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < EPSILON) return distance(p, a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot((p.x - a.x) - t * dx, (p.y - a.y) - t * dy)
    }

    private fun weakestTerm(
        shape: DoubleArray,
        length: DoubleArray,
        position: DoubleArray,
        topology: Double,
    ): Term {
        val s = shape.average()
        val l = length.average()
        val p = position.average()
        return when (minOf(s, l, p, topology)) {
            s -> Term.SHAPE
            l -> Term.LENGTH
            p -> Term.POSITION
            else -> Term.TOPOLOGY
        }
    }

    // ---- the frame ----------------------------------------------------------

    /**
     * Picks the translation and uniform scale that make the drawing look most like
     * the reference, rather than computing them from one formula.
     *
     * A formula has to be fed something -- a bounding box, the spread of the
     * stroke centres -- and whatever it is fed moves when a single stroke changes,
     * which rescales every *other* stroke. That is the bug that produced both
     * recorded failures. Searching instead means one wrong stroke can only make
     * the frame worse for itself, and the range is wide but bounded, so a scribble
     * still cannot be scaled into a match.
     */
    private fun bestFrame(
        drawn: List<List<Vec2>>,
        reference: List<List<Vec2>>,
        extent: Double,
        dotLength: Double,
        radiusFloor: Double,
        weights: Weights,
    ): List<List<Vec2>> {
        val dMean = mean(centroidList(drawn))
        val rMean = mean(centroidList(reference))
        val guess = initialScale(drawn, reference, dMean, rMean)

        var bestScore = Double.NEGATIVE_INFINITY
        var best = place(drawn, dMean, rMean, guess)
        for (step in 0..SCALE_SEARCH_STEPS) {
            val factor = SCALE_SEARCH_MIN +
                (SCALE_SEARCH_MAX - SCALE_SEARCH_MIN) * step / SCALE_SEARCH_STEPS
            val candidate = place(drawn, dMean, rMean, guess * factor)
            val score = meanStrokeScore(candidate, reference, extent, dotLength, radiusFloor, weights)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun place(
        drawn: List<List<Vec2>>,
        dMean: Vec2,
        rMean: Vec2,
        scale: Double,
    ): List<List<Vec2>> = drawn.map { stroke ->
        stroke.map { p ->
            Vec2(
                ((p.x - dMean.x) * scale).toFloat() + rMean.x,
                ((p.y - dMean.y) * scale).toFloat() + rMean.y,
            )
        }
    }

    /**
     * A first guess at the scale: start from the spread of the stroke centres,
     * which is close when the drawing is roughly right, and let the search widen
     * it. Falls back to overall size for a single stroke.
     */
    private fun initialScale(
        drawn: List<List<Vec2>>,
        reference: List<List<Vec2>>,
        dMean: Vec2,
        rMean: Vec2,
    ): Double {
        val dSpread = spread(centroidList(drawn), dMean)
        val rSpread = spread(centroidList(reference), rMean)
        val scale = if (dSpread > EPSILON && rSpread > EPSILON) {
            rSpread / dSpread
        } else {
            val de = extent(drawn)
            val re = extent(reference)
            if (de > EPSILON) re / de else 1.0
        }
        // A scribble must not be scaled into a match; a real drawing is never more
        // than a few times off.
        return scale.coerceIn(0.05, 20.0)
    }

    private fun meanStrokeScore(
        drawn: List<List<Vec2>>,
        reference: List<List<Vec2>>,
        extent: Double,
        dotLength: Double,
        radiusFloor: Double,
        weights: Weights,
    ): Double {
        val total = max(reference.size, drawn.size)
        if (total == 0) return 1.0
        var sum = 0.0
        for (i in 0 until total) {
            if (i >= reference.size || i >= drawn.size) continue
            sum += weights.shape * shapeScore(drawn[i], reference[i], radiusFloor) +
                weights.length * lengthScore(drawn[i], reference[i], dotLength) +
                weights.position * positionScore(drawn[i], reference[i], extent)
        }
        return sum / total
    }

    private fun centroidList(strokes: List<List<Vec2>>): List<Vec2> = strokes.map { centroid(it) }

    private fun mean(points: List<Vec2>): Vec2 =
        if (points.isEmpty()) Vec2(0f, 0f)
        else Vec2(
            (points.sumOf { it.x.toDouble() } / points.size).toFloat(),
            (points.sumOf { it.y.toDouble() } / points.size).toFloat(),
        )

    /** Root-mean-square distance from [centre]. */
    private fun spread(points: List<Vec2>, centre: Vec2): Double {
        if (points.isEmpty()) return 0.0
        var total = 0.0
        for (p in points) {
            val d = distance(p, centre)
            total += d * d
        }
        return sqrt(total / points.size)
    }

    private fun centroid(points: List<Vec2>): Vec2 {
        if (points.isEmpty()) return Vec2(0f, 0f)
        var x = 0.0
        var y = 0.0
        for (p in points) {
            x += p.x
            y += p.y
        }
        return Vec2((x / points.size).toFloat(), (y / points.size).toFloat())
    }

    /** The diagonal of the ink's bounding box, in the frame it is given in. */
    private fun extent(strokes: List<List<Vec2>>): Double {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (s in strokes) for (p in s) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        if (minX > maxX) return 0.0
        return hypot((maxX - minX).toDouble(), (maxY - minY).toDouble())
    }

    private fun extentOf(strokes: List<List<Vec2>>): Double = extent(strokes)

    // ---- crossings ----------------------------------------------------------

    /** Packing stride for crossing keys. No character has a thousand strokes. */
    private const val KEY_STRIDE = 1000L

    /** How near is near enough to count as a crossing that was kept. */
    private const val CROSSING_SLACK_FRACTION = 0.04

    /** Pairs of strokes that properly cross, as packed i*n+j keys. */
    private fun crossingPairs(strokes: List<List<Vec2>>): List<Long> {
        val out = ArrayList<Long>()
        for (i in strokes.indices) {
            for (j in i + 1 until strokes.size) {
                if (crosses(strokes[i], strokes[j])) out += i.toLong() * 1000L + j
            }
        }
        return out
    }

    private fun crosses(a: List<Vec2>, b: List<Vec2>): Boolean {
        for (k in 0 until a.size - 1) {
            for (l in 0 until b.size - 1) {
                if (segmentsCross(a[k], a[k + 1], b[l], b[l + 1])) return true
            }
        }
        return false
    }

    /**
     * Do two segments cross properly -- that is, through each other's middle?
     *
     * Strictly interior, so a stroke that merely *ends* on another one does not
     * count. That distinction is the point: the tail of 羊 reaching through the
     * bottom bar is a crossing, and the same tail stopping at the bar is not.
     */
    private fun segmentsCross(p1: Vec2, p2: Vec2, p3: Vec2, p4: Vec2): Boolean {
        val d1x = p2.x - p1.x
        val d1y = p2.y - p1.y
        val d2x = p4.x - p3.x
        val d2y = p4.y - p3.y
        val denom = d1x * d2y - d1y * d2x
        if (abs(denom) < EPSILON) return false

        val t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom
        val u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denom
        return t > EDGE_FRACTION && t < 1f - EDGE_FRACTION &&
            u > EDGE_FRACTION && u < 1f - EDGE_FRACTION
    }

    // ---- geometry -----------------------------------------------------------

    /** Moves a stroke to the origin and scales it to unit radius, with a floor. */
    private fun normaliseStroke(points: List<Vec2>, radiusFloor: Double): List<Vec2> {
        val centre = centroid(points)
        val radius = max(spread(points, centre), radiusFloor).coerceAtLeast(EPSILON)
        return points.map {
            Vec2(((it.x - centre.x) / radius).toFloat(), ((it.y - centre.y) / radius).toFloat())
        }
    }

    /**
     * A 1-2-1 smoothing pass, endpoints held.
     *
     * Raw finger samples are noisy enough that they zigzag, and a zigzag is longer
     * than the line it was meant to be -- enough to read as a 40% length error on
     * a drawing that is the right length. Smoothing before measuring removes the
     * noise instead of scoring it.
     */
    private fun smooth(points: List<Vec2>, passes: Int): List<Vec2> {
        if (points.size < 3) return points
        var current = points
        repeat(passes) {
            val next = ArrayList<Vec2>(current.size)
            next += current.first()
            for (i in 1 until current.size - 1) {
                next += Vec2(
                    (current[i - 1].x + 2f * current[i].x + current[i + 1].x) / 4f,
                    (current[i - 1].y + 2f * current[i].y + current[i + 1].y) / 4f,
                )
            }
            next += current.last()
            current = next
        }
        return current
    }

    private fun pathLength(points: List<Vec2>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        return total
    }

    /** Even spacing along the stroke, so each sample carries equal weight. */
    private fun resample(points: List<Vec2>, count: Int): List<Vec2> {
        if (points.size == 1) return List(count) { points[0] }
        val lengths = DoubleArray(points.size)
        for (i in 1 until points.size) {
            lengths[i] = lengths[i - 1] + distance(points[i - 1], points[i])
        }
        val total = lengths.last()
        if (total <= 0.0) return List(count) { points[0] }

        val out = ArrayList<Vec2>(count)
        var segment = 1
        for (i in 0 until count) {
            val target = total * i / (count - 1).toDouble()
            while (segment < points.size - 1 && lengths[segment] < target) segment++
            val start = lengths[segment - 1]
            val end = lengths[segment]
            val t = if (end > start) ((target - start) / (end - start)).toFloat() else 0f
            out += Vec2(
                points[segment - 1].x + (points[segment].x - points[segment - 1].x) * t,
                points[segment - 1].y + (points[segment].y - points[segment - 1].y) * t,
            )
        }
        return out
    }

    /** SVG commands to a polyline, with curves sampled rather than jumped. */
    private fun flatten(commands: List<SvgPathCommand>): List<Vec2> {
        val out = ArrayList<Vec2>()
        var current = Vec2(0f, 0f)
        for (command in commands) {
            when (command) {
                is SvgPathCommand.MoveTo -> {
                    current = Vec2(command.x, command.y)
                    out += current
                }

                is SvgPathCommand.LineTo -> {
                    current = Vec2(command.x, command.y)
                    out += current
                }

                is SvgPathCommand.CubicTo -> {
                    val from = current
                    for (step in 1..CURVE_SEGMENTS) {
                        val t = step.toFloat() / CURVE_SEGMENTS
                        out += cubic(from, Vec2(command.x1, command.y1), Vec2(command.x2, command.y2), Vec2(command.x, command.y), t)
                    }
                    current = Vec2(command.x, command.y)
                }

                is SvgPathCommand.QuadTo -> {
                    val from = current
                    for (step in 1..CURVE_SEGMENTS) {
                        val t = step.toFloat() / CURVE_SEGMENTS
                        out += quadratic(from, Vec2(command.x1, command.y1), Vec2(command.x, command.y), t)
                    }
                    current = Vec2(command.x, command.y)
                }

                SvgPathCommand.Close -> Unit
            }
        }
        return out
    }

    private fun cubic(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Float): Vec2 {
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        return Vec2(
            a * p0.x + b * p1.x + c * p2.x + d * p3.x,
            a * p0.y + b * p1.y + c * p2.y + d * p3.y,
        )
    }

    private fun quadratic(p0: Vec2, p1: Vec2, p2: Vec2, t: Float): Vec2 {
        val u = 1f - t
        return Vec2(
            u * u * p0.x + 2f * u * t * p1.x + t * t * p2.x,
            u * u * p0.y + 2f * u * t * p1.y + t * t * p2.y,
        )
    }

    private fun distance(a: Vec2, b: Vec2): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private data class Vec2(val x: Float, val y: Float)
}
