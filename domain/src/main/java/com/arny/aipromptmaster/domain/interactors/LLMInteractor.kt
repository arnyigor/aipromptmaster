package com.arny.aipromptmaster.domain.interactors

// LLMInteractor.kt
import android.util.Log
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.errors.getFriendlyMessage
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val modelsRepository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository,
    private val historyRepository: IChatHistoryRepository
) : ILLMInteractor {

    // Определяем максимальное количество сообщений в истории.
    // 20 сообщений (10 пар "вопрос-ответ") - хороший старт.
    // Можно вынести в настройки, если хотите дать пользователю выбор.
   private companion object {
        const val MAX_HISTORY_SIZE = 20
    }

    override suspend fun createNewConversation(title: String): String {
        return historyRepository.createNewConversation(title)
    }


    override suspend fun addUserMessageToHistory(conversationId: String, userMessage: String) {
        historyRepository.addMessages(
            conversationId,
            listOf(ChatMessage(role = ChatRole.USER, content = userMessage))
        )
    }

    override suspend fun addAssistantMessageToHistory(
        conversationId: String,
        assistantMessage: String
    ) {
        historyRepository.addMessages(
            conversationId,
            listOf(ChatMessage(role = ChatRole.ASSISTANT, content = assistantMessage))
        )
    }

    /**
     * Отправляет сообщение, используя ограниченный контекст.
     */
    override fun sendMessage(model: String, conversationId: String?): Flow<DataResult<String>> =
        flow {
            emit(DataResult.Loading)
            try {
                val apiKey = settingsRepository.getApiKey()?.trim()
                if (apiKey.isNullOrEmpty()) {
                    emit(DataResult.Error(DomainError.Local("API ключ не указан.")))
                    return@flow
                }

                // 3. Получаем контекст для API
                val messagesForApi = getChatHistoryFlow(conversationId)
                    .first()
                    .takeLast(MAX_HISTORY_SIZE)

                val result = modelsRepository.getChatCompletion(model, messagesForApi, apiKey)
                result.fold(
                    onSuccess = { response ->
                        val content = response.choices.firstOrNull()?.message?.content
                        if (content != null) {
                            emit(DataResult.Success(content))
                        } else {
                            emit(DataResult.Error(DomainError.Generic("Пустой ответ от API")))
                        }
                    },
                    onFailure = { exception -> emit(DataResult.Error(exception)) }
                )
            } catch (e: Exception) {
                emit(DataResult.Error(DomainError.Generic(e.message)))
            }
        }

    /**
     * Отправляет сообщение. Сначала пытается сделать это через стриминг.
     * Если стриминг проваливается, автоматически переключается на обычный запрос.
     * Все обновления происходят через базу данных, на которую подписан UI.
     */
    override suspend fun sendMessageWithFallback(model: String, conversationId: String?) {
        // 1. Создаем "пустое" сообщение от ассистента в БД.
        //    UI сразу покажет "печатающий" индикатор.
        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = "")
        val assistantMessageId = historyRepository.addMessage(conversationId.orEmpty(), assistantMessage)

        try {
            // --- ПОПЫТКА 1: СТРИМИНГ ---
            val apiKey = settingsRepository.getApiKey()?.trim()
                ?: throw DomainError.Local("API ключ не указан.")

            val messagesForApi = historyRepository.getHistoryFlow(conversationId.orEmpty())
                .first().takeLast(MAX_HISTORY_SIZE)

            var streamFailed = false
            modelsRepository.getChatCompletionStream(model, messagesForApi, apiKey)
                .catch {
                    // Эта ошибка означает, что сам Flow сломался (например, разрыв сети)
                    streamFailed = true
                    Log.e("LLMInteractor", "Stream failed catastrophically", it)
                }
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            // Добавляем часть текста к сообщению в БД
                            historyRepository.appendContentToMessage(assistantMessageId, result.data)
                        }
                        is DataResult.Error -> {
                            // Ошибка внутри потока (например, 429 Rate Limit)
                            streamFailed = true
                            throw result.exception ?: DomainError.Generic("Stream returned an error")
                        }
                        DataResult.Loading -> {}
                    }
                }

            if (streamFailed) {
                // Если была ошибка, collect завершится, и мы вызовем исключение,
                // чтобы перейти в блок catch для fallback.
                throw DomainError.Generic("Stream failed, attempting fallback.")
            }

        } catch (e: Exception) {
            // --- ПОПЫТКА 2: FALLBACK НА ОБЫЧНЫЙ ЗАПРОС ---
            Log.w("LLMInteractor", "Streaming failed with error: ${e.message}. Falling back to regular request.")

            try {
                val apiKey = settingsRepository.getApiKey()?.trim()
                    ?: throw DomainError.Local("API ключ не указан.")
                val messagesForApi = historyRepository.getHistoryFlow(conversationId.orEmpty())
                    .first().takeLast(MAX_HISTORY_SIZE)

                val result = modelsRepository.getChatCompletion(model, messagesForApi, apiKey)

                result.fold(
                    onSuccess = { response ->
                        val fullContent = response.choices.firstOrNull()?.message?.content ?: ""
                        // Обновляем наше "пустое" сообщение полным текстом
                        historyRepository.updateMessageContent(assistantMessageId, fullContent)
                    },
                    onFailure = { fallbackError ->
                        // Обе попытки провалились. Обновляем сообщение текстом ошибки.
                        val errorMessage = fallbackError.getFriendlyMessage()
                        historyRepository.updateMessageContent(assistantMessageId, "Ошибка: $errorMessage")
                    }
                )
            } catch (finalError: Exception) {
                val errorMessage = finalError.getFriendlyMessage()
                historyRepository.updateMessageContent(assistantMessageId, "Ошибка: $errorMessage")
            }
        }
    }

    override fun getChatList(): Flow<List<Chat>> {
        return historyRepository.getChatList()
    }

    override fun getChatHistoryFlow(conversationId: String?): Flow<List<ChatMessage>> =
        historyRepository.getHistoryFlow(conversationId.orEmpty())

    override suspend fun clearChat(conversationId: String?) {
        historyRepository.clearHistory(conversationId.orEmpty())
    }

    /**
     * Предоставляет поток со списком моделей, обогащенным состоянием выбора.
     */
    override fun getModels(): Flow<DataResult<List<LlmModel>>> {
        val selectedIdFlow: Flow<String?> = settingsRepository.getSelectedModelId()
        val modelsListFlow: Flow<List<LlmModel>> = modelsRepository.getModelsFlow()
        return combine(selectedIdFlow, modelsListFlow) { selectedId, modelsList ->
            Log.i(
                this::class.java.simpleName,
                "getModels: selectedId: $selectedId, modelsList: ${modelsList.size}"
            )
            // Эта лямбда будет выполняться каждый раз, когда меняется ID или список моделей.
            if (modelsList.isEmpty()) {
                DataResult.Loading
            } else {
                val mappedList = modelsList.map { model ->
                    model.copy(isSelected = model.id == selectedId)
                }
                DataResult.Success(mappedList)
            }
        }.onStart { emit(DataResult.Loading) } // Начинаем с Loading в любом случае.
    }

    /**
     * Возвращает реактивный поток с деталями только одной выбранной модели.
     */
    override fun getSelectedModel(): Flow<DataResult<LlmModel>> {
        return getModels().map { dataResult ->
            when (dataResult) {
                is DataResult.Success -> {
                    val selected = dataResult.data.find { it.isSelected }
                    if (selected != null) {
                        DataResult.Success(selected)
                    } else {
                        DataResult.Error(null, R.string.selected_model_not_found)
                    }
                }

                is DataResult.Error -> DataResult.Error(dataResult.exception)
                is DataResult.Loading -> dataResult
            }
        }
    }

    /**
     * Сохраняет выбор пользователя в репозитории настроек.
     */
    override suspend fun selectModel(id: String) {
        settingsRepository.setSelectedModelId(id)
    }

    /**
     * Запускает принудительное обновление списка моделей.
     */
    override suspend fun refreshModels(): Result<Unit> = modelsRepository.refreshModels()

    /**
     * НОВЫЙ МЕТОД: Обрабатывает клик, решая, выбрать или отменить выбор.
     */
    override suspend fun toggleModelSelection(clickedModelId: String) {
        // 1. Получаем ТЕКУЩИЙ выбранный ID.
        //    Используем `first()` чтобы получить однократное значение из потока.
        val currentlySelectedId = settingsRepository.getSelectedModelId().firstOrNull()

        // 2. Принимаем решение
        if (currentlySelectedId == clickedModelId) {
            // Если кликнули на уже выбранную модель -> отменяем выбор
            settingsRepository.setSelectedModelId(null)
        } else {
            // Если кликнули на другую модель -> выбираем ее
            settingsRepository.setSelectedModelId(clickedModelId)
        }
    }
}
