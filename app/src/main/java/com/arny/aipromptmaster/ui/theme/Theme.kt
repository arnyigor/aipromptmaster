package com.arny.aipromptmaster.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Пакет markdownColors.kt – одна точка входа для всех цветов Markdown.
 */
object MarkdownColorPalette {
    /**
     * Цвет фона блока кода (текст + inline‑код)
     */
    val CodeBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant

    val CodeBackgroundInvert: Color
        @Composable get() = MaterialTheme.colorScheme.secondary

    /**
     * Текст внутри блока кода – всегда контрастный с CodeBackground.
     */
    val CodeText: Color
        @Composable get() =
            contentColorFor(MaterialTheme.colorScheme.surfaceVariant)

    /**
     * Цвет текста внутри блока кода для пользовательских сообщений.
     * Используется dark‑on‑light контраст, чтобы текст был читаемым на светлом фоне.
     */
    val CodeUserText: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimary

    /**
     * Цвет ссылок (h1‑h6, inline‑ссылки и т.п.).
     */
    val LinkText: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    /**
     * Текст в обычном Markdown‑тексте.
     */
    val MarkdownText: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface

    /**
     * Текст в обычном Markdown‑тексте.
     */
    val MarkdownTextInvert: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimary

    /**
     * Цвет разделителя (подсветка границ кода и таблиц).
     */
    val DividerColor: Color
        @Composable get() = MaterialTheme.colorScheme.outline
}

private val DarkColorScheme = darkColorScheme(
    primary          = Purple80,
    secondary        = PurpleGrey80,
    tertiary         = Pink80,

    // ── явно задаём контейнеры, чтобы они отличались от primary
    primaryContainer   = Color(0xFF4A2E8F),
    secondaryContainer  = Color(0xFF3C3045),
    tertiaryContainer   = Color(0xFF9D6B7C)
)

private val LightColorScheme = lightColorScheme(
    primary          = Purple40,
    secondary        = PurpleGrey40,
    tertiary         = Pink40,

    primaryContainer   = Color(0xFFB39DFF),
    secondaryContainer  = Color(0xFFE1E0EB),
    tertiaryContainer   = Color(0xFFF8C6CE)
)


@Composable
fun AIPromptMasterComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}