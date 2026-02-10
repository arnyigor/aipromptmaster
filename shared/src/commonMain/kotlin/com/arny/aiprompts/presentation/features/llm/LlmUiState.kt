package com.arny.aiprompts.presentation.features.llm

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.results.DataResult

/**
 * Состояние UI для экрана LLM чата.
 * Единый источник истины для всего состояния чата.
 *
 * @property modelsResult Результат загрузки списка моделей
 * @property searchQuery Поисковый запрос для фильтрации моделей
 * @property selectedCategory Выбранная категория фильтрации
 * @property selectedSortOrder Порядок сортировки моделей
 * @property prompt Текущий текст в поле ввода
 * @property chatSessions Список всех сессий чата
 * @property selectedChatId ID текущей выбранной сессии
 * @property messages Сообщения текущей сессии (загружаются из БД)
 * @property isLoadingMessages Флаг загрузки сообщений
 * @property showChatList Видимость боковой панели со списком чатов
 * @property showParameters Видимость панели параметров
 * @property showModelDialog Видимость диалога выбора модели
 * @property errorMessage Сообщение об ошибке (показывается в Snackbar)
 * @property searchHistoryQuery Поисковый запрос по истории сообщений
 * @property isSearchingHistory Флаг активного поиска по истории
 */
data class LlmUiState(
    // Модели
    val modelsResult: DataResult<List<LlmModel>> = DataResult.Loading,
    val searchQuery: String = "",
    val selectedCategory: ModelCategory = ModelCategory.ALL,
    val selectedSortOrder: ModelSortOrder = ModelSortOrder.NAME,
    
    // Ввод
    val prompt: String = "",
    
    // Сессии
    val chatSessions: List<ChatSession> = emptyList(),
    val selectedChatId: String? = null,
    
    // Сообщения текущей сессии
    val messages: List<ChatMessage> = emptyList(),
    val isLoadingMessages: Boolean = false,
    
    // UI состояние
    val showChatList: Boolean = true,
    val showParameters: Boolean = true,
    val showModelDialog: Boolean = false,
    val errorMessage: String? = null,
    
    // Поиск по истории
    val searchHistoryQuery: String = "",
    val isSearchingHistory: Boolean = false
) {
    // ==================== Computed Properties ====================
    
    /** Выбранная модель из списка доступных. */
    val selectedModel: LlmModel?
        get() = when (modelsResult) {
            is DataResult.Success -> modelsResult.data.firstOrNull { it.isSelected }
            else -> null
        }

    /** Проверяет, идет ли сейчас генерация ответа. */
    val isGenerating: Boolean
        get() = messages.any { msg -> msg.isStreaming() }

    /** Отфильтрованные и отсортированные модели для отображения. */
    val displayModels: List<LlmModel>
        get() = when (modelsResult) {
            is DataResult.Success -> {
                var filtered = modelsResult.data

                // Применяем поиск
                if (searchQuery.isNotBlank()) {
                    filtered = filtered.filter { 
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true) 
                    }
                }

                // Применяем фильтр категории
                filtered = when (selectedCategory) {
                    ModelCategory.ALL -> filtered
                    ModelCategory.FREE -> filtered.filter {
                        it.pricingPrompt == null || it.pricingPrompt.toDouble() == 0.0
                    }
                    ModelCategory.TEXT -> filtered.filter { "text" in it.inputModalities }
                    ModelCategory.VISION -> filtered.filter { "image" in it.inputModalities }
                    ModelCategory.CODE -> filtered.filter {
                        "text" in it.inputModalities && "code" in it.outputModalities
                    }
                }

                // Применяем сортировку
                filtered = when (selectedSortOrder) {
                    ModelSortOrder.NAME -> filtered.sortedBy { it.name }
                    ModelSortOrder.CONTEXT_LENGTH -> filtered.sortedByDescending { it.contextLength }
                    ModelSortOrder.CREATED -> filtered.sortedByDescending { it.created }
                }

                filtered
            }
            else -> emptyList()
        }

    /** Текущая выбранная сессия чата. */
    val currentSession: ChatSession?
        get() = chatSessions.find { it.id == selectedChatId }

    /** Настройки текущей сессии (или настройки по умолчанию). */
    val currentSettings: ChatSettings
        get() = currentSession?.settings ?: ChatSettings()

    /** System prompt текущей сессии. */
    val currentSystemPrompt: String
        get() = currentSession?.systemPrompt ?: ""

    /** Общее количество токенов в текущей сессии. */
    val totalTokens: Int
        get() = messages.sumOf { it.tokenCount ?: 0 }

    /** Проверяет, можно ли отправить сообщение. */
    val canSendMessage: Boolean
        get() = prompt.isNotBlank() && selectedModel != null && !isGenerating

    /** Последнее сообщение для preview в списке сессий. */
    val lastMessagePreview: String
        get() = messages.lastOrNull()?.content?.take(50) ?: ""

    /** Количество сообщений в текущей сессии. */
    val messageCount: Int
        get() = messages.size
}

/**
 * Категории фильтрации моделей.
 */
enum class ModelCategory(val displayName: String) {
    ALL("Все модели"),
    FREE("Бесплатные"),
    TEXT("Только текст"),
    VISION("С изображениями"),
    CODE("Для кода")
}

/**
 * Порядки сортировки моделей.
 */
enum class ModelSortOrder(val displayName: String) {
    NAME("По имени"),
    CONTEXT_LENGTH("По контексту"),
    CREATED("По дате создания")
}

/**
 * Состояние отправки сообщения.
 */
sealed class SendMessageState {
    object Idle : SendMessageState()
    object Sending : SendMessageState()
    data class Streaming(val progress: String) : SendMessageState()
    data class Error(val message: String) : SendMessageState()
    object Success : SendMessageState()
}
