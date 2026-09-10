package com.example.kanjipractice.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kanjipractice.domain.stroke.StrokeDiagram
import com.example.kanjipractice.domain.stroke.SvgPathCommand

/**
 * Renders a KanjiVG stroke-order diagram (plan, section 5.4).
 *
 * Drawing happens in the SVG's own 109x109 coordinate space, scaled to whatever
 * box it is given, so the same composable serves both the post-success
 * self-check and the low-opacity tracing guide in RELEARN.
 *
 * When no diagram is bundled for the character it falls back to drawing the
 * character itself -- the "M6 can be stubbed" escape hatch from the plan
 * (section 11). The review flow must not depend on KanjiVG being complete.
 *
 * @param animate draws the strokes one at a time instead of all at once.
 * @param showNumbers labels each stroke with its order.
 */
@Composable
fun StrokeOrderView(
    diagram: StrokeDiagram,
    character: String,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    showNumbers: Boolean = false,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    guideColor: Color = MaterialTheme.colorScheme.outline,
    strokeWidth: Dp = 4.dp,
    showGuideBox: Boolean = true,
) {
    val paths = remember(diagram) { diagram.strokes.map { it.toComposePath() } }
    val textMeasurer = rememberTextMeasurer()
    val strokeCount = paths.size
    val strokeWidthPx = with(LocalDensity.current) { strokeWidth.toPx() }

    // 0 .. strokeCount: how many strokes have been drawn, fractionally, so the
    // stroke in flight can be drawn partially.
    val progress = remember(diagram) { Animatable(strokeCount.toFloat()) }

    LaunchedEffect(diagram, animate) {
        if (!animate || strokeCount == 0) {
            progress.snapTo(strokeCount.toFloat())
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = strokeCount.toFloat(),
            animationSpec = tween(
                durationMillis = STROKE_DURATION_MILLIS * strokeCount,
                easing = LinearEasing,
            ),
        )
    }

    Canvas(modifier) {
        if (strokeCount == 0) {
            drawFallbackCharacter(textMeasurer, character, strokeColor)
            return@Canvas
        }

        val viewBox = maxOf(diagram.viewBoxWidth, diagram.viewBoxHeight)
        val scale = size.minDimension / viewBox
        val offsetX = (size.width - diagram.viewBoxWidth * scale) / 2f
        val offsetY = (size.height - diagram.viewBoxHeight * scale) / 2f

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            if (showGuideBox) {
                drawRect(
                    color = guideColor.copy(alpha = GUIDE_ALPHA),
                    topLeft = Offset.Zero,
                    size = Size(diagram.viewBoxWidth, diagram.viewBoxHeight),
                    style = DrawStroke(width = 1f / scale),
                )
            }
            val strokeFraction = progress.value
            paths.forEachIndexed { index, path ->
                val fraction = (strokeFraction - index).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    drawStrokePath(path, fraction, strokeColor, strokeWidthPx / scale)
                }
            }
        }

        if (showNumbers && diagram.numbers.isNotEmpty()) {
            val style = TextStyle(color = guideColor, fontSize = 10.sp)
            for (number in diagram.numbers) {
                val layout = textMeasurer.measure(number.label, style)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        offsetX + number.x * scale - layout.size.width / 2f,
                        offsetY + number.y * scale - layout.size.height / 2f,
                    ),
                )
            }
        }
    }
}

/**
 * Draws [fraction] of a stroke. Fully drawn strokes take the cheap path; only
 * the stroke currently being animated needs a PathMeasure.
 */
private fun DrawScope.drawStrokePath(
    path: Path,
    fraction: Float,
    color: Color,
    width: Float,
) {
    if (fraction >= 1f) {
        drawPath(path, color, style = inkStroke(width))
        return
    }
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    if (length <= 0f) return
    val partial = Path()
    measure.getSegment(0f, length * fraction, partial, true)
    drawPath(partial, color, style = inkStroke(width))
}

private fun inkStroke(width: Float) =
    DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Shown when KanjiVG has no entry for the character. */
private fun DrawScope.drawFallbackCharacter(
    textMeasurer: TextMeasurer,
    character: String,
    color: Color,
) {
    if (character.isEmpty()) return
    // DrawScope implements Density, so this converts px -> sp for the measurer.
    val layout = textMeasurer.measure(
        text = character,
        style = TextStyle(color = color, fontSize = (size.minDimension * 0.7f).toSp()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            (size.width - layout.size.width) / 2f,
            (size.height - layout.size.height) / 2f,
        ),
    )
}

private fun List<SvgPathCommand>.toComposePath(): Path {
    val path = Path()
    for (command in this) {
        when (command) {
            is SvgPathCommand.MoveTo -> path.moveTo(command.x, command.y)
            is SvgPathCommand.LineTo -> path.lineTo(command.x, command.y)
            is SvgPathCommand.CubicTo -> path.cubicTo(
                command.x1, command.y1,
                command.x2, command.y2,
                command.x, command.y,
            )
            is SvgPathCommand.QuadTo -> path.quadraticTo(
                command.x1, command.y1, command.x, command.y,
            )
            SvgPathCommand.Close -> path.close()
        }
    }
    return path
}

private const val STROKE_DURATION_MILLIS = 220
private const val GUIDE_ALPHA = 0.3f
