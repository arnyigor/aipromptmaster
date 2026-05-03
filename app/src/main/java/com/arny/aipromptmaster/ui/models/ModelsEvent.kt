package com.arny.aipromptmaster.ui.models

import com.arny.aipromptmaster.domain.models.SortType

/**
 * События интента – действия пользователя, которые инициируют изменения состояния UI.
 */
sealed interface ModelsEvent {

    /**
     * Уведомление о том, что пользователь ввёл строку запроса в поисковую панель.
     *
     * @property query Текст, введённый пользователем (может быть пустым).
     */
    data class Search(val query: String) : ModelsEvent

    /**
     * Переключение фильтра «Только бесплатные».
     *
     * При каждом вызове флаг `onlyFree` меняется с `false` на `true`
     * и наоборот. Состояние сохраняется в ViewState.
     */
    data object ToggleFree : ModelsEvent

    /**
     * Переключение фильтра «Только избранные».
     *
     * При каждом вызове флаг `favoritesOnly` меняется с `false` на `true`
     * и наоборот. Состояние сохраняется в ViewState.
     */
    data object ToggleFavoritesOnly : ModelsEvent

    /**
     * Переключение фильтра «Только доступные».
     */
    data object ToggleAvailableOnly : ModelsEvent

    /**
     * Событие изменения порядка сортировки моделей.
     *
     * @property type Новый тип сортировки, определённый перечислением
     *                `[SortType]`. Вьюмодель обновляет состояние,
     *                а UI перерисовывается согласно новому `sortOrder`.
     */
    data class ChangeSort(val type: SortType) : ModelsEvent

    /**
     * Установить/снять отметку «избранное» у модели.
     *
     * @property id Идентификатор модели, к которой применяется действие.
     */
    data class ToggleModelFavorite(
        val id: String,
    ) : ModelsEvent

    /**
     * Принудительный запрос обновления данных с сервера.
     *
     * ViewModel инициирует сетевой вызов (или другой источник) и
     * приём свежих моделей. Учитывается ситуация, когда пользователь
     * находится в режиме офлайн – в этом случае действие может быть отложено.
     */
    data object Refresh : ModelsEvent

    /**
     * Выбор конкретной модели для дальнейшего взаимодействия (например,
     * переход на экран детали).
     *
     * @property id Идентификатор выбранной модели.
     */
    data class SelectModel(val id: String) : ModelsEvent

    /**
     * Проверка доступности всех free моделей.
     */
    data object CheckFreeModelsAvailability : ModelsEvent

    /**
     * Проверка доступности конкретной модели.
     *
     * @property id Идентификатор модели.
     */
    data class CheckModelAvailability(val id: String) : ModelsEvent

    /**
     * Отмена проверки доступности моделей.
     */
    data object CancelAvailabilityCheck : ModelsEvent
}
