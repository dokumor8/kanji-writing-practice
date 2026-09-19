package com.example.kanjipractice.domain.stroke

import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * How closely a drawing reproduces a reference stroke diagram.
 *
 * The recogniser answers "which character is this?" -- a different question from
 * "did you draw *this* character correctly?", and the one that matters here. A
 * model can read a drawing as the target while several strokes are missing or in
 * the wrong place, because it is choosing the nearest character rather than
 * checking the drawing against one. This measures the drawing against one.
 *
 * Both sides are ordered strokes, so stroke *i* is compared with stroke *i*. That
 * is deliberately unforgiving about order: drawing 主's strokes in 玉's order
 * misaligns every stroke after the first, which is the correct verdict for a
 * production drill even though a recogniser would happily accept it.
 *
 * The comparison is shape-only. Position on the canvas and absolute size are
 * normalised away, so it says nothing about where on the paper you wrote, and
 * KanjiVG carries no stroke width, so it cannot tell a confident stroke from a
 * tentative one.
 */
object StrokeSimilarity {

    /** Fraction of the character's size within which two points count as the same. */
    const val TOLERANCE = 0.12

    /** Points sampled per stroke, so a long stroke does not outweigh a dot. */
    private const val SAMPLES_PER_STROKE = 24

    private const val CURVE_SEGMENTS = 8

    /**
     * How much the single worst stroke counts for, against the average.
     *
     * Averaging alone dilutes one bad stroke by the number of strokes: missing
     * the last stroke of a twenty-stroke character still averages 0.95, which is
     * exactly the kind of quiet slip this is meant to catch. Weighting the worst
     * stroke makes one stroke wrong cost something whatever the character's size,
     * while the average keeps a single wobbly stroke from dominating.
     */
    private const val WORST_STROKE_WEIGHT = 0.4

    data class Result(
        /** 0..1; 1 is a perfect reproduction. */
        val score: Double,
        /** The worst-matching stroke, so a single bad stroke is visible. */
        val worstStroke: Double,
        /** Strokes the drawing was missing, or had in excess. */
        val unmatchedStrokes: Int,
        val referenceStrokes: Int,
        val drawnStrokes: Int,
    ) {
        val percent: Int get() = Math.round(score * 100).toInt()
    }

    /**
     * The reference diagram as drawable strokes, in stroke order.
     *
     * The inverse of what the app stores: useful for tests, and for anything that
     * wants to show the reference as a drawing rather than as paths.
     */
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
        val referenceStrokes = reference.strokes
            .map { flatten(it) }
            .filter { it.size >= 2 }
        val drawnStrokes = drawn
            .map { stroke -> stroke.points.map { Vec2(it.x, it.y) } }
            .filter { it.isNotEmpty() }
        if (referenceStrokes.isEmpty() || drawnStrokes.isEmpty()) return null

        val referencePoints = normalise(referenceStrokes)
        val drawnPoints = normalise(drawnStrokes)

        // Stroke for stroke, over *all* the strokes either side has. A stroke the
        // drawing does not have scores zero rather than being averaged away --
        // otherwise drawing half a character scores well on the half it drew,
        // which is precisely the mistake this exists to catch.
        val total = maxOf(referencePoints.size, drawnPoints.size)
        val perStroke = (0 until total).map { index ->
            if (index < referencePoints.size && index < drawnPoints.size) {
                symmetricMatch(referencePoints[index], drawnPoints[index])
            } else {
                0.0
            }
        }

        val mean = perStroke.average()
        val worst = perStroke.minOrNull() ?: 0.0
        val blended = (1.0 - WORST_STROKE_WEIGHT) * mean + WORST_STROKE_WEIGHT * worst

        return Result(
            score = blended.coerceIn(0.0, 1.0),
            worstStroke = worst,
            unmatchedStrokes = abs(referencePoints.size - drawnPoints.size),
            referenceStrokes = referencePoints.size,
            drawnStrokes = drawnPoints.size,
        )
    }

    /**
     * The share of each stroke's points that have a counterpart in the other,
     * averaged in both directions so neither a missing nor a stray stroke can hide.
     */
    private fun symmetricMatch(a: List<Vec2>, b: List<Vec2>): Double {
        val aMatched = a.count { point -> b.any { distance(point, it) <= TOLERANCE } }
        val bMatched = b.count { point -> a.any { distance(point, it) <= TOLERANCE } }
        return (aMatched.toDouble() / a.size + bMatched.toDouble() / b.size) / 2.0
    }

    /** Every stroke resampled to the same point count, inside one shared unit box. */
    private fun normalise(strokes: List<List<Vec2>>): List<List<Vec2>> {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (stroke in strokes) {
            for (p in stroke) {
                if (p.x < minX) minX = p.x
                if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y
                if (p.y > maxY) maxY = p.y
            }
        }
        val width = maxX - minX
        val height = maxY - minY
        // Uniform scale, so a wide character stays wide instead of being stretched
        // into the box and matching one that should look different.
        val scale = if (max(width, height) > 0f) 1f / max(width, height) else 1f
        val offsetX = (1f - width * scale) / 2f
        val offsetY = (1f - height * scale) / 2f

        return strokes.map { stroke ->
            resample(
                stroke.map { Vec2((it.x - minX) * scale + offsetX, (it.y - minY) * scale + offsetY) },
                SAMPLES_PER_STROKE,
            )
        }
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
