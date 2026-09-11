package com.example.kanjipractice.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.example.kanjipractice.domain.model.Stroke
import com.example.kanjipractice.domain.recognition.StrokeNormalizer

/**
 * Shows a drawing back to the user, read-only.
 *
 * The strokes were captured in the coordinates of whatever canvas they were drawn
 * on, which is not the size of this view, so they are re-fitted to it first --
 * the same normalisation the recogniser gets, which also means the preview shows
 * roughly what the recogniser saw.
 */
@Composable
fun DrawingPreview(
    strokes: List<Stroke>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val sidePx = with(density) { minOf(maxWidth, maxHeight).toPx() }
        val fitted = remember(strokes, sidePx) {
            if (strokes.isEmpty() || sidePx <= 0f) {
                emptyList()
            } else {
                StrokeNormalizer.normalize(strokes, sidePx)
            }
        }
        DrawingCanvas(
            strokes = fitted,
            onStrokeFinished = {},
            modifier = Modifier.fillMaxSize(),
            enabled = false,
            backgroundColor = backgroundColor,
        )
    }
}
