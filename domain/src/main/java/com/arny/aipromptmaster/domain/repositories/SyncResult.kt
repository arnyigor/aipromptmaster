package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.strings.StringHolder

/**
 * Представляет результат операции синхронизации промптов.
 *
 * Используется для инкапсуляции различных исходов процесса синхронизации данных между локальным хранилищем
 * и удалённым источником (например, сервером или облаком). Позволяет обрабатывать успешные сценарии,
 * ошибки, конфликты и ограничения частоты запросов.
 */
sealed class SyncResult {

    /**
     * Успешное завершение синхронизации.
     *
     * @property updatedPrompts Список промптов, которые были обновлены или добавлены в результате синхронизации.
     */
    data class Success(val updatedPrompts: List<Prompt>) : SyncResult()

    /**
     * Ошибка во время синхронизации.
     *
     * @property stringHolder Описание ошибки, которое может быть использовано для отображения пользователю
     * или логирования.
     */
    data class Error(val stringHolder: StringHolder) : SyncResult()

    /**
     * Попытка синхронизации была отклонена из-за слишком частых запросов.
     *
     * Используется для предотвращения избыточной нагрузки на систему или соблюдения ограничений API.
     */
    data object TooSoon : SyncResult()
}