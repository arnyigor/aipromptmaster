package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.presentation.ui.detail.PromptDetailState
import com.arny.aiprompts.presentation.ui.detail.PromptLanguage
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
    private val promptId: String, // Получаем ID из навигации
    private val onNavigateBack: () -> Unit, // Коллбэк для возврата назад
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


    private suspend fun loadPromptDetails() {
         // Убедимся, что при каждом вызове (например, для Refresh) показывается индикатор
        _state.update { it.copy(isLoading = true, error = null) }

        try {
            val loadedPrompt = promptsInteractor.getPromptById(promptId)
            if (loadedPrompt != null) {
                // Успех: обновляем состояние с полученными данными
                _state.update {
                    it.copy(
                        isLoading = false,
                        prompt = loadedPrompt
                    )
                }
            } else {
                // Ошибка: промпт с таким ID не найден
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Промпт с ID '$promptId' не найден."
                    )
                }
            }
        } catch (e: Exception) {
            // Ошибка: произошло исключение при загрузке
            Logger.e(e) { "Ошибка загрузки деталей промпта" }
            _state.update {
                it.copy(
                    isLoading = false,
                    error = "Не удалось загрузить данные: ${e.message}"
                )
            }
        }
    }
}
