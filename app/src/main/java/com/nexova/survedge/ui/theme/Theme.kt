package com.nexova.survedge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFFF682C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDD0),
    onPrimaryContainer = Color(0xFF6B2410),

    secondary = Color(0xFF725B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDD0),
    onSecondaryContainer = Color(0xFF2A1810),

    tertiary = Color(0xFF595D52),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDE1D5),
    onTertiaryContainer = Color(0xFF1B1F16),

    error = Color(0xFFED3241),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),

    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF000000),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF0EFEB),
    onSurfaceVariant = Color(0xFF666666),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF682C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6B2410),
    onPrimaryContainer = Color(0xFFFFDDD0),

    secondary = Color(0xFFE0BFAA),
    onSecondary = Color(0xFF41301F),
    secondaryContainer = Color(0xFF5A4530),
    onSecondaryContainer = Color(0xFFFFDDD0),

    tertiary = Color(0xFFC0C7B8),
    onTertiary = Color(0xFF31352C),
    tertiaryContainer = Color(0xFF474B42),
    onTertiaryContainer = Color(0xFFDDE1D5),

    error = Color(0xFFED3241),
    onError = Color(0xFF410E0B),
    errorContainer = Color(0xFF690D09),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF1E1E1E),
    onBackground = Color(0xFFFFFFFF),

    surface = Color(0xFF2C2C2C),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF534632),
    onSurfaceVariant = Color(0xFFD8D8D8),
)

@Composable
fun SurvedgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = SurvedgeTypography(),
        content = content
    )
}
