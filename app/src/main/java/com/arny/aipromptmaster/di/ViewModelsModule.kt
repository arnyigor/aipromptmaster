package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.ui.models.ModelsViewModel
import com.arny.aipromptmaster.ui.screens.chat.ChatViewModel
import com.arny.aipromptmaster.ui.screens.chathistory.ChatHistoryViewModel
import com.arny.aipromptmaster.ui.screens.edit.PromptEditViewModel
import com.arny.aipromptmaster.ui.screens.prompts.PromptListViewModel
import com.arny.aipromptmaster.ui.screens.settings.SettingsViewModel
import com.arny.aipromptmaster.ui.screens.systemprompt.SystemPromptViewModel
import com.arny.aipromptmaster.ui.screens.view.PromptViewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { (navScreen: String) ->
        PromptListViewModel(
            navScreen = navScreen,
            interactor = get(),
            promptSynchronizer = get(),
        )
    }
    viewModel { (promptId: String) ->
        PromptViewViewModel(promptId = promptId, interactor = get())
    }
    viewModel { (conversationId: String?) ->
        ChatViewModel(
            conversationId = conversationId,
            shareService = get(),
            fileRepository = get(),
            interactor = get(),
            modelRepository = get()
        )
    }
    viewModel { (conversationId: String) ->
        SystemPromptViewModel(
            interactor = get(),
            conversationId = conversationId,
        )
    }
    viewModel { ChatHistoryViewModel(interactor = get()) }
    viewModel { ModelsViewModel(repository = get(), settingsRepository = get()) }
    viewModel { SettingsViewModel(interactor = get()) }
    viewModel { (promptId: String?) ->
        PromptEditViewModel(
            promptId = promptId,        // 1. Приходит из parametersOf
            interactor = get()          // 3. Обычная зависимость
        )
    }
}
