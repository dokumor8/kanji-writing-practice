package com.example.kanjipractice.domain.recognition

import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeNormalizerTest {

    private val size = StrokeNormalizer.DEFAULT_TARGET_SIZE

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertTrue(StrokeNormalizer.normalize(emptyList()).isEmpty())
        // A pen-down with no samples carries no ink, so it is dropped rather
        // than passed to the recogniser as an empty stroke.
        assertTrue(StrokeNormalizer.normalize(listOf(Stroke(emptyList()))).isEmpty())
    }

    @Test
    fun aWideDrawingIsCentredInsideTheTargetBox() {
        val strokes = listOf(Stroke(listOf(StrokePoint(0f, 400f), StrokePoint(1000f, 600f))))
        val normalized = StrokeNormalizer.normalize(strokes).single().points

        for (point in normalized) {
            assertTrue(point.x in 0f..size, "x out of box: ${point.x}")
            assertTrue(point.y in 0f..size, "y out of box: ${point.y}")
        }
        // Uniform scaling, so the drawing stays centred vertically.
        val top = normalized.minOf { it.y }
        val bottom = normalized.maxOf { it.y }
        assertTrue(abs((top + bottom) / 2f - size / 2f) < 0.01f, "not vertically centred")
    }

    @Test
    fun aspectRatioIsPreserved() {
        // A square drawing must stay square; stretching 一 into a box would feed
        // the recogniser a shape the user never drew.
        val strokes = listOf(
            Stroke(listOf(StrokePoint(100f, 100f), StrokePoint(300f, 300f))),
        )
        val points = StrokeNormalizer.normalize(strokes).single().points
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        assertTrue(abs(width - height) < 0.01f, "width $width != height $height")
    }

    @Test
    fun aHorizontalLineUsesTheHorizontalSpanForScale() {
        val strokes = listOf(Stroke(listOf(StrokePoint(0f, 50f), StrokePoint(400f, 50f))))
        val points = StrokeNormalizer.normalize(strokes).single().points
        val width = points.maxOf { it.x } - points.minOf { it.x }
        assertEquals(size * 0.9f, width, 0.01f)
    }

    @Test
    fun aSinglePointDoesNotDivideByZero() {
        val points = StrokeNormalizer.normalize(
            listOf(Stroke(listOf(StrokePoint(7f, 9f))))
        ).single().points
        assertEquals(1, points.size)
        assertTrue(points[0].x.isFinite() && points[0].y.isFinite())
        assertTrue(points[0].x in 0f..size && points[0].y in 0f..size)
    }

    @Test
    fun strokeStructureIsPreserved() {
        val strokes = listOf(
            Stroke(listOf(StrokePoint(0f, 0f), StrokePoint(10f, 10f))),
            Stroke(listOf(StrokePoint(50f, 50f))),
        )
        val normalized = StrokeNormalizer.normalize(strokes)
        assertEquals(2, normalized.size)
        assertEquals(2, normalized[0].points.size)
        assertEquals(1, normalized[1].points.size)
    }

    @Test
    fun normalizationIsIndependentOfCanvasSize() {
        // The same drawing at two canvas scales must reach ML Kit identically.
        val small = listOf(Stroke(listOf(StrokePoint(10f, 10f), StrokePoint(90f, 60f))))
        val large = listOf(Stroke(listOf(StrokePoint(100f, 100f), StrokePoint(900f, 600f))))

        val a = StrokeNormalizer.normalize(small).single().points
        val b = StrokeNormalizer.normalize(large).single().points
        for (i in a.indices) {
            assertEquals(a[i].x, b[i].x, 0.01f)
            assertEquals(a[i].y, b[i].y, 0.01f)
        }
    }
}
