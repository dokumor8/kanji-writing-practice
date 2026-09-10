package com.example.kanjipractice.domain.stroke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KanjiVgParserTest {

    @Test
    fun readsViewBoxStrokesAndStrokeNumbers() {
        val diagram = KanjiVgParser.parse(SAMPLE)

        assertEquals(109f, diagram.viewBoxWidth)
        assertEquals(109f, diagram.viewBoxHeight)
        assertEquals(2, diagram.strokeCount)
        assertEquals(2, diagram.numbers.size)
        assertEquals("1", diagram.numbers[0].label)
        assertEquals(43.5f, diagram.numbers[0].x)
        assertEquals(12.5f, diagram.numbers[0].y)
        assertEquals("2", diagram.numbers[1].label)
    }

    @Test
    fun theIdAttributeIsNotMistakenForPathData() {
        // Every KanjiVG path carries id="kvg:..." next to d="...".
        val first = KanjiVgParser.parse(SAMPLE).strokes.first()
        assertEquals(SvgPathCommand.MoveTo(84.04f, 44.88f), first.first())
        assertTrue(first.size > 1)
    }

    @Test
    fun strokeOrderFollowsDocumentOrder() {
        val strokes = KanjiVgParser.parse(SAMPLE).strokes
        val firstStart = strokes[0].first() as SvgPathCommand.MoveTo
        val secondStart = strokes[1].first() as SvgPathCommand.MoveTo
        assertEquals(84.04f, firstStart.x)
        assertEquals(20f, secondStart.x)
    }

    @Test
    fun aDiagramWithoutStrokeNumbersStillParses() {
        val svg = "<svg viewBox=\"0 0 109 109\"><g><path d=\"M0 0 L1 1\"/></g></svg>"
        val diagram = KanjiVgParser.parse(svg)
        assertEquals(1, diagram.strokeCount)
        assertTrue(diagram.numbers.isEmpty())
    }

    @Test
    fun aDiagramWithNoStrokesIsEmptyButValid() {
        val diagram = KanjiVgParser.parse("<svg viewBox=\"0 0 109 109\"></svg>")
        assertEquals(0, diagram.strokeCount)
    }

    private companion object {
        val SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 109 109">
            <g id="kvg:StrokePaths_06b6f" style="fill:none;stroke:#000000">
              <g id="kvg:06b6f" kvg:element="&#x6b6f;">
                <path id="kvg:06b6f-s1" kvg:type="&#x2f0;" d="M84.04,44.88c1.17,1.17,1.57,2.62,1.57,4.25"/>
                <path id="kvg:06b6f-s2" kvg:type="&#x2f0;" d="M20,20 C21,21 22,22 23,23"/>
              </g>
            </g>
            <g id="kvg:StrokeNumbers_06b6f" style="font-size:8;fill:#808080">
              <text transform="matrix(1 0 0 1 43.50 12.50)">1</text>
              <text transform="matrix(1 0 0 1 59.50 21.50)">2</text>
            </g>
            </svg>
        """.trimIndent()
    }
}
