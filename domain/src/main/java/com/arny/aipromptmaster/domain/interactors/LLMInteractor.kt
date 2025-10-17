package com.arny.aipromptmaster.domain.interactors

import android.util.Log
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.ApiMessage
import com.arny.aipromptmaster.domain.models.ApiRequestPayload
import com.arny.aipromptmaster.domain.models.ApiRequestWithFiles
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.FileReference
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.StreamChunk
import com.arny.aipromptmaster.domain.models.TokenEstimationResult
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.domain.utils.FileUtils.formatFileSize
import com.arny.aipromptmaster.domain.utils.ITokenEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class LLMInteractor @Inject constructor(
    private val modelsRepository: IOpenRouterRepository,
    private val settingsRepository: ISettingsRepository,
    private val historyRepository: IChatHistoryRepository,
    private val tokenEstimator: ITokenEstimator
) : ILLMInteractor {

    // Определяем максимальное количество сообщений в истории.
    // 20 сообщений (10 пар "вопрос-ответ") - хороший старт.
    // Можно вынести в настройки, если хотите дать пользователю выбор.
    private companion object {
        const val MAX_HISTORY_SIZE = 20
    }

    override suspend fun estimateTokensForCurrentChat(
        inputText: String,
        attachedFiles: List<FileAttachment>,
        conversationId: String?
    ): TokenEstimationResult {
        if (conversationId == null) {
            // Если нет ID чата, используем только ввод и файлы
            return tokenEstimator.estimateTokens(
                inputText = inputText,
                attachedFiles = attachedFiles,
                systemPrompt = null, //  Понять будет ли передаваться системный промпт глобальный или только для уже созданного чата
                chatHistory = emptyList(),
            )
        }

        val systemPrompt = getSystemPrompt(conversationId) // Используем существующий метод
        val chatHistory = historyRepository.getHistoryFlow(conversationId).first() // Получаем текущую историю

        return tokenEstimator.estimateTokens(
            inputText = inputText,
            attachedFiles = attachedFiles,
            systemPrompt = systemPrompt,
            chatHistory = chatHistory,
        )
    }

    override suspend fun createNewConversation(title: String): String {
        return historyRepository.createNewConversation(title)
    }

    override suspend fun setSystemPrompt(conversationId: String, prompt: String) {
        historyRepository.updateSystemPrompt(conversationId, prompt)
    }

    override suspend fun getSystemPrompt(conversationId: String): String? {
        val prompt = historyRepository.getSystemPrompt(conversationId)
        android.util.Log.d("LLMInteractor", "getSystemPrompt for $conversationId: $prompt")
        return prompt
    }

    override suspend fun setSystemPromptWithChatCreation(
        conversationId: String?,
        prompt: String,
        chatTitle: String
    ): String {
        val targetConversationId = conversationId ?: createNewConversation(chatTitle)
        setSystemPrompt(targetConversationId, prompt)
        return targetConversationId
    }

    override suspend fun deleteConversation(conversationId: String) {
        historyRepository.deleteConversation(conversationId)
    }

    override suspend fun toggleModelFavorite(modelId: String) {
        if (settingsRepository.isFavorite(modelId)) {
            settingsRepository.removeFromFavorites(modelId)
        } else {
            settingsRepository.addToFavorites(modelId)
        }
    }

    override fun cancelCurrentRequest() {
        modelsRepository.cancelCurrentRequest()
    }

    override fun getFavoriteModels(): Flow<List<LlmModel>> = getModels()
        .map { result ->
            when (result) {
                is DataResult.Success -> result.data.filter { it.isFavorite }
                else -> emptyList()
            }
        }

// LLMInteractor.kt - ИСПРАВЛЕННАЯ ВЕРСИЯ

    override suspend fun getFullChatForExport(conversationId: String): String {
        val conversation = historyRepository.getConversation(conversationId)
            ?: throw DomainError.local(R.string.dialog_not_found)

        val history = historyRepository.getFullHistory(conversationId)
        val stringBuilder = StringBuilder()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        // --- Заголовок ---
        stringBuilder.append("# Диалог: ${conversation.title}\n\n")
        stringBuilder.append("**Дата экспорта:** ${dateFormat.format(Date())}\n\n")

        // --- Системный промпт ---
        if (!conversation.systemPrompt.isNullOrBlank()) {
            stringBuilder.append("## Системный промпт\n\n")
            stringBuilder.append(conversation.systemPrompt)
            stringBuilder.append("\n\n")
        }

        // --- Прикрепленные файлы (используем ПРАВИЛЬНЫЙ метод) ---
        val attachedFiles = getConversationFiles(conversationId) // ✅ Используем метод интерфейса

        if (attachedFiles.isNotEmpty()) {
            stringBuilder.append("## Прикрепленные файлы\n\n")
            attachedFiles.forEachIndexed { index, file ->
                stringBuilder.append("### Файл ${index + 1}: ${file.fileName}\n\n")
                stringBuilder.append("- **Тип:** ${file.mimeType}\n")
                stringBuilder.append("- **Размер:** ${formatFileSize(file.fileSize)}\n")
                stringBuilder.append("- **Расширение:** ${file.fileExtension}\n\n")
                stringBuilder.append("**Содержимое файла:**\n\n")
                stringBuilder.append("```")
                        stringBuilder.append(file.originalContent)
                        stringBuilder.append("\n```\n\n")
            }
        }

        // --- История диалога ---
        stringBuilder.append("## История диалога\n\n")

        if (history.isEmpty()) {
            stringBuilder.append("*Сообщений нет.*\n\n")
        } else {
            history.forEach { message ->
                val role = when (message.role) {
                    ChatRole.USER -> "👤 Пользователь"
                    ChatRole.ASSISTANT -> "🤖 Ассистент"
                    ChatRole.SYSTEM -> "⚙️ Система"
                }

                stringBuilder.append("### $role\n\n")
                stringBuilder.append("${message.content}\n\n")
            }
        }

        return stringBuilder.toString()
    }


    override suspend fun addUserMessageToHistory(conversationId: String, userMessage: String) {
        // Проверяем, существует ли диалог, и создаем его при необходимости
        val conversation = historyRepository.getConversation(conversationId)
        if (conversation == null) {
            // Создаем новый диалог с текстом сообщения как заголовком
            val title = userMessage.take(50).ifEmpty { "Новый чат" }
            historyRepository.createNewConversation(title)
        }

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
     *  Регенерация последнего ответа
     */
    override suspend fun regenerateLastResponse(
        model: String,
        conversationId: String
    ) {
        // 1. Получаем последнее сообщение
        val history = historyRepository.getHistoryFlow(conversationId).first()
        val lastMessage = history.lastOrNull()

        // 2. Проверяем, что это сообщение ассистента
        if (lastMessage?.role != ChatRole.ASSISTANT) {
            throw DomainError.Local("Последнее сообщение не от ассистента")
        }

        // 3. Удаляем последнее сообщение ассистента
        historyRepository.deleteMessage(lastMessage.id)

        // 4. Отправляем запрос заново (без добавления user message)
        sendMessage(model, conversationId, 0)
    }

    /**
     * Регенерация ЛЮБОГО сообщения в истории
     * @param messageId ID сообщения которое нужно регенерировать
     */
    override suspend fun regenerateMessage(
        model: String,
        conversationId: String,
        messageId: String
    ) {
        val history = historyRepository.getHistoryFlow(conversationId).first()

        // 1. Находим сообщение для регенерации
        val messageIndex = history.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) {
            throw DomainError.Local("Сообщение не найдено")
        }

        val messageToRegenerate = history[messageIndex]

        // 2. Проверяем, что это сообщение ассистента
        if (messageToRegenerate.role != ChatRole.ASSISTANT) {
            throw DomainError.Local("Можно регенерировать только ответы ассистента") // Тут исправить, иконка стоит рядом с каждым сообщением,но регерируется только ответ
        }

        // 3. Удаляем все сообщения ПОСЛЕ (включая само сообщение)
        val messagesToDelete = history.subList(messageIndex, history.size)
        messagesToDelete.forEach { msg ->
            historyRepository.deleteMessage(msg.id)
        }

        // 4. Отправляем запрос заново
        sendMessage(model, conversationId, 0)
    }

    // РЕАЛИЗАЦИЯ МЕТОДОВ ДЛЯ ФАЙЛОВ ЧАТА
    override suspend fun addFileToConversation(conversationId: String, file: FileAttachment) {
        historyRepository.addFileToConversation(conversationId, file)
    }

    override suspend fun removeFileFromConversation(conversationId: String, fileId: String) {
        historyRepository.removeFileFromConversation(conversationId, fileId)
    }

    override suspend fun getConversationFiles(conversationId: String): List<FileAttachment> {
        return historyRepository.getConversationFiles(conversationId)
    }

    override fun getConversationFilesFlow(conversationId: String): Flow<List<FileAttachment>> {
        return historyRepository.getConversationFilesFlow(conversationId)
    }

    /**
     * Создание пустого сообщения ассистента
     */
    override suspend fun createAssistantMessage(conversationId: String): String {
        val assistantMessage = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = ""
        )
        return historyRepository.addMessage(conversationId, assistantMessage)
    }

    /**
     * Streaming с файлами
     * Использует специальный формат API для файловых вложений
     */
    private suspend fun runStreamingWithFiles(
        model: String,
        payload: ApiRequestPayload,
        apiKey: String,
        messageId: String
    ) {
        var receivedAnyData = false
        var streamError: DomainError? = null
        val startTime = System.currentTimeMillis()

        // Устанавливаем состояние "думает"
        historyRepository.updateMessageThinkingState(messageId, isThinking = true, thinkingTime = null)

        val requestWithFiles = ApiRequestWithFiles(
            model = model,
            messages = payload.messages,
            files = payload.attachedFiles.map { file ->
                FileReference(
                    id = file.id,
                    name = file.fileName,
                    content = file.originalContent,
                    mimeType = file.mimeType
                )
            }
        )

        modelsRepository.getChatCompletionStreamWithFiles(requestWithFiles, apiKey)
            .onCompletion { cause ->
                val thinkingTime = System.currentTimeMillis() - startTime

                if (cause != null) {
                    streamError = when (cause) {
                        is DomainError -> cause
                        else -> DomainError.Generic(cause.localizedMessage ?: "Unknown flow error")
                    }
                    historyRepository.updateMessageThinkingState(messageId, isThinking = false, thinkingTime = null)
                    return@onCompletion
                }

                if (!receivedAnyData && streamError == null) {
                    streamError = DomainError.Generic("Stream completed without emitting any data.")
                }

                historyRepository.updateMessageThinkingState(messageId, isThinking = false, thinkingTime = thinkingTime)
            }
            .collect { result ->
                when (result) {
                    is DataResult.Success -> {
                        historyRepository.appendContentToMessage(messageId, result.data)
                        receivedAnyData = true
                    }
                    is DataResult.Error -> {
                        val exception = result.exception
                        streamError = when (exception) {
                            is DomainError -> exception
                            null -> DomainError.Generic("Stream returned an error with no exception details")
                            else -> DomainError.Generic(
                                exception.localizedMessage ?: "Unknown stream error"
                            )
                        }
                    }
                    DataResult.Loading -> { /* Игнорируем */ }
                }
            }

        streamError?.let { throw it }
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
        val favoriteModelIds = settingsRepository.getFavoriteModelIds()
        return combine(
            selectedIdFlow,
            modelsListFlow,
            favoriteModelIds
        ) { selectedId, modelsList, favoriteModelIds ->
            if (modelsList.isEmpty()) {
                DataResult.Loading
            } else {
                val mappedList = modelsList.map { model ->
                    model.copy(
                        isSelected = model.id == selectedId,
                        isFavorite = model.id in favoriteModelIds
                    )
                }
                DataResult.Success(mappedList)
            }
        }.onStart { emit(DataResult.Loading) } // Начинаем с Loading в любом случае.
    }

    /**
     * Возвращает реактивный поток с деталями только одной выбранной модели.
     */
    override fun getSelectedModel(): Flow<DataResult<LlmModel>> = getModels().map { dataResult ->
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

    /**
     * метод отправки сообщения
     */
    override suspend fun sendMessage(
        model: String,
        conversationId: String,
        estimatedTokens: Int
    ) {
        val apiKey = settingsRepository.getApiKey()?.trim()
            ?: throw DomainError.Local("API ключ не указан")

        // 1. Создаем пустое сообщение ассистента
        val assistantMessageId = createAssistantMessage(conversationId)

        try {
            // 2. Получаем системный промпт
            val systemPrompt = historyRepository.getSystemPrompt(conversationId)

            // 3. Строим payload (файлы автоматически из чата)
            val payload = buildMessagesForApi(conversationId, systemPrompt)

            // 4. Отправляем
            if (payload.attachedFiles.isNotEmpty()) {
                runStreamingWithFiles(
                    model = model,
                    payload = payload,
                    apiKey = apiKey,
                    messageId = assistantMessageId
                )
            } else {
                runStreamingAttempt(
                    model = model,
                    messages = payload.messages,
                    apiKey = apiKey,
                    messageId = assistantMessageId,
                    estimatedTokens = estimatedTokens
                )
            }
        } catch (e: Exception) {
            historyRepository.deleteMessage(assistantMessageId)
            throw e
        }
    }

    /**
     * Построение сообщений для API
     * Файлы берутся из ЧАТА, а не из отдельных сообщений
     */
    private suspend fun buildMessagesForApi(
        conversationId: String,
        systemPrompt: String?
    ): ApiRequestPayload {
        // 1. Получаем историю сообщений (БЕЗ файлов)
        val history = historyRepository.getHistoryFlow(conversationId)
            .first()
            .takeLast(MAX_HISTORY_SIZE)

        val messagesForApi = mutableListOf<ApiMessage>()

        // 2. Системный промпт ВСЕГДА первым
        systemPrompt?.let {
            messagesForApi.add(ApiMessage(role = "system", content = it))
        }

        // 3. Получаем файлы ЧАТА (НЕ из сообщений!)
        val attachedFiles = historyRepository.getConversationFiles(conversationId)

        // 4. Если есть файлы, добавляем system message с инструкцией
        if (attachedFiles.isNotEmpty()) {
            val filesInstruction = buildString {
                append("📋 **Context Files**\n\n")
                append("The user has ${attachedFiles.size} file(s) available for reference:\n\n")

                attachedFiles.forEachIndexed { index, file ->
                    append("--- FILE ${index + 1}: ${file.fileName} ---\n")
                    append("Type: ${file.mimeType}\n")
                    append("Size: ${formatFileSize(file.fileSize)}\n")
                    append("Content:\n${file.originalContent}\n")
                    append("--- END OF FILE ${index + 1} ---\n\n")
                }

                append("Use these files to answer user questions.\n")
            }

            messagesForApi.add(ApiMessage(role = "system", content = filesInstruction))
        }

        // 5. Добавляем историю сообщений (чистые, БЕЗ упоминаний файлов)
        for (message in history) {
            messagesForApi.add(
                ApiMessage(
                    role = message.role.toApiRole(),
                    content = message.content
                )
            )
        }

        return ApiRequestPayload(
            messages = messagesForApi,
            attachedFiles = attachedFiles
        )
    }

    private suspend fun runStreamingAttempt(
        model: String,
        messages: List<ApiMessage>,
        apiKey: String,
        messageId: String,
        estimatedTokens: Int
    ) {
        var receivedAnyData = false
        var streamError: DomainError? = null
        var actualPromptTokens: Int? = null
        val startTime = System.currentTimeMillis()

        // Включаем индикатор "думает"
        historyRepository.updateMessageThinkingState(messageId, isThinking = true, thinkingTime = null)

        try {
            val chatMessages = messages.map { apiMsg ->
                ChatMessage(
                    role = ChatRole.fromApiRole(apiMsg.role),
                    content = apiMsg.content
                )
            }

            modelsRepository.getChatCompletionStream(model, chatMessages, apiKey)
                .onCompletion { cause ->
                    val thinkingTime = System.currentTimeMillis() - startTime

                    if (cause != null) {
                        streamError = when (cause) {
                            is DomainError -> cause
                            else -> DomainError.Generic(cause.localizedMessage ?: "Unknown flow error")
                        }
                        historyRepository.updateMessageThinkingState(messageId, isThinking = false, thinkingTime = null)
                        return@onCompletion
                    }

                    if (!receivedAnyData && streamError == null) {
                        streamError = DomainError.Generic("Stream completed without emitting any data.")
                    }

                    // Калибруем токены, если получили реальные данные
                    actualPromptTokens?.let { actual ->
                        if (estimatedTokens > 0) {
                            tokenEstimator.adjustTokenRatio(actual, estimatedTokens)
                            Log.d("TokenCalibration", "Estimated: $estimatedTokens, Actual: $actual, New Ratio: ${tokenEstimator.getCurrentRatio()}")
                        }
                    }

                    historyRepository.updateMessageThinkingState(messageId, isThinking = false, thinkingTime = thinkingTime)
                }
                .collect { result ->
                    when (result) {
                        is DataResult.Success -> {
                            when (val chunk = result.data) {
                                is StreamChunk.Content -> {
                                    historyRepository.appendContentToMessage(messageId, chunk.text)
                                    receivedAnyData = true
                                }
                                is StreamChunk.Usage -> {
                                    // Сохраняем реальное количество токенов
                                    actualPromptTokens = chunk.promptTokens
                                    Log.d("TokenUsage", "Prompt: ${chunk.promptTokens}, Completion: ${chunk.completionTokens}")
                                }
                            }
                        }
                        is DataResult.Error -> {
                            val exception = result.exception
                            streamError = when (exception) {
                                is DomainError -> exception
                                null -> DomainError.Generic("Stream returned an error with no exception details")
                                else -> DomainError.Generic(
                                    exception.localizedMessage ?: "Unknown stream error"
                                )
                            }
                        }
                        DataResult.Loading -> { }
                    }
                }

            streamError?.let { throw it }
        } catch (e: Exception) {
            historyRepository.updateMessageThinkingState(messageId, isThinking = false, thinkingTime = null)
            throw e
        }
    }

}
