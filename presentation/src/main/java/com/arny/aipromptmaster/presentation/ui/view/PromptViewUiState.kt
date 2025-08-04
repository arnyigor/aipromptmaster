package com.arny.aipromptmaster.presentation.ui.view

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.strings.StringHolder

/**
 * Представляет состояние экрана отображения промпта.
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
     * Состояние успешной загрузки данных промпта.
     *
     * @property prompt Объект промпта, содержащий всю необходимую информацию для отображения.
     */
    data class Content(val prompt: Prompt) : PromptViewUiState()

    /**
     * Состояние ошибки при загрузке данных промпта.
     *
     * @property stringHolder Объект, содержащий информацию об ошибке, локализованную строку или код ошибки.
     */
    data class Error(val stringHolder: StringHolder) : PromptViewUiState()
}