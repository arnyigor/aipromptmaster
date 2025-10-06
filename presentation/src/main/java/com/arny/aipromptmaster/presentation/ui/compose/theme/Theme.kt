package com.arny.aipromptmaster.presentation.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Цвета для светлой темы
private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4FD),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF705575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD7FC),
    onTertiaryContainer = Color(0xFF28132E),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFEFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    surfaceContainerHigh = Color(0xFFE8EDF4),
    surfaceContainer = Color(0xFFEEF2F9),
    surfaceContainerLow = Color(0xFFF3F7FE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    inverseSurface = Color(0xFF2E3033),
    inverseOnSurface = Color(0xFFF0F0F4),
    inversePrimary = Color(0xFFA4C8FF),
    surfaceTint = Color(0xFF1976D2),
    scrim = Color(0xFF000000),
)

// Цвета для темной темы
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA4C8FF),
    onPrimary = Color(0xFF001B3E),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E4FD),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDFBCDF),
    onTertiary = Color(0xFF3E2844),
    tertiaryContainer = Color(0xFF563E5C),
    onTertiaryContainer = Color(0xFFFBD7FC),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerHighest = Color(0xFF31373C),
    surfaceContainerHigh = Color(0xFF272C31),
    surfaceContainer = Color(0xFF1D2226),
    surfaceContainerLow = Color(0xFF1A1C1E),
    surfaceContainerLowest = Color(0xFF0E1013),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF1A1C1E),
    inversePrimary = Color(0xFF1976D2),
    surfaceTint = Color(0xFFA4C8FF),
    scrim = Color(0xFF000000),
)

// Дополнительные цвета приложения
data class ExtendedColors(
    val success: Color = Color(0xFF4CAF50),
    val warning: Color = Color(0xFFFF9800),
    val chipBackgroundNormal: Color = Color(0xFFE8DEF8),
    val chipBackgroundSelected: Color = Color(0xFF6750A4),
    val chipTextNormal: Color = Color(0xFF1D192B),
    val chipTextSelected: Color = Color(0xFFFFFFFF),
    val filterCardBackgroundActive: Color = Color(0xFFEADDFF),
    val filterCardBackgroundInactive: Color = Color(0xFFF5F5F5),
    val filterCardStrokeActive: Color = Color(0xFF6750A4),
    val filterCardStrokeInactive: Color = Color(0xFFE0E0E0),
)

val LocalExtendedColors = compositionLocalOf { ExtendedColors() }

// Тайпография
object AppTypography {
    // Можно настроить дополнительные текстовые стили здесь
}

// Формы
object AppShapes {
    // Можно настроить формы компонентов здесь
}

@Composable
fun AIPromptMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extendedColors = ExtendedColors()

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(), // Используем дефолтную типографику Material 3
        shapes = androidx.compose.material3.Shapes(), // Используем дефолтные формы Material 3
        content = {
            CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
                content()
            }
        }
    )
}