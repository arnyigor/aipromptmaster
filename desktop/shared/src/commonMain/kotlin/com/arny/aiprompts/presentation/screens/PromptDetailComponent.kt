@file:OptIn(ExperimentalTime::class)

package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import com.arny.aiprompts.domain.usecase.GetPromptUseCase
import com.arny.aiprompts.domain.usecase.UpdatePromptUseCase
import com.arny.aiprompts.domain.usecase.CreatePromptUseCase
import com.arny.aiprompts.domain.usecase.DeletePromptUseCase
import com.arny.aiprompts.domain.usecase.GetAvailableTagsUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import com.arny.aiprompts.presentation.ui.detail.PromptDetailState
import com.arny.aiprompts.presentation.ui.detail.PromptLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Добавим события для управления редактированием
sealed interface PromptDetailEvent {
    data object OpenImprovement : PromptDetailEvent
    data class Improvement(val action: com.arny.aiprompts.presentation.ui.detail.PromptImprovementAction) : PromptDetailEvent
    object BackClicked : PromptDetailEvent
    object FavoriteClicked : PromptDetailEvent
    object Refresh : PromptDetailEvent
    object EditClicked : PromptDetailEvent
    object CancelClicked : PromptDetailEvent
    object SaveClicked : PromptDetailEvent
    object DeleteClicked : PromptDetailEvent
    object ShowDeleteDialog : PromptDetailEvent
    object HideDeleteDialog : PromptDetailEvent
    object ConfirmDelete : PromptDetailEvent
    data class TitleChanged(val newTitle: String) : PromptDetailEvent
    data class ContentChanged(val lang: PromptLanguage, val newContent: String) : PromptDetailEvent
    data class TagAdded(val tag: String) : PromptDetailEvent
    data class TagRemoved(val tag: String) : PromptDetailEvent
}

interface PromptDetailComponent {
    val state: StateFlow<PromptDetailState>
    fun onEvent(event: PromptDetailEvent)
}

