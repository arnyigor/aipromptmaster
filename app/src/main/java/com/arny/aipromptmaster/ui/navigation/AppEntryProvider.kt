package com.arny.aipromptmaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.ui.models.ModelsScreen
import com.arny.aipromptmaster.ui.models.ModelsViewModel
import com.arny.aipromptmaster.ui.screens.chat.ChatRoute
import com.arny.aipromptmaster.ui.screens.chat.ChatViewModel
import com.arny.aipromptmaster.ui.screens.chathistory.ChatHistoryRoute
import com.arny.aipromptmaster.ui.screens.chathistory.ChatHistoryViewModel
import com.arny.aipromptmaster.ui.screens.edit.PromptEditScreen
import com.arny.aipromptmaster.ui.screens.edit.PromptEditViewModel
import com.arny.aipromptmaster.ui.screens.prompts.PromptListScreen
import com.arny.aipromptmaster.ui.screens.prompts.PromptListViewModel
import com.arny.aipromptmaster.ui.screens.settings.SettingsScreen
import com.arny.aipromptmaster.ui.screens.settings.SettingsViewModel
import com.arny.aipromptmaster.ui.screens.systemprompt.SystemPromptPickerScreen
import com.arny.aipromptmaster.ui.screens.systemprompt.SystemPromptViewModel
import com.arny.aipromptmaster.ui.screens.view.PromptDetailsScreen
import com.arny.aipromptmaster.ui.screens.view.PromptViewViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

@Composable
fun rememberAppEntryProvider(
    onNavigateToDetails: (String) -> Unit,
    onChatClicked: (String?) -> Unit,
    onSystemClick: (String) -> Unit,
    onModelsClick: () -> Unit,
    navToPrompts: (navScreen: String) -> Unit,
    navigateToEdit: (String?) -> Unit,
    onBack: () -> Unit,
) = entryProvider {

    // ============ HOME / PROMPTS ============
    entry<PromptsKey> { key ->
        val viewModel = koinViewModel<PromptListViewModel>(
            key = key.navScreen
        ) {
            parametersOf(key.navScreen)
        }
        PromptListScreen(
            viewModel = viewModel,
            onNavigateToDetails = onNavigateToDetails,
            onNavigateToEdit = navigateToEdit,
        )
    }

    entry<PromptDetailKey> { key ->
        val viewModel = koinViewModel<PromptViewViewModel>(
            // ВАЖНО: Указываем ключ, уникальный для этого контента.
            // Это заставит ViewModelStore создать НОВУЮ ViewModel для каждого promptId,
            // даже если экран технически тот же.
            key = key.promptId
        ) {
            parametersOf(key.promptId)
        }

        PromptDetailsScreen(
            viewModel = viewModel,
            onBack = onBack,
            navigateToEdit = navigateToEdit
        )
    }

    // ============ CHATS ============

    entry<ChatHistoryKey> {
        ChatHistoryRoute(
            viewModel = koinViewModel<ChatHistoryViewModel>(),
            onChatClicked = onChatClicked,
        )
    }

    entry<ChatDetailKey> { key ->
        val viewModel = koinViewModel<ChatViewModel>(
            key = key.chatId
        ) {
            parametersOf(key.chatId)
        }
        ChatRoute(
            viewModel = viewModel,
            onModelsClick = onModelsClick,
            onSystemClick = onSystemClick,
        )
    }

    entry<SystemPromptKey> { key ->
        val viewModel = koinViewModel<SystemPromptViewModel>(
            key = key.chatId
        ) {
            parametersOf(key.chatId)
        }
        SystemPromptPickerScreen(
            viewModel = viewModel,
            navToPrompts = {
                navToPrompts(AppConstants.SYSTEM_PROMPT_SCREEN_KEY)
            },
        )
    }

    entry<ModelsKey> {
        val viewModel = koinViewModel<ModelsViewModel>()
        ModelsScreen(
            viewModel = viewModel,
        )
    }

// ============ SETTINGS ============
    entry<SettingsKey> {
        SettingsScreen(
            viewModel = koinViewModel<SettingsViewModel>(),
        )
    }

    // ============ EDIT PROMPT ============
    entry<PromptEditKey> { key ->
        // 1️⃣ Log entry into the route with the raw key
        Timber.tag("NavGraph")
            .d("Navigating to PromptEditScreen, key=%s", key)

        // 2️⃣ Extract promptId from the key for debugging
        val promptId = key.promptId
        Timber.d("PromptEditKey contains promptId=%s", promptId)

        // 3️⃣ Resolve ViewModel via Koin and log success
        val viewModel: PromptEditViewModel =
            koinViewModel<PromptEditViewModel>(key = promptId) {
                parametersOf(promptId)
            }.also { vm ->
                Timber.d(
                    "Resolved %s for promptId=%s",
                    vm::class.simpleName, promptId
                )
            }

        PromptEditScreen(
            viewModel = viewModel,
            onBack = onBack
        )
    }
}