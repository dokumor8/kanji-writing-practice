package com.example.kanjipractice.domain.model

/** A single sampled point of handwriting, in canvas pixels. */
data class StrokePoint(val x: Float, val y: Float)

/**
 * One pen-down .. pen-up stroke.
 *
 * Strokes are held as an immutable list so the review state machine can keep the
 * drawing alive across failed recognition attempts: the user undoes one bad
 * stroke instead of starting over.
 */
data class Stroke(val points: List<StrokePoint>) {
    val isEmpty: Boolean get() = points.isEmpty()
}
