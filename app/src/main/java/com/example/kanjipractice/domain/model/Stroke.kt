package com.example.kanjipractice.domain.model

/**
 * A single sampled point of handwriting, in canvas pixels.
 *
 * [timestampMillis] is the device uptime at which the point was sampled. The
 * recogniser uses stroke timing, so synthetic timestamps are a real (if small)
 * loss of signal; it defaults to 0 for callers that have no clock, such as tests.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val timestampMillis: Long = 0L,
)

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
