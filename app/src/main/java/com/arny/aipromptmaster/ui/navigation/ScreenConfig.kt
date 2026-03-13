package com.arny.aipromptmaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.arny.aipromptmaster.R

/**
 * Конфигурация UI для конкретного экрана.
 */
data class ScreenConfig(
    val title: String,
    val showBackButton: Boolean = false,
    val showBottomBar: Boolean = true,
    val actions: List<AppBarAction> = emptyList()
)

/**
 * Функция-mapper: превращает NavKey в конфигурацию UI.
 * Здесь мы централизованно управляем заголовками и видимостью меню.
 */
@Composable
fun AppNavKey.toScreenConfig(): ScreenConfig {
    return when (this) {
        // --- Главные экраны (Bottom Bar виден) ---
        is PromptsKey -> {
            val hasNavScreen = navScreen.isNotBlank()
            ScreenConfig(
                title = stringResource(R.string.nav_home), // "Home"
                showBottomBar = !hasNavScreen,
                showBackButton = hasNavScreen
            )
        }

        is ChatHistoryKey -> ScreenConfig(
            title = stringResource(R.string.nav_history), // "Chats"
            showBottomBar = true,
            showBackButton = false
        )

        is SettingsKey -> ScreenConfig(
            title = stringResource(R.string.action_settings), // "Settings"
            showBottomBar = true,
            showBackButton = false
        )

        // --- Детальные экраны (Bottom Bar скрыт) ---
        is PromptDetailKey -> ScreenConfig(
            title = "Просмотр промпта",
            showBottomBar = false,
            showBackButton = true
        )

        is ChatDetailKey -> ScreenConfig(
            title = "Chat",
            showBottomBar = false,
            showBackButton = true
        )

        is ModelsKey -> ScreenConfig(
            title = "Модели",
            showBottomBar = true,
            showBackButton = false
        )

        is SystemPromptKey -> ScreenConfig(
            title = "Системный промпт",
            showBottomBar = false,
            showBackButton = true
        )

        is PromptEditKey -> ScreenConfig(
            title = "Редактирование промпта",
            showBottomBar = false,
            showBackButton = true
        )
    }
}