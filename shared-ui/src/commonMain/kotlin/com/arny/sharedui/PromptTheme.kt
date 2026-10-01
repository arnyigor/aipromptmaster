package com.arny.sharedui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun PromptTheme(dark: Boolean = isSystemInDarkTheme(), colors: ColorScheme? = null, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors ?: if (dark) darkColorScheme(
            primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
            primaryContainer = Color(0xFF4A2E8F), secondaryContainer = Color(0xFF3C3045), tertiaryContainer = Color(0xFF9D6B7C)
        ) else lightColorScheme(
            primary = Color(0xFF6650A4), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260),
            primaryContainer = Color(0xFFB39DFF), secondaryContainer = Color(0xFFE1E0EB), tertiaryContainer = Color(0xFFF8C6CE)
        ),
        typography = Typography(), content = content
    )
}
