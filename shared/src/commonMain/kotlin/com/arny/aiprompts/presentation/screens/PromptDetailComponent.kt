package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.usecase.GetPromptUseCase
import com.arny.aiprompts.domain.usecase.UpdatePromptUseCase
import com.arny.aiprompts.domain.usecase.GetAvailableTagsUseCase
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
    data class TagAdded(val tag: String) : PromptDetailEvent
    data class TagRemoved(val tag: String) : PromptDetailEvent
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
    private val updatePromptUseCase: UpdatePromptUseCase,
    private val getAvailableTagsUseCase: GetAvailableTagsUseCase,
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
                val draftPrompt = _state.value.draftPrompt
                if (draftPrompt != null) {
                    scope.launch {
                        _state.update { it.copy(isSaving = true, saveError = null) }

                        val result = updatePromptUseCase(
                            promptId = promptId,
                            title = draftPrompt.title,
                            contentRu = draftPrompt.content?.ru.orEmpty(),
                            contentEn = draftPrompt.content?.en.orEmpty(),
                            description = draftPrompt.description,
                            category = draftPrompt.category,
                            tags = draftPrompt.tags,
                            compatibleModels = draftPrompt.compatibleModels
                        )

                        result.onSuccess {
                            _state.update {
                                it.copy(
                                    isEditing = false,
                                    prompt = draftPrompt, // Обновляем основной prompt
                                    draftPrompt = null,
                                    isSaving = false
                                )
                            }
                        }.onFailure { error ->
                            _state.update {
                                it.copy(
                                    isSaving = false,
                                    saveError = error.message ?: "Failed to save prompt"
                                )
                            }
                        }
                    }
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
            is PromptDetailEvent.TagAdded -> {
                _state.update {
                    val currentTags = it.draftPrompt?.tags ?: emptyList()
                    it.copy(draftPrompt = it.draftPrompt?.copy(tags = currentTags + event.tag))
                }
            }
            is PromptDetailEvent.TagRemoved -> {
                _state.update {
                    val currentTags = it.draftPrompt?.tags ?: emptyList()
                    it.copy(draftPrompt = it.draftPrompt?.copy(tags = currentTags - event.tag))
                }
            }
        }
    }


    private fun loadPromptDetails() {
        _state.update { it.copy(isLoading = true, error = null) }

        scope.launch {
            // Загружаем промпт
            getPromptUseCase.getPromptFlow(promptId)
                .collect { result ->
                    result.onSuccess { prompt ->
                        _state.update { it.copy(prompt = prompt, isLoading = false) }
                    }.onFailure { error ->
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    }
                }
        }

        // Загружаем доступные теги для автодополнения
        scope.launch {
            getAvailableTagsUseCase().onSuccess { tags ->
                _state.update { it.copy(availableTags = tags) }
            }
        }
    }
}
