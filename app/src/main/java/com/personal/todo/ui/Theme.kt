package com.personal.todo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personal.todo.domain.Priority

private val LightColors = lightColorScheme(
    primary = Color(0xFF4458D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF), onPrimaryContainer = Color(0xFF0C1A66),
    background = Color(0xFFF5F6FA), onBackground = Color(0xFF1B1C22),
    surface = Color.White, onSurface = Color(0xFF1B1C22), onSurfaceVariant = Color(0xFF5A5D6B),
    surfaceContainerHigh = Color(0xFFECEEF6), outlineVariant = Color(0xFFE2E4EC),
    error = Color(0xFFD93B3B), errorContainer = Color(0xFFFFE1E0), onErrorContainer = Color(0xFF5A1010),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFB8FF), onPrimary = Color(0xFF0E1A6B),
    primaryContainer = Color(0xFF2B3AA8), onPrimaryContainer = Color(0xFFE0E3FF),
    background = Color(0xFF0F1015), onBackground = Color(0xFFE6E7EE),
    surface = Color(0xFF181A21), onSurface = Color(0xFFE6E7EE), onSurfaceVariant = Color(0xFF9EA1B0),
    surfaceContainerHigh = Color(0xFF222530), outlineVariant = Color(0xFF2A2D38),
    error = Color(0xFFFF8A8A), errorContainer = Color(0xFF5A1F1F), onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().let {
    it.copy(
        headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    )
}

@Composable
fun TodoTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

fun Priority.color(): Color = when (this) {
    Priority.LOW -> Color(0xFF8A94A6)
    Priority.MEDIUM -> Color(0xFF3B82F6)
    Priority.HIGH -> Color(0xFFF59E0B)
    Priority.URGENT -> Color(0xFFE5484D)
}
