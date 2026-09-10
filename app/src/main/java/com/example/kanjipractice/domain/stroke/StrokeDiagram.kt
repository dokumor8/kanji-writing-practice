package com.example.kanjipractice.domain.stroke

/** A drawing instruction produced by [SvgPathParser]. */
sealed interface SvgPathCommand {
    data class MoveTo(val x: Float, val y: Float) : SvgPathCommand
    data class LineTo(val x: Float, val y: Float) : SvgPathCommand
    data class CubicTo(
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float,
        val x: Float, val y: Float,
    ) : SvgPathCommand

    data class QuadTo(
        val x1: Float, val y1: Float,
        val x: Float, val y: Float,
    ) : SvgPathCommand

    data object Close : SvgPathCommand
}

/** Position of a stroke-order number in KanjiVG coordinates. */
data class StrokeNumber(val x: Float, val y: Float, val label: String)

/**
 * A character's stroke-order diagram, in the SVG's own coordinate space.
 *
 * KanjiVG draws every character in a 109x109 view box; the renderer scales that
 * to whatever square it is given. [strokes] is in KanjiVG's stroke order, which
 * is the whole point of the diagram.
 */
data class StrokeDiagram(
    val viewBoxWidth: Float,
    val viewBoxHeight: Float,
    val strokes: List<List<SvgPathCommand>>,
    val numbers: List<StrokeNumber>,
) {
    val strokeCount: Int get() = strokes.size

    companion object {
        val EMPTY = StrokeDiagram(109f, 109f, emptyList(), emptyList())
    }
}
