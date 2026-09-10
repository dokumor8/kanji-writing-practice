package com.example.kanjipractice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.model.StrokePoint

/**
 * The handwriting surface (plan, section 5.1).
 *
 * Finished strokes are owned by the caller so they survive failed recognition
 * attempts and configuration changes; only the stroke currently under the finger
 * lives here. Gesture handling starts on touch-down rather than after a drag
 * slop, so a short stroke or a dot registers.
 */
@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    onStrokeFinished: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    strokeColor: Color = Color.Unspecified,
    strokeWidth: Dp = 5.dp,
    cornerRadius: Dp = 20.dp,
) {
    val inkColor = if (strokeColor == Color.Unspecified) {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    } else {
        strokeColor
    }
    val widthPx = with(LocalDensity.current) { strokeWidth.toPx() }

    var livePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val points = ArrayList<StrokePoint>()
                    points += StrokePoint(down.position.x, down.position.y)
                    livePoints = points.toList()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        points += StrokePoint(change.position.x, change.position.y)
                        livePoints = points.toList()
                        change.consume()
                    }

                    if (points.isNotEmpty()) {
                        onStrokeFinished(Stroke(points.toList()))
                    }
                    livePoints = emptyList()
                }
            }
    ) {
        strokes.forEach { drawInk(it.points, inkColor, widthPx) }
        drawInk(livePoints, inkColor, widthPx)
    }
}

/**
 * Draws a stroke as a smooth curve through its sample points, using the midpoint
 * of each pair as the on-curve anchor. Raw finger samples are noisy enough that
 * straight segments look visibly faceted.
 */
private fun DrawScope.drawInk(points: List<StrokePoint>, color: Color, widthPx: Float) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color, radius = widthPx / 2f, center = Offset(points[0].x, points[0].y))
        return
    }
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val midX = (previous.x + current.x) / 2f
        val midY = (previous.y + current.y) / 2f
        path.quadraticTo(previous.x, previous.y, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)

    drawPath(
        path = path,
        color = color,
        style = DrawStroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
