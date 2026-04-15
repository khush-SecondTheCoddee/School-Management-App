package com.schoolmanagement.app.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColorTokens = lightColorScheme(
    primary = Color(0xFF155EEF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001A4D),
    secondary = Color(0xFF006E1C),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9E3F12),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF101323),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D2E),
    surfaceVariant = Color(0xFFE3E7F4),
    onSurfaceVariant = Color(0xFF43485A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val DarkColorTokens = darkColorScheme(
    primary = Color(0xFFB6C7FF),
    onPrimary = Color(0xFF002A75),
    secondary = Color(0xFF7BDB8E),
    onSecondary = Color(0xFF00390B),
    tertiary = Color(0xFFFFB691),
    onTertiary = Color(0xFF5C1A00),
    background = Color(0xFF101323),
    onBackground = Color(0xFFE3E7F4),
    surface = Color(0xFF1A1D2E),
    onSurface = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFF43485A),
    onSurfaceVariant = Color(0xFFC4C8D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val SchoolTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
)

object SchoolShapes {
    val card = RoundedCornerShape(20.dp)
    val input = RoundedCornerShape(16.dp)
}

@Composable
fun SchoolTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorTokens else LightColorTokens,
        typography = SchoolTypography,
        content = content
    )
}
