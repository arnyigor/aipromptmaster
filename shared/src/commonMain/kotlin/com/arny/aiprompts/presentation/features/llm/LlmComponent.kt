package com.arny.aiprompts.presentation.features.llm

import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Интерфейс компонента для работы с LLM чатом.
 * Определяет контракт между UI и бизнес-логикой.
 */
interface LlmComponent {

    /** Текущее состояние UI. */
    val uiState: StateFlow<LlmUiState>

    // ==================== Навигация ====================

    /** Возврат к предыдущему экрану. */
    fun onNavigateBack()

    // ==================== Модели ====================

    /** Выбор модели из списка. */
    fun onModelSelected(modelId: String)

    /** Обновление поискового запроса моделей. */
    fun onSearchQueryChanged(query: String)

    /** Выбор категории фильтрации моделей. */
    fun onCategorySelected(category: ModelCategory)

    /** Выбор порядка сортировки моделей. */
    fun onSortOrderSelected(sortOrder: ModelSortOrder)

    /** Принудительное обновление списка моделей из API. */
    fun refreshModels()

    /** Переключение видимости диалога выбора модели. */
    fun toggleModelDialog()

    // ==================== Сессии чата ====================

    /** Выбор сессии чата. */
    fun onChatSessionSelected(sessionId: String)

    /** Создание новой сессии чата. */
    fun onCreateNewChatSession()

    /** Удаление сессии чата. */
    fun onDeleteChatSession(sessionId: String)

    /** Переименование сессии чата. */
    fun onRenameChatSession(sessionId: String, newName: String)

    /** Архивирование сессии чата. */
    fun onArchiveChatSession(sessionId: String)

    /** Обновление system prompt для текущей сессии. */
    fun onSystemPromptChanged(systemPrompt: String)

    /** Обновление настроек чата. */
    fun onChatSettingsChanged(settings: ChatSettings)

    /** Переключение видимости панели списка чатов. */
    fun toggleChatList()

    // ==================== Сообщения ====================

    /** Изменение текста в поле ввода. */
    fun onPromptChanged(newPrompt: String)

    /** Отправка сообщения (запуск генерации). */
    fun onStreamingGenerateClicked()

    /** Отмена текущей генерации. */
    fun onCancelGenerating()

    /** Повторная отправка сообщения с ошибкой. */
    fun onRetryMessage(messageId: String)

    /** Редактирование сообщения пользователя. */
    fun onEditMessage(messageId: String, newContent: String)

    /** Удаление сообщения. */
    fun onDeleteMessage(messageId: String)

    /** Очистка истории текущего чата. */
    fun clearChat()

    // ==================== UI ====================

    /** Переключение видимости панели параметров. */
    fun toggleParameters()

    /** Очистка сообщения об ошибке. */
    fun clearError()

    /** Поиск по истории сообщений. */
    fun onSearchInHistory(query: String)
}
