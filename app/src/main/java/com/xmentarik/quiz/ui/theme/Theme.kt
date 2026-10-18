package com.xmentarik.quiz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object QuizColors {
    val NightBlue = Color(0xFF0A0E33)
    val DeepPurple = Color(0xFF1B1464)
    val Gold = Color(0xFFFFC107)
    val GoldLight = Color(0xFFFFE082)
    val OptionBackground = Color(0xFF13185A)
    val OptionBorder = Color(0xFF5C6BC0)
    val Selected = Color(0xFFFF9800)
    val Correct = Color(0xFF2E7D32)
    val Wrong = Color(0xFFC62828)
    val TextSecondary = Color(0xCCFFFFFF)

    val Background = Brush.verticalGradient(listOf(DeepPurple, NightBlue))
}

private val QuizColorScheme = darkColorScheme(
    primary = QuizColors.Gold,
    onPrimary = QuizColors.NightBlue,
    secondary = QuizColors.GoldLight,
    onSecondary = QuizColors.NightBlue,
    background = QuizColors.NightBlue,
    onBackground = Color.White,
    surface = QuizColors.DeepPurple,
    onSurface = Color.White,
    surfaceVariant = QuizColors.OptionBackground,
    onSurfaceVariant = Color.White,
    outline = QuizColors.OptionBorder,
)

@Composable
fun QuizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QuizColorScheme, content = content)
}
