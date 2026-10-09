package com.arny.sharedui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PromptTheme(dark: Boolean = isSystemInDarkTheme(), colors: ColorScheme? = null, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors ?: if (dark) darkColorScheme(
            primary = Color(0xFFC3BFFF), onPrimary = Color(0xFF26205D),
            primaryContainer = Color(0xFF353071), onPrimaryContainer = Color(0xFFE6E3FF),
            secondary = Color(0xFFBBC6DA), secondaryContainer = Color(0xFF303B4E),
            tertiary = Color(0xFF83D6CD), tertiaryContainer = Color(0xFF004F49),
            background = Color(0xFF11131B), onBackground = Color(0xFFE4E6EF),
            surface = Color(0xFF11131B), onSurface = Color(0xFFE4E6EF),
            surfaceVariant = Color(0xFF303B4E), onSurfaceVariant = Color(0xFFC1C5D4), outline = Color(0xFF8B90A1), outlineVariant = Color(0xFF3D4253),
            surfaceContainerLowest = Color(0xFF0D0F16), surfaceContainerLow = Color(0xFF191C26),
            surfaceContainer = Color(0xFF202330), surfaceContainerHigh = Color(0xFF292D3A),
            surfaceContainerHighest = Color(0xFF343847)
        ) else lightColorScheme(
            primary = Color(0xFF4F46E5), onPrimary = Color.White,
            primaryContainer = Color(0xFFE5E7FF), onPrimaryContainer = Color(0xFF242050),
            secondary = Color(0xFF526175), secondaryContainer = Color(0xFFE9EDF5),
            tertiary = Color(0xFF006A65), tertiaryContainer = Color(0xFFD0F4EE),
            background = Color(0xFFF7F8FC), onBackground = Color(0xFF1A1C26),
            surface = Color(0xFFFBFCFF), onSurface = Color(0xFF1A1C26),
            surfaceVariant = Color(0xFFE9EDF5), onSurfaceVariant = Color(0xFF45495B), outline = Color(0xFF747B90), outlineVariant = Color(0xFFDDE1EB),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F3F9),
            surfaceContainer = Color(0xFFEAEDF5), surfaceContainerHigh = Color(0xFFE3E7F0),
            surfaceContainerHighest = Color(0xFFDDE2EC)
        ),
        typography = Typography().let { base -> base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        ) },
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}
