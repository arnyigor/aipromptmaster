package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.usecase.GetPromptUseCase
import com.arny.aiprompts.presentation.ui.detail.PromptDetailState
import com.arny.aiprompts.presentation.ui.detail.PromptLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Добавим события для управления редактированием
sealed interface PromptDetailEvent {
    object BackClicked : PromptDetailEvent
    object FavoriteClicked : PromptDetailEvent
    object Refresh : PromptDetailEvent
    object EditClicked : PromptDetailEvent
    object CancelClicked : PromptDetailEvent
    object SaveClicked : PromptDetailEvent
    data class TitleChanged(val newTitle: String) : PromptDetailEvent
    data class ContentChanged(val lang: PromptLanguage, val newContent: String) : PromptDetailEvent
}

interface PromptDetailComponent {
    val state: StateFlow<PromptDetailState>
    // Заменим отдельные функции на единую точку входа для событий
    fun onEvent(event: PromptDetailEvent)

    // Можно добавить навигационные выходы
    // sealed class Output { data class NavigateToChat(val promptId: String): Output }
    // val navigation: Flow<Output>
}

class DefaultPromptDetailComponent(
    componentContext: ComponentContext,
    private val getPromptUseCase: GetPromptUseCase,
    private val promptId: String,
    private val onNavigateBack: () -> Unit,
) : PromptDetailComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(PromptDetailState(isLoading = true))
    override val state: StateFlow<PromptDetailState> = _state.asStateFlow()

    private val scope = coroutineScope()

    init {
        scope.launch {
            loadPromptDetails()
        }
    }

    override fun onEvent(event: PromptDetailEvent) {
        when (event) {

            PromptDetailEvent.EditClicked -> {
                _state.update {
                    it.copy(
                        isEditing = true,
                        // Создаем копию для безопасного редактирования
                        draftPrompt = it.prompt?.copy()
                    )
                }
            }
            PromptDetailEvent.CancelClicked -> {
                _state.update { it.copy(isEditing = false, draftPrompt = null) }
            }
            PromptDetailEvent.SaveClicked -> {
                _state.update {
                    it.copy(
                        isEditing = false,
                        prompt = it.draftPrompt, // Обновляем основной prompt
                        draftPrompt = null
                    )
                }
            }
            is PromptDetailEvent.TitleChanged -> {
                _state.update {
                    it.copy(draftPrompt = it.draftPrompt?.copy(title = event.newTitle))
                }
            }
            PromptDetailEvent.BackClicked -> onNavigateBack()
            is PromptDetailEvent.ContentChanged -> {
                _state.update { state ->
                    val newContent = when(event.lang) {
                        PromptLanguage.RU -> state.draftPrompt?.content?.copy(ru = event.newContent)
                        PromptLanguage.EN -> state.draftPrompt?.content?.copy(en = event.newContent)
                    }
                    state.copy(draftPrompt = state.draftPrompt?.copy(content = newContent))
                }
            }
            PromptDetailEvent.FavoriteClicked -> {}
            PromptDetailEvent.Refresh -> {}
        }
    }


    private fun loadPromptDetails() {
        _state.update { it.copy(isLoading = true, error = null) }

        scope.launch {
            getPromptUseCase.getPromptFlow(promptId)
                .collect { result ->
                    result.onSuccess { prompt ->
                        _state.update { it.copy(prompt = prompt, isLoading = false) }
                    }.onFailure { error ->
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    }
                }
        }
    }
}