class DefaultPromptDetailComponent(
    componentContext: ComponentContext,
    private val getPromptUseCase: GetPromptUseCase,
    private val updatePromptUseCase: UpdatePromptUseCase,
    private val createPromptUseCase: CreatePromptUseCase,
    private val deletePromptUseCase: DeletePromptUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val getAvailableTagsUseCase: GetAvailableTagsUseCase,
    private val promptId: String,
    private val onNavigateBack: () -> Unit,
    improvePromptUseCase: com.arny.aiprompts.domain.usecase.ImprovePromptUseCase? = null,
) : PromptDetailComponent, ComponentContext by componentContext {

    private var currentPromptId = promptId

    private val _state = MutableStateFlow(PromptDetailState(isLoading = true))
    override val state: StateFlow<PromptDetailState> = _state.asStateFlow()

    private val scope = coroutineScope()
    private val improvementController = improvePromptUseCase?.let { useCase ->
        com.arny.aiprompts.presentation.ui.detail.PromptImprovementController(useCase, scope) { language, text ->
            _state.update { current ->
                val base = current.draftPrompt ?: current.prompt
                val content = base?.content ?: PromptContent()
                val improved = if (language == PromptLanguage.RU) content.copy(ru = text) else content.copy(en = text)
                current.copy(isEditing = true, draftPrompt = base?.copy(content = improved, isLocal = true))
            }
        }
    }

    init {
        improvementController?.let { controller ->
            scope.launch { controller.state.collect { state -> _state.update { it.copy(improvement = state) } } }
        }
        scope.launch {
            // Проверяем, существует ли промпт с таким ID
            getPromptUseCase.getPromptFlow(currentPromptId)
                .collect { result ->
                    result.onSuccess { existingPrompt ->
                        if (existingPrompt == null) {
                            // Новый промпт - инициализируем пустое состояние в режиме редактирования
                            _state.update { createNewPrompt() }
                            loadAvailableTags()
                        } else {
                            // Существующий промпт - загружаем его
                            loadPromptDetails()
                        }
                    }.onFailure { error ->
                        // В случае ошибки считаем, что промпт новый
                        _state.update { createNewPrompt() }
                        loadAvailableTags()
                    }
                }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun createNewPrompt(): PromptDetailState = PromptDetailState(
        isLoading = false,
        isEditing = true,
        draftPrompt = Prompt(
            id = currentPromptId,
            title = "",
            content = PromptContent(ru = "", en = ""),
            description = null,
            category = "general",
            tags = emptyList(),
            compatibleModels = emptyList(),
            status = "draft",
            isLocal = true,
            createdAt = Clock.System.now(),
            modifiedAt = Clock.System.now()
        )
    )

    override fun onEvent(event: PromptDetailEvent) {
        when (event) {
            PromptDetailEvent.OpenImprovement -> {
                (_state.value.draftPrompt ?: _state.value.prompt)?.let { improvementController?.open(it) }
            }
            is PromptDetailEvent.Improvement -> improvementController?.onAction(event.action)

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

                        val result = if (_state.value.prompt == null) {
                            // Новый промпт
                            createPromptUseCase(
                                title = draftPrompt.title,
                                contentRu = draftPrompt.content?.ru.orEmpty(),
                                contentEn = draftPrompt.content?.en.orEmpty(),
                                description = draftPrompt.description,
                                category = draftPrompt.category,
                                tags = draftPrompt.tags,
                                compatibleModels = draftPrompt.compatibleModels,
                                status = "active"
                            )
                        } else {
                            // Существующий промпт
                            updatePromptUseCase(
                                promptId = currentPromptId,
                                title = draftPrompt.title,
                                contentRu = draftPrompt.content?.ru.orEmpty(),
                                contentEn = draftPrompt.content?.en.orEmpty(),
                                description = draftPrompt.description,
                                category = draftPrompt.category,
                                tags = draftPrompt.tags,
                                compatibleModels = draftPrompt.compatibleModels
                            )
                        }

                        result.onSuccess { newPromptId ->
                            val savedPromptId = if (_state.value.prompt == null) newPromptId.toString() else currentPromptId
                            _state.update {
                                it.copy(
                                    isEditing = false,
                                    prompt = draftPrompt.copy(id = savedPromptId), // Обновляем основной prompt
                                    draftPrompt = null,
                                    isSaving = false
                                )
                            }
                            // Если это новый промпт, обновляем currentPromptId для дальнейших операций
                            if (_state.value.prompt == null) {
                                currentPromptId = savedPromptId
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
            PromptDetailEvent.DeleteClicked -> {
                if (_state.value.prompt?.isLocal == true) {
                    _state.update { it.copy(showDeleteDialog = true) }
                }
            }
            PromptDetailEvent.ShowDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = true) }
            }
            PromptDetailEvent.HideDeleteDialog -> {
                _state.update { it.copy(showDeleteDialog = false) }
            }
            PromptDetailEvent.ConfirmDelete -> {
                if (_state.value.prompt?.isLocal == true) {
                    scope.launch {
                        _state.update { it.copy(showDeleteDialog = false) }
                        deletePromptUseCase(currentPromptId).onSuccess {
                            onNavigateBack()
                        }
                    }
                }
            }
            PromptDetailEvent.FavoriteClicked -> {
                scope.launch {
                    toggleFavoriteUseCase(currentPromptId)
                    // После изменения статуса получаем обновленный промпт
                    getPromptUseCase.getPromptFlow(currentPromptId)
                        .collect { result ->
                            result.onSuccess { updatedPrompt ->
                                _state.update { it.copy(prompt = updatedPrompt) }
                            }
                        }
                }
            }
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

        loadAvailableTags()
    }

    private fun loadAvailableTags() {
        // Загружаем доступные теги для автодополнения
        scope.launch {
            getAvailableTagsUseCase().onSuccess { tags ->
                _state.update { it.copy(availableTags = tags) }
            }
        }
    }
}
