package com.example.kanjipractice.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Ink on paper. The palette is deliberately low-chroma so the only saturated
// things on screen are the user's ink and the correctness feedback.
private val InkBlue = Color(0xFF3B5B92)
private val InkBlueLight = Color(0xFFAEC6F5)
private val Sumi = Color(0xFF1B1B1F)
private val Paper = Color(0xFFFDFBF7)
private val PaperDim = Color(0xFFF2EEE6)
private val SealedRed = Color(0xFFB3261E)
private val SealedRedLight = Color(0xFFF2B8B5)
private val MatchaGreen = Color(0xFF3F6B4A)
private val MatchaGreenLight = Color(0xFFA6D3B0)

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = MatchaGreen,
    onSecondary = Color.White,
    error = SealedRed,
    onError = Color.White,
    background = Paper,
    onBackground = Sumi,
    surface = Paper,
    onSurface = Sumi,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
)

private val DarkColors = darkColorScheme(
    primary = InkBlueLight,
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF1F4480),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = MatchaGreenLight,
    onSecondary = Color(0xFF00391B),
    error = SealedRedLight,
    onError = Color(0xFF601410),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF2A2B30),
    onSurfaceVariant = Color(0xFFC6C6D0),
    outline = Color(0xFF90909A),
)

@Composable
fun KanjiPracticeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Only the icon tint is set here: the bar colour itself follows the
            // platform theme, which is what keeps this working on every OEM skin.
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
