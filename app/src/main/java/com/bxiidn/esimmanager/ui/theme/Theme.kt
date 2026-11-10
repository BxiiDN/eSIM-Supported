package com.bxiidn.esimmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palet monokrom: hitam, abu-abu, putih.
val Ink = Color(0xFF070708)
val Graphite = Color(0xFF0F0F11)
val Slate = Color(0xFF17171A)
val Steel = Color(0xFF222226)
val Line = Color(0xFF2A2A2F)
val LineSoft = Color(0xFF1D1D21)
val Fog = Color(0xFF9A9AA1)
val Mist = Color(0xFFC9C9CE)
val Paper = Color(0xFFF4F4F5)

private val MonoScheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Steel,
    onPrimaryContainer = Paper,
    secondary = Mist,
    onSecondary = Ink,
    secondaryContainer = Slate,
    onSecondaryContainer = Paper,
    tertiary = Fog,
    onTertiary = Ink,
    tertiaryContainer = Slate,
    onTertiaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Graphite,
    onSurface = Paper,
    surfaceVariant = Slate,
    onSurfaceVariant = Fog,
    surfaceTint = Paper,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    inversePrimary = Ink,
    outline = Line,
    outlineVariant = LineSoft,
    error = Mist,
    onError = Ink,
    errorContainer = Steel,
    onErrorContainer = Paper,
    scrim = Color(0xCC000000),
    surfaceBright = Steel,
    surfaceDim = Ink,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Graphite,
    surfaceContainer = Graphite,
    surfaceContainerHigh = Slate,
    surfaceContainerHighest = Steel,
)

private val Base = Typography()

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(lineHeight = 21.sp),
    bodySmall = Base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.9.sp),
    labelSmall = Base.labelSmall.copy(letterSpacing = 0.8.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EsimManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonoScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
