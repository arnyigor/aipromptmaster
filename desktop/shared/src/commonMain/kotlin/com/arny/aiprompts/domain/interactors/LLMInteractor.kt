@file:OptIn(ExperimentalTime::class)

package com.arny.aiprompts.domain.interactors

import com.arny.aiprompts.data.model.AttachmentType
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.MessageAttachment
import com.arny.aiprompts.data.model.MessageStatus
import com.arny.aiprompts.data.model.ApiException
import com.arny.aiprompts.data.repositories.IChatHistoryRepository
import com.arny.aiprompts.data.repositories.IChatSessionRepository
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import com.arny.aiprompts.domain.files.PlatformFileHandler
import com.arny.aiprompts.domain.strings.StringHolder
import com.arny.aiprompts.results.DataResult
import com.arny.aiprompts.utils.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Основной бизнес‑логический слой взаимодействия с LLM‑сервисом.
 *
 * ОБНОВЛЕНИЯ:
 * - Поддержка множественных сессий чата с persistence в Room
 * - System prompt для каждой сессии + глобальный User Context
 * - Настройки генерации (temperature, maxTokens и т.д.)
 * - Улучшенная обработка ошибок
 * - Поддержка кастомных Base URL (LMStudio, Ollama и др.)
 * - Поддержка вложений (изображения, текстовые файлы)
 *
 * @property modelsRepository Репозиторий моделей OpenRouter.
 * @property settingsRepository Репозиторий настроек приложения.
 * @property chatSessionRepository Репозиторий сессий чата.
 * @property historyRepository Репозиторий истории чата (для обратной совместимости).
 * @property fileHandler Обработчик файлов для чтения вложений.
 */
