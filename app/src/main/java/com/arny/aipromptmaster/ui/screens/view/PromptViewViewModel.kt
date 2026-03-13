package com.arny.aipromptmaster.ui.screens.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.DomainPromptVariant
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.models.strings.toErrorHolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * Представляет состояние экрана отображения промпта с поддержкой вариантов.
 *
 * Используется для управления UI-состоянием экрана, связанного с отображением данных промпта.
 * Позволяет обрабатывать различные этапы жизненного цикла загрузки данных: от начального состояния до отображения контента или ошибки.
 */
sealed class PromptViewUiState {

    /**
     * Начальное состояние экрана, до начала загрузки данных.
     */
    data object Initial : PromptViewUiState()

    /**
     * Состояние загрузки данных промпта.
     */
    data object Loading : PromptViewUiState()

    /**
     * Состояние успешной загрузки данных промпта с поддержкой вариантов.
     *
     * @property prompt Объект промпта, содержащий всю необходимую информацию для отображения.
     * @property selectedVariantIndex Индекс выбранного варианта (-1 для основного контента).
     * @property availableVariants Список доступных вариантов для отображения.
     * @property currentContent Текущий отображаемый контент (основной или выбранного варианта).
     * @property isLocal Флаг, указывающий, является ли промпт локальным (для управления видимостью кнопки удаления).
     */
    data class Content(
        val prompt: Prompt,
        val selectedVariantIndex: Int = -1,
        val availableVariants: List<DomainPromptVariant> = emptyList(),
        val currentContent: PromptContent = prompt.content,
        val isLocal: Boolean = prompt.isLocal
    ) : PromptViewUiState()

    /**
     * Состояние ошибки при загрузке данных промпта.
     *
     * @property stringHolder Объект, содержащий информацию об ошибке, локализованную строку или код ошибки.
     */
    data class Error(val stringHolder: StringHolder) : PromptViewUiState()
}

// 1. Действия пользователя (UI -> VM)
sealed interface PromptUiIntent {
    data object ToggleFavorite : PromptUiIntent
    data class SelectVariant(val index: Int) : PromptUiIntent
    data class CopyText(val text: String, val label: String) : PromptUiIntent
    data object RequestDelete : PromptUiIntent
    data object NavigateToEdit : PromptUiIntent
}

// 2. Эффекты для экрана (VM -> UI)
sealed interface PromptUiEffect {
    data class ShowToast(val message: StringHolder) : PromptUiEffect
    data class CopyToClipboard(val text: String, val label: String) : PromptUiEffect
    data object NavigateBack : PromptUiEffect
    data object ShowDeleteDialog : PromptUiEffect
    data class NavigateToEdit(val id: String) : PromptUiEffect
}

class PromptViewViewModel(
    private val promptId: String,
    private val interactor: IPromptsInteractor
) : ViewModel() {
    private val _uiState = MutableStateFlow<PromptViewUiState>(PromptViewUiState.Initial)
    val uiState: StateFlow<PromptViewUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<PromptUiEffect>()
    val uiEffect: SharedFlow<PromptUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadPrompt()
    }

    fun loadPrompt() {
        viewModelScope.launch {
            _uiState.value = PromptViewUiState.Loading
            try {
                interactor.observePrompt(promptId)
                    .catch { showError(it) }
                    .collect { prompt ->
                        if (prompt != null) {
                            _uiState.update { currentState ->
                                if (currentState is PromptViewUiState.Content) {
                                    // Если у нас уже был контент, сохраняем выбор варианта
                                    val currentIdx = currentState.selectedVariantIndex

                                    // Вычисляем, какой текст показывать
                                    val contentToShow = if (currentIdx == -1) {
                                        prompt.content
                                    } else {
                                        // Пытаемся найти тот же вариант по индексу (или по ID, если бы он был)
                                        prompt.promptVariants.getOrNull(currentIdx)?.content
                                            ?: prompt.content
                                    }

                                    currentState.copy(
                                        prompt = prompt,
                                        // Индекс оставляем старым
                                        // selectedVariantIndex = currentIdx, // (он уже в copy)
                                        availableVariants = prompt.promptVariants,
                                        currentContent = contentToShow,
                                        isLocal = prompt.isLocal
                                    )
                                } else {
                                    // Первый запуск (или после ошибки)
                                    PromptViewUiState.Content(
                                        prompt = prompt,
                                        selectedVariantIndex = -1,
                                        availableVariants = prompt.promptVariants,
                                        currentContent = prompt.content,
                                        isLocal = prompt.isLocal
                                    )
                                }
                            }
                        } else {
                            _uiState.value =
                                PromptViewUiState.Error(StringHolder.Resource(R.string.prompt_not_found))
                        }
                    }
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    fun selectVariant(variantIndex: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is PromptViewUiState.Content) {
                val newContent = if (variantIndex == -1) {
                    // Выбран основной контент
                    currentState.prompt.content
                } else {
                    // Выбран вариант
                    currentState.availableVariants.getOrNull(variantIndex)?.content
                        ?: currentState.prompt.content
                }

                _uiState.value = currentState.copy(
                    selectedVariantIndex = variantIndex,
                    currentContent = newContent
                )
            }
        }
    }

    fun copyContent(content: String, label: String) {
        viewModelScope.launch {
            _uiEffect.emit(PromptUiEffect.CopyToClipboard(content, label))
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                interactor.toggleFavorite(promptId)
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    fun showDeleteConfirmation() {
        viewModelScope.launch {
            _uiEffect.emit(PromptUiEffect.ShowDeleteDialog)
        }
    }
    fun navigateToEdit() {
        viewModelScope.launch {
            _uiEffect.emit(PromptUiEffect.NavigateToEdit(promptId))
        }
    }

    fun deletePrompt() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                if (currentState is PromptViewUiState.Content && currentState.isLocal) {
                    interactor.deletePrompt(promptId)
                    _uiEffect.emit(PromptUiEffect.ShowToast(StringHolder.Resource(R.string.prompt_deleted)))
                    _uiEffect.emit(PromptUiEffect.NavigateBack)
                }
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun showError(e: Throwable) {
        _uiState.value = PromptViewUiState.Error(e.toErrorHolder())
    }
}
