package com.arny.aipromptmaster.presentation.ui.home

import com.arny.aipromptmaster.domain.models.SyncConflict
import com.arny.aipromptmaster.domain.models.strings.StringHolder

/**
 * События UI-слоя экрана работы с промптами.
 * Используются для коммуникации между ViewModel и UI (Composable/Screen).
 */
sealed class PromptsUiEvent {

    /**
     * Успешная синхронизация данных.
     * @param updatedCount Количество обновленных записей
     */
    data class SyncSuccess(val updatedCount: Int) : PromptsUiEvent()

    /**
     * Обнаружены конфликты синхронизации, требующие разрешения пользователем.
     * @param conflicts Список конфликтов [SyncConflict]
     */
    data class SyncConflicts(val conflicts: List<SyncConflict>) : PromptsUiEvent()

    /**
     * Синхронизация начата (сигнал для показа индикатора загрузки)
     */
    data object SyncInProgress : PromptsUiEvent()

    /**
     * Требуется показать сообщение об ошибке.
     * @param stringHolder Текст ошибки в обёртке [StringHolder]
     */
    data class ShowError(val stringHolder: StringHolder) : PromptsUiEvent()

    /**
     * Промпт успешно обновлен (сигнал для обновления UI)
     */
    data object PromptUpdated : PromptsUiEvent()

    data object SyncFinished : PromptsUiEvent()

    /**
     * Открытие экрана сортировки и фильтрации.
     * @param sortData Данные для сортировки
     * @param currentFilters Текущие активные фильтры
     */
    data class OpenSortScreenEvent(val sortData: SortData, val currentFilters: CurrentFilters) :
        PromptsUiEvent()

    /**
     * Сигнализирует о необходимости показать информационное сообщение (нейтрального характера).
     * Примеры: "Данные уже актуальны", "Операция завершена".
     *
     * @param stringHolder Текст сообщения в обёртке [StringHolder]
     */
    data class ShowInfoMessage(val stringHolder: StringHolder) : PromptsUiEvent()

}