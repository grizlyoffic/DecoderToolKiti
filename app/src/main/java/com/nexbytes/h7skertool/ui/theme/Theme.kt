package com.nexbytes.h7skertool.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color.Black,
    primaryContainer = NeonGreenDim,
    onPrimaryContainer = Color.Black,
    secondary = ElectricBlue,
    onSecondary = Color.Black,
    tertiary = Amber,
    background = DeepBlack,
    onBackground = TextPrimary,
    surface = CardBlack,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedBlack,
    onSurfaceVariant = TextSecondary,
    error = AlertRed,
    onError = Color.White,
    outline = DividerGray
)

@Composable
fun H7skERTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = Typography, content = content)
}
