package com.arny.aiprompts.domain.interactors

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.MessageStatus
import com.arny.aiprompts.data.model.ApiException
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

/**
 * Основной бизнес‑логический слой взаимодействия с LLM‑сервисом.
 *
 * @property modelsRepository Репозиторий моделей OpenRouter.
 * @property settingsRepository Репозиторий настроек приложения (API‑ключ, выбранная модель и т.п.).
 * @property historyRepository Репозиторий истории чата.
 */
class LLMInteractor(
    private val modelsRepository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository,
    private val historyRepository: IChatHistoryRepository
) : ILLMInteractor {

    /** Хранит ошибку, возникшую при принудительном обновлении списка моделей. */
    private val _refreshError = MutableStateFlow<Exception?>(null)

    /**
     * Возвращает поток с полной историей чата.
     *
     * @return Поток, публикующий список всех сообщений.
     */
    override fun getChatHistoryFlow(): Flow<List<ChatMessage>> = historyRepository.getHistoryFlow()

    /**
     * Очищает историю чата в репозитории.
     */
    override suspend fun clearChat() {
        historyRepository.clearHistory()
    }

    /**
     * Отправляет сообщение модели с потоковой обработкой ответа.
     *
     * Сначала сохраняется пользовательское сообщение, затем создаётся заглушка для ответа,
     * после чего начинается потоковое получение данных от API. Каждое полученное
     * «частичное» сообщение обновляется в истории и публикуется как `DataResult.Success`.
     *
     * @param model Идентификатор модели.
     * @param userMessage Текст пользовательского сообщения.
     * @return Поток, публикующий `Success` с частями ответа или `Error` при сбое.
     */
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
                        if (chunk.content.isNotEmpty()) {
                            accumulatedContent.append(chunk.content)
                        }
                        val updatedMsg = modelMsg.copy(
                            content = accumulatedContent.toString(),
                            status = if (chunk.isComplete) MessageStatus.Sent else MessageStatus.Streaming(false)
                        )
                        historyRepository.updateMessage(updatedMsg)
                        emit(DataResult.Success(updatedMsg))
                    },
                    onFailure = { error ->
                        Logger.e(error, "LLMInteractor", "Streaming failed")
                        val failedMsg = modelMsg.copy(
                            content = accumulatedContent.toString(),
                            status = MessageStatus.Failed(error.message ?: "Unknown error")
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

    /**
     * Формирует контекст из истории чата для последующего запроса к модели.
     *
     * Возвращает только сообщения с состоянием `Sent` и ограничивает их
     * до `MAX_CONTEXT_MESSAGES` последних элементов.
     *
     * @return Список сообщений‑контекста.
     */
    private suspend fun buildApiContext(): List<ChatMessage> {
        return historyRepository.getHistoryFlow()
            .first()
            .filter { it.status is MessageStatus.Sent }
            .takeLast(MAX_CONTEXT_MESSAGES)
    }

    /**
     * Повторяет отправку сообщения модели, если предыдущее завершилось с ошибкой.
     *
     * Принимает ID сообщения‑ошибки, ищет связанное пользовательское сообщение,
     * удаляет `Failed`‑сообщение и инициирует новый потоковой запрос.
     *
     * @param messageId Идентификатор сообщения модели с ошибкой.
     */
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

    /**
     * Отмена потоковой операции (вызывается из UI‑компонента).
     *
     * Реализация зависит от того, как управляется `CoroutineScope` в компоненте.
     */
    override suspend fun cancelStreaming() {
        Logger.d("LLMInteractor", "Cancelling streaming")
    }

    /**
     * Возвращает поток с обновляемым списком моделей OpenRouter,
     * где каждая модель помечена флагом `isSelected`.
     *
     * @return Поток, публикующий `DataResult` со списком моделей.
     */
    override fun getModels(): Flow<DataResult<List<LlmModel>>> {
        val selectedIdFlow: Flow<String?> = settingsRepository.getSelectedModelId()
        val modelsListFlow: Flow<List<LlmModel>> = modelsRepository.getModelsFlow()
        return combine(
            selectedIdFlow,
            modelsListFlow,
            _refreshError
        ) { selectedId, modelsList, refreshError ->
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
        }.onStart { emit(DataResult.Loading) }
    }

    /**
     * Возвращает поток с деталями только выбранной модели.
     *
     * @return Поток, публикующий `DataResult` с моделью либо ошибкой/загрузкой.
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
     * Сохраняет выбранный пользователем ID модели в репозитории настроек.
     *
     * @param id Идентификатор модели, которую нужно выбрать.
     */
    override suspend fun selectModel(id: String) {
        settingsRepository.setSelectedModelId(id)
    }

    /**
     * Запускает принудительное обновление списка моделей у сервиса OpenRouter.
     *
     * При ошибке сохраняется в `_refreshError`, чтобы UI могло отобразить её.
     *
     * @return `Result` с `Unit` при успехе или ошибкой.
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
     * Обрабатывает клик по элементу модели в списке.
     *
     * Если пользователь нажал на уже выбранную модель – снимает выбор,
     * иначе выбирает новую модель, обновляя состояние в репозитории настроек.
     *
     * @param clickedModelId ID модели, по которой был произведён клик.
     */
    override suspend fun toggleModelSelection(clickedModelId: String) {
        val currentlySelectedId = settingsRepository.getSelectedModelId().firstOrNull()
        if (currentlySelectedId == clickedModelId) {
            settingsRepository.setSelectedModelId(null)
        } else {
            settingsRepository.setSelectedModelId(clickedModelId)
        }
    }

    companion object {
        /** Максимальное число сообщений, которые будут отправлены в один запрос к модели. */
        const val MAX_CONTEXT_MESSAGES = 20

        /** Минимальная длина пользовательского сообщения. */
        const val MIN_MESSAGE_LENGTH = 1
    }
}
