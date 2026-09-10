package com.example.kanjipractice.domain.stroke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SvgPathParserTest {

    @Test
    fun absoluteMoveAndLine() {
        val commands = SvgPathParser.parse("M10 20 L30 40")
        assertEquals(
            listOf(SvgPathCommand.MoveTo(10f, 20f), SvgPathCommand.LineTo(30f, 40f)),
            commands,
        )
    }

    @Test
    fun extraCoordinatePairsAfterMoveBecomeLineTos() {
        // "M 1 2 3 4" is M followed by an implicit L, per the SVG grammar.
        val commands = SvgPathParser.parse("M1 2 3 4")
        assertEquals(
            listOf(SvgPathCommand.MoveTo(1f, 2f), SvgPathCommand.LineTo(3f, 4f)),
            commands,
        )
    }

    @Test
    fun relativeCommandsAccumulateFromTheCurrentPoint() {
        val commands = SvgPathParser.parse("m10 10 l5 0 l0 5")
        assertEquals(
            listOf(
                SvgPathCommand.MoveTo(10f, 10f),
                SvgPathCommand.LineTo(15f, 10f),
                SvgPathCommand.LineTo(15f, 15f),
            ),
            commands,
        )
    }

    @Test
    fun numbersMayBeSeparatedOnlyByTheirSign() {
        assertEquals(
            listOf(SvgPathCommand.MoveTo(0f, 0f), SvgPathCommand.LineTo(-5.5f, 3f)),
            SvgPathParser.parse("M0,0L-5.5 3"),
        )
    }

    @Test
    fun cubicCurvesAreReadInFull() {
        val commands = SvgPathParser.parse("M0 0 C1 2 3 4 5 6")
        assertEquals(
            listOf(
                SvgPathCommand.MoveTo(0f, 0f),
                SvgPathCommand.CubicTo(1f, 2f, 3f, 4f, 5f, 6f),
            ),
            commands,
        )
    }

    @Test
    fun smoothCubicReflectsThePreviousControlPoint() {
        // "C 1 2 3 4 5 6" ends at (5,6) with its second control point at (3,4).
        // The S below writes 0 0 as its first control point precisely so that a
        // parser ignoring the reflection would be caught: the correct value is
        // 2*(5,6) - (3,4) = (7,8).
        val commands = SvgPathParser.parse("M0 0 C1 2 3 4 5 6 S0 0 9 10")
        val smooth = commands[2] as SvgPathCommand.CubicTo
        assertEquals(7f, smooth.x1)
        assertEquals(8f, smooth.y1)
        assertEquals(0f, smooth.x2)
        assertEquals(0f, smooth.y2)
        assertEquals(9f, smooth.x)
        assertEquals(10f, smooth.y)
    }

    @Test
    fun theFirstSmoothCubicFallsBackToTheCurrentPoint() {
        // Nothing to reflect yet, so the first control point is the current point.
        val commands = SvgPathParser.parse("M2 3 S4 5 6 7")
        val smooth = commands[1] as SvgPathCommand.CubicTo
        assertEquals(2f, smooth.x1)
        assertEquals(3f, smooth.y1)
    }

    @Test
    fun horizontalAndVerticalLinesWork() {
        assertEquals(
            listOf(
                SvgPathCommand.MoveTo(1f, 2f),
                SvgPathCommand.LineTo(9f, 2f),
                SvgPathCommand.LineTo(9f, 7f),
            ),
            SvgPathParser.parse("M1 2 H9 V7"),
        )
    }

    @Test
    fun closePathWorks() {
        val commands = SvgPathParser.parse("M0 0 L1 1 Z")
        assertEquals(SvgPathCommand.Close, commands.last())
    }

    @Test
    fun emptyDataProducesNoCommands() {
        assertTrue(SvgPathParser.parse("").isEmpty())
        assertTrue(SvgPathParser.parse("   ").isEmpty())
    }

    @Test
    fun anUnknownCommandIsRejectedRatherThanSilentlyIgnored() {
        // Silent skipping would turn a bad diagram into a plausible-looking one.
        assertFailsWith<IllegalArgumentException> { SvgPathParser.parse("M0 0 X5 5") }
    }

    @Test
    fun truncatedDataIsRejected() {
        assertFailsWith<IllegalArgumentException> { SvgPathParser.parse("M0 0 C1 2 3") }
    }

    @Test
    fun scientificNotationIsParsed() {
        val commands = SvgPathParser.parse("M1e1 2.5e-1")
        assertEquals(SvgPathCommand.MoveTo(10f, 0.25f), commands.single())
    }
}
