package com.arny.aipromptmaster.domain.repositories

import com.arny.aipromptmaster.domain.models.ApiRequestWithFiles
import com.arny.aipromptmaster.domain.models.ChatCompletionResponse
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.StreamResult
import kotlinx.coroutines.flow.Flow

interface IOpenRouterRepository {
    /**
     * Предоставляет реактивный поток со списком всех доступных моделей.
     * Этот поток можно слушать, чтобы получать обновления.
     */
    fun getModelsFlow(): Flow<List<LlmModel>>

    /**
     * Запускает принудительное обновление списка моделей из сети.
     * Возвращает Result, чтобы можно было отследить успех/ошибку операции.
     */
    suspend fun refreshModels(): Result<Unit>

    /**
     * Streaming чата с опциональными файлами.
     * Возвращает StreamResult, который содержит content и modelId (если API его вернул).
     *
     * @param model ID модели
     * @param messages Список сообщений
     * @param apiKey API ключ
     * @param attachedFiles Прикрепленные файлы для последнего сообщения
     * @param llmModel Данные о модели (для проверки multimodal возможностей)
     */
    fun getChatCompletionStream(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String,
        attachedFiles: List<FileAttachment>,
        llmModel: LlmModel? = null
    ): Flow<DataResult<StreamResult>>

    /**
     * Streaming с файлами
     * Отправляет файлы в отдельном поле запроса
     */
    fun getChatCompletionStreamWithFiles(
        request: ApiRequestWithFiles,
        apiKey: String
    ): Flow<DataResult<StreamResult>>

    /**
     * Отменяет текущий активный запрос к API
     */
    fun cancelCurrentRequest()
}
