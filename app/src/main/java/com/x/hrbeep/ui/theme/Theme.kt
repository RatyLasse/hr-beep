package com.x.hrbeep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Dark scheme — sleek near-black surface with cyan accents
private val DarkColors = darkColorScheme(
    primary             = Color(0xFF4DD0E1),  // cyan accent
    onPrimary           = Color(0xFF003549),
    primaryContainer    = Color(0xFF004D67),
    onPrimaryContainer  = Color(0xFFB3E5FC),
    surface             = Color(0xFF0A1018),  // very dark blue-black
    surfaceVariant      = Color(0xFF151E28),  // slightly lighter panel
    onSurface           = Color(0xFFE8EDF2),  // clean white-ish
    onSurfaceVariant    = Color(0xFF8A9BAA),  // muted label gray
    background          = Color(0xFF0A1018),
    onBackground        = Color(0xFFE8EDF2),
    error               = Color(0xFFEF5350),
)

// Light scheme — same hue family, readable on white
private val LightColors = lightColorScheme(
    primary             = Color(0xFF0277BD),
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0xFFB3E5FC),
    onPrimaryContainer  = Color(0xFF001E2B),
    surface             = Color(0xFFF4FAFE),
    surfaceVariant      = Color(0xFFDCEFF8),
    onSurface           = Color(0xFF0D1E2B),
    onSurfaceVariant    = Color(0xFF4A6572),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun HrBeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
