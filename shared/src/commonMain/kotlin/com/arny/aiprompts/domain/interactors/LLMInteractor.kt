package com.arny.aiprompts.domain.interactors

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.MessageStatus
import com.arny.aiprompts.data.repositories.ApiException
import com.arny.aiprompts.data.repositories.IChatHistoryRepository
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.domain.strings.StringHolder
import com.arny.aiprompts.results.DataResult
import com.arny.aiprompts.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import java.util.UUID

class LLMInteractor(
    private val modelsRepository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository,
    private val historyRepository: IChatHistoryRepository
) : ILLMInteractor {

    private val _refreshError = MutableStateFlow<Exception?>(null)

    override fun sendMessage(model: String, userMessage: String): Flow<DataResult<String>> =
        flow {
            emit(DataResult.Loading)
            try {
                // 1. Создаем сообщение пользователя и сразу добавляем его в историю.
                historyRepository.addMessages(
                    listOf(
                        ChatMessage(
                            role = ChatMessageRole.USER,
                            content = userMessage,
                            timestamp = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                )

                // 3. Формируем полный контекст для API.
                // Берем историю (которая уже включает новое сообщение) и ограничиваем до 20 последних сообщений.
                val fullHistory = historyRepository.getHistoryFlow().first()
                val messagesForApi = if (fullHistory.size > 20) {
                    // Оставляем последние 20 сообщений (10 пар вопрос-ответ)
                    fullHistory.takeLast(20)
                } else {
                    fullHistory
                }

                // 4. Проверяем API ключ
                val apiKey = settingsRepository.getOpenRouterApiKey()?.trim()
                if (apiKey.isNullOrEmpty()) {
                    emit(DataResult.Error(IllegalArgumentException("OpenRouter API ключ не настроен. Пожалуйста, введите API ключ в настройках.")))
                    return@flow
                }

                // 5. Выполняем запрос с полным контекстом
                val result = modelsRepository.getChatCompletion(model, messagesForApi, apiKey)

                result.fold(
                    onSuccess = { response ->
                        val content = response.choices?.firstOrNull()?.message?.content
                        if (content != null) {
                            // 6. При успехе добавляем ответ модели в историю
                            val modelMessage = ChatMessage(
                                role = ChatMessageRole.MODEL,
                                content = content,
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                            historyRepository.addMessages(listOf(modelMessage))
                            emit(DataResult.Success(content))
                        } else {
                            emit(DataResult.Error(Exception("Empty response from API")))
                        }
                    },
                    onFailure = { exception -> emit(DataResult.Error(exception)) }
                )
            } catch (e: Exception) {
                emit(DataResult.Error(e))
            }
        }

    // НОВЫЙ МЕТОД для получения истории для UI
    override fun getChatHistoryFlow(): Flow<List<ChatMessage>> = historyRepository.getHistoryFlow()

    // МЕТОД для очистки истории
    override suspend fun clearChat() {
        historyRepository.clearHistory()
    }

    override fun sendStreamingMessage(
        model: String,
        userMessage: String
    ): Flow<DataResult<ChatMessage>> = flow {
        if (userMessage.length < MIN_MESSAGE_LENGTH) {
            emit(DataResult.Error(IllegalArgumentException("Message too short")))
            return@flow
        }

        val apiKey = settingsRepository.getOpenRouterApiKey()?.trim()
        if (apiKey.isNullOrEmpty()) {
            emit(DataResult.Error(ApiException.MissingApiKey()))
            return@flow
        }

        // 1. Создаем и сохраняем сообщение пользователя
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatMessageRole.USER,
            content = userMessage,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            status = MessageStatus.Sent
        )

        historyRepository.addMessage(userMsg)

        // 2. Создаем заглушку для ответа модели
        val modelMsgId = UUID.randomUUID().toString()
        val modelMsg = ChatMessage(
            id = modelMsgId,
            role = ChatMessageRole.MODEL,
            content = "",
            timestamp = Clock.System.now().toEpochMilliseconds(),
            status = MessageStatus.Streaming(isComplete = false),
            modelId = model
        )

        historyRepository.addMessage(modelMsg)
        emit(DataResult.Success(modelMsg))

        // 3. Получаем контекст для API
        val context = buildApiContext()

        // 4. Стримим ответ
        val accumulatedContent = StringBuilder()

        modelsRepository.getStreamingChatCompletion(model, context, apiKey)
            .collect { result ->
                result.fold(
                    onSuccess = { chunk ->
                        // только дополняй, если контент не пустой
                        if (chunk.content.isNotEmpty()) {
                            accumulatedContent.append(chunk.content)
                        }
                        val updatedMsg = modelMsg.copy(
                            content = accumulatedContent.toString(),
                            status = if (chunk.isComplete) MessageStatus.Sent else MessageStatus.Streaming(
                                false
                            )
                        )
                        historyRepository.updateMessage(updatedMsg)
                        emit(DataResult.Success(updatedMsg))
                    },
                    onFailure = { error ->
                        Logger.e(error, "LLMInteractor", "Streaming failed")

                        val failedMsg = modelMsg.copy(
                            content = accumulatedContent.toString(),
                            status = MessageStatus.Failed(
                                error = error.message ?: "Unknown error"
                            )
                        )

                        historyRepository.updateMessage(failedMsg)
                        emit(DataResult.Error(error))
                    }
                )
            }

    }.catch { e ->
        Logger.e(e, "LLMInteractor", "Unexpected error in sendStreamingMessage")
        emit(DataResult.Error(e))
    }

    private suspend fun buildApiContext(): List<ChatMessage> {
        return historyRepository.getHistoryFlow()
            .first()
            .filter { it.status is MessageStatus.Sent } // Только успешные
            .takeLast(MAX_CONTEXT_MESSAGES)
    }

    override suspend fun retryMessage(messageId: String) {
        val message = historyRepository.getMessage(messageId) ?: return

        if (message.role == ChatMessageRole.MODEL && message.isFailed()) {
            // Находим предыдущее пользовательское сообщение
            val userMessage = historyRepository.getHistoryFlow()
                .first()
                .takeWhile { it.id != messageId }
                .lastOrNull { it.role == ChatMessageRole.USER }
                ?: return

            // Удаляем failed сообщение
            historyRepository.deleteMessage(messageId)

            // Повторяем запрос
            sendStreamingMessage(
                model = message.modelId ?: return,
                userMessage = userMessage.content
            ).collect()
        }
    }

    override suspend fun cancelStreaming() {
        // Отмена через scope cancellation в компоненте
        Logger.d("LLMInteractor", "Cancelling streaming")
    }

    companion object {
        const val MAX_CONTEXT_MESSAGES = 20
        const val MIN_MESSAGE_LENGTH = 1
    }

    /**
     * Предоставляет поток со списком моделей, обогащенным состоянием выбора.
     */
    override fun getModels(): Flow<DataResult<List<LlmModel>>> {
        val selectedIdFlow: Flow<String?> = settingsRepository.getSelectedModelId()
        val modelsListFlow: Flow<List<LlmModel>> = modelsRepository.getModelsFlow()
        return combine(
            selectedIdFlow,
            modelsListFlow,
            _refreshError
        ) { selectedId, modelsList, refreshError ->
//            println("${this::class.java.simpleName} getModels: selectedId: $selectedId, modelsList: ${modelsList.size}, refreshError: $refreshError")
            // Эта лямбда будет выполняться каждый раз, когда меняется ID, список моделей или ошибка.
            if (modelsList.isEmpty()) {
                if (refreshError != null) {
                    DataResult.Error(refreshError)
                } else {
                    DataResult.Loading
                }
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
                        DataResult.Error(null, StringHolder.Text("Selected model not found").value)
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
    override suspend fun refreshModels(): Result<Unit> {
        val result = modelsRepository.refreshModels()
        if (result.isFailure) {
            _refreshError.value = result.exceptionOrNull() as Exception?
        } else {
            _refreshError.value = null
        }
        return result
    }

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