package com.arny.aiprompts.presentation.ui.llm.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ==================== Цвета сообщений ====================

@Composable
fun ColorScheme.userMessageBackground(): Color {
    return primaryContainer.copy(alpha = 0.9f)
}

@Composable
fun ColorScheme.aiMessageBackground(): Color {
    return surfaceVariant.copy(alpha = 0.5f)
}

@Composable
fun ColorScheme.systemMessageBackground(): Color {
    return secondaryContainer.copy(alpha = 0.5f)
}

// ==================== Аватар AI ====================

@Composable
fun ColorScheme.aiAvatarBackground(): Color {
    return primary.copy(alpha = 0.9f)
}

@Composable
fun ColorScheme.aiAvatarIcon(): Color {
    return onPrimary
}

// ==================== Code Blocks ====================

@Composable
fun ColorScheme.codeBlockBackground(): Color {
    return if (isLight) {
        Color(0xFFF5F5F5) // Светло-серый для светлой темы
    } else {
        Color(0xFF1E1E1E) // Темно-серый как в VS Code для темной темы
    }
}

@Composable
fun ColorScheme.codeBlockText(): Color {
    return if (isLight) {
        Color(0xFF333333)
    } else {
        Color(0xFFD4D4D4) // Как в VS Code
    }
}

// ==================== Параметры (Jan/LM Studio стиль) ====================

@Composable
fun ColorScheme.parameterPanelBackground(): Color {
    return surface.copy(alpha = 0.95f)
}

@Composable
fun ColorScheme.parameterLabelColor(): Color {
    return onSurfaceVariant
}

@Composable
fun ColorScheme.parameterValueColor(): Color {
    return primary
}

@Composable
fun ColorScheme.statLabelColor(): Color {
    return onSurfaceVariant.copy(alpha = 0.7f)
}

@Composable
fun ColorScheme.statValueColor(): Color {
    return onSurface
}

// ==================== Sidebar ====================

@Composable
fun ColorScheme.sidebarBackground(): Color {
    return surface.copy(alpha = 0.98f)
}

@Composable
fun ColorScheme.sidebarSelectedItem(): Color {
    return primaryContainer.copy(alpha = 0.5f)
}

@Composable
fun ColorScheme.sidebarItemHover(): Color {
    return surfaceVariant.copy(alpha = 0.5f)
}

// ==================== Input Panel ====================

@Composable
fun ColorScheme.inputPanelBackground(): Color {
    return surface
}

@Composable
fun ColorScheme.inputFieldBackground(): Color {
    return surfaceVariant.copy(alpha = 0.5f)
}

// ==================== Статусы ====================

@Composable
fun ColorScheme.streamingIndicator(): Color {
    return primary
}

@Composable
fun ColorScheme.errorIndicator(): Color {
    return error
}

// ==================== Helpers ====================

private val ColorScheme.isLight: Boolean
    get() = this.background.luminance() > 0.5f

private fun Color.luminance(): Float {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return r + g + b
}
