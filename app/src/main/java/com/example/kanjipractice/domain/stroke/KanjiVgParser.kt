package com.example.kanjipractice.domain.stroke

/**
 * Turns a KanjiVG SVG into a [StrokeDiagram].
 *
 * KanjiVG output is machine generated and rigid, so a couple of anchored
 * regular expressions are enough -- and unlike an XML pull parser they run in
 * plain JVM unit tests. The strokes live before the StrokeNumbers group and are
 * already in stroke order.
 */
object KanjiVgParser {

    private val VIEW_BOX = Regex("""viewBox="([^"]*)"""")

    // The word boundary stops these from also matching id="..." / attr="...".
    private val PATH = Regex("""<path\b[^>]*?\bd="([^"]*)"""")

    private val TEXT = Regex("""<text\b[^>]*?\btransform="matrix\(([^)]*)\)"[^>]*>([^<]*)</text>""")

    private const val NUMBERS_MARKER = "kvg:StrokeNumbers"
    private const val DEFAULT_VIEW_BOX = 109f

    fun parse(svg: String): StrokeDiagram {
        val strokeSection = svg.substringBefore(NUMBERS_MARKER)
        val numberSection = svg.substringAfter(NUMBERS_MARKER, "")

        val strokes = PATH.findAll(strokeSection)
            .map { SvgPathParser.parse(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .toList()

        val numbers = TEXT.findAll(numberSection).mapNotNull { match ->
            // matrix(a b c d e f) -- the translation is the fifth and sixth entry.
            val matrix = match.groupValues[1]
                .split(' ', ',', '\t', '\n')
                .filter { it.isNotBlank() }
                .mapNotNull { it.toFloatOrNull() }
            val label = match.groupValues[2].trim()
            if (matrix.size < 6 || label.isEmpty()) {
                null
            } else {
                StrokeNumber(matrix[4], matrix[5], label)
            }
        }.toList()

        val viewBox = VIEW_BOX.find(svg)?.groupValues?.get(1)
            ?.split(' ')
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 4 }

        return StrokeDiagram(
            viewBoxWidth = viewBox?.get(2) ?: DEFAULT_VIEW_BOX,
            viewBoxHeight = viewBox?.get(3) ?: DEFAULT_VIEW_BOX,
            strokes = strokes,
            numbers = numbers,
        )
    }
}
