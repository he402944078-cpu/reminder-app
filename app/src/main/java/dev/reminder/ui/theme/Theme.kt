package dev.reminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00696D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF6EF),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF7A5900),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0A5),
    onSecondaryContainer = Color(0xFF261B00),
    surface = Color(0xFFF6FBFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE8E6),
    onSurfaceVariant = Color(0xFF3F4947),
    tertiary = Color(0xFF5C5C7A),
    tertiaryContainer = Color(0xFFE2DFFF),
    onTertiaryContainer = Color(0xFF181933)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF49BFB9),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF005052),
    onPrimaryContainer = Color(0xFF6FF6EF),
    secondary = Color(0xFFE7B95F),
    onSecondary = Color(0xFF402D00),
    secondaryContainer = Color(0xFF5C4200),
    onSecondaryContainer = Color(0xFFFFE0A5),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBFC8C6),
    tertiary = Color(0xFFC5C3EB),
    tertiaryContainer = Color(0xFF30314B),
    onTertiaryContainer = Color(0xFFE2DFFF)
)

@Composable
fun ReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
