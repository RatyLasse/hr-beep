package com.x.hrbeep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark scheme — sky blue primary on deep blue-tinted surface
private val DarkColors = darkColorScheme(
    primary             = Color(0xFF81D4FA),  // light sky blue
    onPrimary           = Color(0xFF003549),
    primaryContainer    = Color(0xFF004D67),
    onPrimaryContainer  = Color(0xFFB3E5FC),
    surface             = Color(0xFF0D1E2B),  // deep blue-tinted near-black
    surfaceVariant      = Color(0xFF1C2E3A),
    onSurface           = Color(0xFFE3F2FD),  // blue-tinted near-white
    onSurfaceVariant    = Color(0xFF90A4AE),
)

// Light scheme — same hue family, readable on white
private val LightColors = lightColorScheme(
    primary             = Color(0xFF0277BD),  // readable dark sky blue
    onPrimary           = Color(0xFFFFFFFF),
    primaryContainer    = Color(0xFFB3E5FC),
    onPrimaryContainer  = Color(0xFF001E2B),
    surface             = Color(0xFFF4FAFE),  // faint blue-tinted white
    surfaceVariant      = Color(0xFFDCEFF8),
    onSurface           = Color(0xFF0D1E2B),  // same deep blue as dark surface, readable on light
    onSurfaceVariant    = Color(0xFF4A6572),
)

@Composable
fun HrBeepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
