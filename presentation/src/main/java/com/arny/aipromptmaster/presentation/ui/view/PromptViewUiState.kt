package com.arny.aipromptmaster.presentation.ui.view

import com.arny.aipromptmaster.domain.models.DomainPromptVariant
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.strings.StringHolder

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
     */
    data class Content(
        val prompt: Prompt,
        val selectedVariantIndex: Int = -1,
        val availableVariants: List<DomainPromptVariant> = emptyList(),
        val currentContent: PromptContent = prompt.content
    ) : PromptViewUiState()

    /**
     * Состояние ошибки при загрузке данных промпта.
     *
     * @property stringHolder Объект, содержащий информацию об ошибке, локализованную строку или код ошибки.
     */
    data class Error(val stringHolder: StringHolder) : PromptViewUiState()
}