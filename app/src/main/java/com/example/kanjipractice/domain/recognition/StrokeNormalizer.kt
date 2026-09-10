package com.example.kanjipractice.domain.recognition

import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint

/**
 * Rescales a drawing into a fixed internal box before it is handed to ML Kit
 * (plan, section 12).
 *
 * The canvas the user draws on is whatever size the screen gives us, and ML Kit
 * is trained on a consistent coordinate range. Normalising also removes the
 * effect of where on the canvas the character was drawn.
 *
 * The drawing is scaled uniformly (aspect ratio preserved) and centred, so a
 * narrow character such as 一 is not stretched into a square.
 */
object StrokeNormalizer {

    /** The coordinate box ML Kit is fed. */
    const val DEFAULT_TARGET_SIZE = 300f

    /** Fraction of the box left empty on each side. */
    private const val PADDING_FRACTION = 0.05f

    private const val EPSILON = 1e-3f

    fun normalize(strokes: List<Stroke>, targetSize: Float = DEFAULT_TARGET_SIZE): List<Stroke> {
        if (targetSize <= 0f) return strokes
        val points = strokes.flatMap { it.points }
        if (points.isEmpty()) return emptyList()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        val spanX = maxX - minX
        val spanY = maxY - minY
        val inner = targetSize * (1f - 2f * PADDING_FRACTION)

        // A dot or a straight line has a zero span in one axis; fall back to the
        // other axis, and to "leave it alone" when there is no span at all.
        val scale = when {
            spanX > EPSILON && spanY > EPSILON -> minOf(inner / spanX, inner / spanY)
            spanX > EPSILON -> inner / spanX
            spanY > EPSILON -> inner / spanY
            else -> 1f
        }

        val offsetX = (targetSize - spanX * scale) / 2f - minX * scale
        val offsetY = (targetSize - spanY * scale) / 2f - minY * scale

        return strokes.map { stroke ->
            Stroke(
                stroke.points.map { p ->
                    StrokePoint(p.x * scale + offsetX, p.y * scale + offsetY)
                }
            )
        }
    }
}