class LLMInteractor(
    private val modelsRepository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository,
    private val chatSessionRepository: IChatSessionRepository,
    private val historyRepository: IChatHistoryRepository,
    private val fileHandler: PlatformFileHandler
) : ILLMInteractor {

    /** Хранит ошибку, возникшую при принудительном обновлении списка моделей. */
    private val _refreshError = MutableStateFlow<Exception?>(null)

    /** Текущая задача генерации для возможности отмены. */
    private var currentStreamingJob: kotlinx.coroutines.Job? = null

    // ==================== Модели ====================

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

    override suspend fun selectModel(id: String) {
        settingsRepository.setSelectedModelId(id)
    }

    override suspend fun refreshModels(): Result<Unit> {
        val result = modelsRepository.refreshModels()
        if (result.isFailure) {
            _refreshError.value = result.exceptionOrNull() as Exception?
        } else {
            _refreshError.value = null
        }
        return result
    }

    override suspend fun toggleModelSelection(clickedModelId: String) {
        val currentlySelectedId = settingsRepository.getSelectedModelId().firstOrNull()
        if (currentlySelectedId == clickedModelId) {
            settingsRepository.setSelectedModelId(null)
        } else {
            settingsRepository.setSelectedModelId(clickedModelId)
        }
    }

    // ==================== Сессии чата ====================

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return chatSessionRepository.getAllSessions()
    }

    override fun getSessionById(sessionId: String): Flow<ChatSession?> {
        return chatSessionRepository.getSessionById(sessionId)
    }

    override suspend fun createSession(name: String, systemPrompt: String?): ChatSession {
        return chatSessionRepository.createSession(name, systemPrompt)
    }

    override suspend fun deleteSession(sessionId: String) {
        chatSessionRepository.deleteSession(sessionId)
    }

    override suspend fun archiveSession(sessionId: String) {
        chatSessionRepository.archiveSession(sessionId)
    }

    override suspend fun renameSession(sessionId: String, newName: String) {
        chatSessionRepository.renameSession(sessionId, newName)
    }

    override suspend fun updateSystemPrompt(sessionId: String, systemPrompt: String?) {
        chatSessionRepository.updateSystemPrompt(sessionId, systemPrompt)
    }

    override suspend fun updateChatSettings(sessionId: String, settings: ChatSettings) {
        chatSessionRepository.updateSession(
            chatSessionRepository.getSessionById(sessionId).firstOrNull()?.copy(settings = settings)
                ?: return
        )
    }

    // ==================== Сообщения ====================

    override fun sendMessage(sessionId: String, content: String): Flow<DataResult<ChatMessage>> = flow {
        check(currentStreamingJob?.isActive != true) { "Дождитесь завершения ответа" }
        // Валидация
        if (content.length < MIN_MESSAGE_LENGTH) {
            emit(DataResult.Error(IllegalArgumentException("Message too short")))
            return@flow
        }

        // Получаем API ключ
        val apiKey = settingsRepository.getOpenRouterApiKey()?.trim()
        if (apiKey.isNullOrEmpty() && settingsRepository.loadProviders().active.requiresKey) {
            emit(DataResult.Error(ApiException.MissingApiKey()))
            return@flow
        }

        // Получаем сессию и модель
        val session = chatSessionRepository.getSessionById(sessionId).firstOrNull()
            ?: run {
                emit(DataResult.Error(IllegalStateException("Session not found")))
                return@flow
            }

        val modelId = session.modelId ?: settingsRepository.getSelectedModelId().firstOrNull()
            ?: run {
                emit(DataResult.Error(IllegalStateException("No model selected")))
                return@flow
            }

        // 1. Создаем и сохраняем сообщение пользователя
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatMessageRole.USER,
            content = content,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            status = MessageStatus.Sent
        )
        chatSessionRepository.addMessage(sessionId, userMsg)

        emitResponse(session, modelId)
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(DataResult.Error(e))
    }

    override fun sendMessageWithAttachments(sessionId: String, input: MessageInput): Flow<DataResult<ChatMessage>> = flow {
        check(currentStreamingJob?.isActive != true) { "Дождитесь завершения ответа" }
        // Валидация
        if (input.text.length < MIN_MESSAGE_LENGTH && input.attachments.isEmpty()) {
            emit(DataResult.Error(IllegalArgumentException("Message too short")))
            return@flow
        }

        // Получаем API ключ
        val apiKey = settingsRepository.getOpenRouterApiKey()?.trim()
        if (apiKey.isNullOrEmpty() && settingsRepository.loadProviders().active.requiresKey) {
            emit(DataResult.Error(ApiException.MissingApiKey()))
            return@flow
        }

        // Получаем сессию и модель
        val session = chatSessionRepository.getSessionById(sessionId).firstOrNull()
            ?: run {
                emit(DataResult.Error(IllegalStateException("Session not found")))
                return@flow
            }

        val modelId = session.modelId ?: settingsRepository.getSelectedModelId().firstOrNull()
            ?: run {
                emit(DataResult.Error(IllegalStateException("No model selected")))
                return@flow
            }

        // 1. Сохраняем вложения во внутреннее хранилище
        val savedAttachments = mutableListOf<MessageAttachment>()
        for (attachment in input.attachments) {
            try {
                val mimeType = attachment.mimeType ?: fileHandler.getMimeType(attachment.uri) ?: "application/octet-stream"
                val internalUri = fileHandler.copyToInternalStorage(attachment.uri, "${UUID.randomUUID()}_${attachment.fileName}")
                val size = fileHandler.getFileSize(attachment.uri)
                
                savedAttachments.add(
                    MessageAttachment(
                        id = UUID.randomUUID().toString(),
                        type = detectAttachmentType(mimeType),
                        uri = internalUri,
                        fileName = attachment.fileName,
                        mimeType = mimeType,
                        size = size,
                        createdAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalStateException("Не удалось прочитать вложение: ${attachment.fileName}", e)
            }
        }

        // 2. Формируем контент: текст + текстовые файлы
        val fullContent = buildString {
            if (input.text.isNotBlank()) {
                append(input.text)
            }
            
            // Для текстовых файлов - читаем содержимое и добавляем к сообщению
            savedAttachments
                .filter { it.type == AttachmentType.TEXT_FILE || it.type == AttachmentType.CODE }
                .forEach { file ->
                    if (isNotEmpty()) append("\n\n")
                    append("--- File: ${file.fileName} ---\n")
                    try {
                        append(fileHandler.readText(file.uri))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        throw IllegalStateException("Не удалось прочитать текст: ${file.fileName}", e)
                    }
                    append("\n---")
                }
        }

        // 3. Создаем и сохраняем сообщение пользователя с вложениями
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatMessageRole.USER,
            content = fullContent,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            status = MessageStatus.Sent,
            attachments = savedAttachments
        )
        chatSessionRepository.addMessage(sessionId, userMsg)

        emitResponse(session, modelId)
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(DataResult.Error(e))
    }

    // Both text and attachments use the same response lifecycle as the Android chat.
    private suspend fun kotlinx.coroutines.flow.FlowCollector<DataResult<ChatMessage>>.emitResponse(
        session: ChatSession, modelId: String
    ) {
        val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        check(currentStreamingJob == null || currentStreamingJob?.isActive != true) { "Дождитесь завершения ответа" }
        currentStreamingJob = job
        val placeholder = ChatMessage(role = ChatMessageRole.MODEL, content = "",
            status = MessageStatus.Streaming(false), modelId = modelId)
        val content = StringBuilder()
        var lastWrite = System.nanoTime()
        try {
            chatSessionRepository.addMessage(session.id, placeholder)
            emit(DataResult.Success(placeholder))
            val context = buildApiContext(session.id, session.systemPrompt, session.settings.contextWindow)
            modelsRepository.getStreamingChatCompletion(modelId, context,
                temperature = session.settings.temperature.toDouble(), maxTokens = session.settings.maxTokens,
                topP = session.settings.topP.toDouble()).collect { result ->
                val chunk = result.getOrThrow()
                content.append(chunk.content)
                val now = System.nanoTime()
                if (chunk.isComplete || now - lastWrite >= 300_000_000L) {
                    val updated = placeholder.copy(content = content.toString(),
                        status = if (chunk.isComplete) MessageStatus.Sent else MessageStatus.Streaming(false))
                    chatSessionRepository.updateMessage(updated)
                    emit(DataResult.Success(updated))
                    lastWrite = now
                }
            }
            if (content.isBlank()) chatSessionRepository.deleteMessage(placeholder.id)
            else {
                val completed = placeholder.copy(content = content.toString(), status = MessageStatus.Sent)
                chatSessionRepository.updateMessage(completed)
                emit(DataResult.Success(completed))
            }
        } catch (e: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                if (content.isBlank()) chatSessionRepository.deleteMessage(placeholder.id)
                else chatSessionRepository.updateMessage(placeholder.copy(content = content.toString(),
                    status = if (e is CancellationException) MessageStatus.Sent
                    else MessageStatus.Failed(e.message ?: "Ошибка генерации")))
            }
            throw e
        } finally {
            if (currentStreamingJob === job) currentStreamingJob = null
        }
    }

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatSessionRepository.getMessagesForSession(sessionId)
    }

    override fun getChatHistoryFlow(): Flow<List<ChatMessage>> {
        return historyRepository.getHistoryFlow()
    }

    override suspend fun clearChat(sessionId: String?) {
        if (sessionId != null) {
            chatSessionRepository.clearSessionMessages(sessionId)
        } else {
            historyRepository.clearHistory()
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        chatSessionRepository.deleteMessage(messageId)
    }

    private suspend fun locateMessage(id: String): Pair<ChatSession, List<ChatMessage>> {
        for (session in chatSessionRepository.getAllSessions().first()) {
            val messages = chatSessionRepository.getMessagesForSession(session.id).first()
            if (messages.any { it.id == id }) return session to messages
        }
        error("Сообщение не найдено")
    }

    override suspend fun retryMessage(messageId: String) {
        val (session, messages) = locateMessage(messageId)
        val index = messages.indexOfFirst { it.id == messageId }
        val userIndex = if (messages[index].role == ChatMessageRole.USER) index
            else (index - 1 downTo 0).firstOrNull { messages[it].role == ChatMessageRole.USER }
                ?: error("Исходное сообщение не найдено")
        val modelId = session.modelId ?: settingsRepository.getSelectedModelId().firstOrNull()
            ?: error("Выберите модель")
        messages.drop(userIndex + 1).forEach { chatSessionRepository.deleteMessage(it.id) }
        flow<DataResult<ChatMessage>> { emitResponse(session, modelId) }.collect {
            if (it is DataResult.Error) throw it.exception ?: IllegalStateException("Ошибка генерации")
        }
    }

    override suspend fun editMessage(messageId: String, newContent: String): Flow<DataResult<ChatMessage>> = flow {
        require(newContent.isNotBlank()) { "Введите сообщение" }
        val (session, messages) = locateMessage(messageId)
        val index = messages.indexOfFirst { it.id == messageId }
        val original = messages[index]
        require(original.role == ChatMessageRole.USER) { "Редактировать можно сообщение пользователя" }
        val modelId = session.modelId ?: settingsRepository.getSelectedModelId().firstOrNull()
            ?: error("Выберите модель")
        chatSessionRepository.updateMessage(original.copy(content = newContent,
            editedAt = Clock.System.now().toEpochMilliseconds(), previousVersions = original.previousVersions + original.content))
        messages.drop(index + 1).forEach { chatSessionRepository.deleteMessage(it.id) }
        emitResponse(session, modelId)
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(DataResult.Error(e))
    }

    override suspend fun cancelStreaming() {
        currentStreamingJob?.cancel()
        currentStreamingJob = null
        Logger.d("LLMInteractor", "Streaming cancelled")
    }

    // ==================== Private Methods ====================

    /**
     * Формирует контекст из истории чата для запроса к модели.
     * Включает:
     * 1. System prompt сессии (если задан)
     * 2. Глобальный User Context (если задан)
     * 3. Историю сообщений
     *
     * @param sessionId ID сессии
     * @param sessionSystemPrompt System prompt сессии (может быть null)
     * @return Список сообщений для API
     */
    private suspend fun buildApiContext(sessionId: String, sessionSystemPrompt: String?, contextWindow: Int): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        
        // Формируем итоговый системный промпт
        val finalSystemPrompt = buildFinalSystemPrompt(sessionSystemPrompt)
        
        // Добавляем системный промпт если получился непустой
        finalSystemPrompt?.takeIf { it.isNotBlank() }?.let {
            messages.add(
                ChatMessage(
                    id = "system",
                    role = ChatMessageRole.SYSTEM,
                    content = it,
                    timestamp = 0,
                    status = MessageStatus.Sent
                )
            )
        }
        
        // Добавляем историю сообщений
        val history = chatSessionRepository.getRecentMessagesForContext(sessionId, contextWindow.coerceIn(1, MAX_CONTEXT_MESSAGES) + 1)
            .filter { it.status is MessageStatus.Sent }
            .takeLast(contextWindow.coerceIn(1, MAX_CONTEXT_MESSAGES))
        
        messages.addAll(history)
        
        return messages
    }

    /**
     * Собирает финальный системный промпт из:
     * - System prompt сессии
     * - Глобального User Context
     */
    private fun buildFinalSystemPrompt(sessionSystemPrompt: String?): String? {
        val userContext = settingsRepository.getUserContext()
        
        // Если ничего не задано, возвращаем null
        if (sessionSystemPrompt.isNullOrBlank() && userContext.isBlank()) {
            return null
        }
        
        return buildString {
            // Добавляем системный промпт сессии
            sessionSystemPrompt?.takeIf { it.isNotBlank() }?.let {
                append(it)
            }
            
            // Добавляем User Context
            userContext.takeIf { it.isNotBlank() }?.let { context ->
                if (isNotEmpty()) {
                    append("\n\n---\n\n")
                }
                append("CONTEXT ABOUT USER:\n")
                append(context)
                append("\n\nINSTRUCTIONS:\n")
                append("Always consider the user context above when answering. " +
                       "Adapt your responses to match the user's background, preferences, and communication style.")
            }
        }
    }

    companion object {
        /** Максимальное число сообщений, которые будут отправлены в один запрос к модели. */
        const val MAX_CONTEXT_MESSAGES = 20

        /** Минимальная длина пользовательского сообщения. */
        const val MIN_MESSAGE_LENGTH = 1

        /**
         * Определяет тип вложения на основе MIME типа.
         */
        private fun detectAttachmentType(mimeType: String): AttachmentType {
            return when {
                mimeType.startsWith("image/") -> AttachmentType.IMAGE
                mimeType.startsWith("text/") -> AttachmentType.TEXT_FILE
                mimeType in listOf("application/json", "application/xml") || 
                     mimeType.contains("kotlin") || 
                     mimeType.contains("javascript") ||
                     mimeType.contains("python") -> AttachmentType.CODE
                else -> AttachmentType.DOCUMENT
            }
        }
    }
}
