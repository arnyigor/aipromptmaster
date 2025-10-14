package com.arny.aipromptmaster.domain.interactors

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
    private val fileRepository: IFileRepository,
    private val tokenEstimator: ITokenEstimator
) : ILLMInteractor {

    // Определяем максимальное количество сообщений в истории.
    // 20 сообщений (10 пар "вопрос-ответ") - хороший старт.
    // Можно вынести в настройки, если хотите дать пользователю выбор.
    private companion object {
        const val MAX_HISTORY_SIZE = 20
        const val FILE_PREVIEW_LENGTH = 500  // Максимальная длина превью файла
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

    override suspend fun getFullChatForExport(conversationId: String): String {
        val conversation = historyRepository.getConversation(conversationId)
            ?: throw DomainError.local(R.string.dialog_not_found) // Или вернуть строку с ошибкой

        val history = historyRepository.getFullHistory(conversationId)

        val stringBuilder = StringBuilder()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        // --- Заголовок ---
        stringBuilder.append("# Диалог: ${conversation.title}\n\n")
        stringBuilder.append("**Дата экспорта:** ${dateFormat.format(Date())}\n\n")

        // --- Системный промпт (ВАЖНО: в начале) ---
        if (!conversation.systemPrompt.isNullOrBlank()) {
            stringBuilder.append("## Системный промпт\n\n")
            stringBuilder.append(conversation.systemPrompt)
            stringBuilder.append("\n\n")
        }

        // --- Прикрепленные файлы ---
        val attachedFiles = mutableListOf<FileAttachment>()
        val fileIds = mutableSetOf<String>()

        // Собираем уникальные fileId из истории сообщений
        history.forEach { message ->
            message.fileAttachment?.let { metadata ->
                if (!fileIds.contains(metadata.fileId)) {
                    fileRepository.getTemporaryFile(metadata.fileId)?.let { fullFile ->
                        attachedFiles.add(fullFile)
                        fileIds.add(metadata.fileId)
                    }
                }
            }
        }

        if (attachedFiles.isNotEmpty()) {
            stringBuilder.append("## Прикрепленные файлы\n\n")
            attachedFiles.forEachIndexed { index, file ->
                stringBuilder.append("### Файл ${index + 1}: ${file.fileName}\n\n")
                stringBuilder.append("- **Тип:** ${file.mimeType}\n")
                stringBuilder.append("- **Размер:** ${formatFileSize(file.fileSize)}\n")
                stringBuilder.append("- **Расширение:** ${file.fileExtension}\n\n")

                stringBuilder.append("**Содержимое файла:**\n\n")
                stringBuilder.append("```${getFileExtensionForMarkdown(file.fileExtension)}\n")
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

                // Обрабатываем содержимое сообщения
                val content = if (message.fileAttachment != null) {
                    // Убираем техническую информацию о файле из сообщения, оставляем только пользовательский текст
                    val lines = message.content.lines()
                    val filteredLines = lines.filter { line ->
                        !line.contains("📎 **Файл**:") &&
                        !line.contains("**Размер**:") &&
                        !line.contains("**Превью**:") &&
                        !line.contains("Полный файл будет передан при отправке запроса")
                    }
                    filteredLines.joinToString("\n").trim()
                } else {
                    message.content
                }

                if (content.isNotBlank()) {
                    stringBuilder.append("$content\n\n")
                }

                // Добавляем информацию о файле, если он есть
                message.fileAttachment?.let { metadata ->
                    stringBuilder.append("*📎 Файл прикреплен: ${metadata.fileName}*\n\n")
                }
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

    override suspend fun sendMessageWithFallback(
        model: String,
        conversationId: String?,
        estimatedTokens: Int // для точной корректировки количества токенов
    ) {
        val currentConversationId = conversationId
            ?: throw DomainError.Local("Conversation ID is required")

        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = "")
        val assistantMessageId =
            historyRepository.addMessage(currentConversationId, assistantMessage)

        try {
            val apiKey = settingsRepository.getApiKey()?.trim()
                ?: throw DomainError.Local("API ключ не указан.")

            // 1. Получаем системный промпт
            val systemPrompt = historyRepository.getSystemPrompt(currentConversationId)

            // 2. Строим payload с файлами
            val payload = buildMessagesForApi(currentConversationId, systemPrompt)

            // 3. Выбираем стратегию отправки в зависимости от наличия файлов
            if (payload.attachedFiles.isNotEmpty()) {
                // СТРАТЕГИЯ А: Отправка с файлами
                runStreamingWithFiles(
                    model = model,
                    payload = payload,
                    apiKey = apiKey,
                    messageId = assistantMessageId
                )
            } else {
                // СТРАТЕГИЯ Б: Обычная отправка без файлов
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

        // Формируем специальный запрос с файлами
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
                if (cause != null) {
                    streamError = when (cause) {
                        is DomainError -> cause
                        else -> DomainError.Generic(cause.localizedMessage ?: "Unknown flow error")
                    }
                    return@onCompletion
                }

                if (!receivedAnyData && streamError == null) {
                    streamError = DomainError.Generic("Stream completed without emitting any data.")
                }
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

                    DataResult.Loading -> { /* Игнорируем */
                    }
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
     * ✅ ИСПРАВЛЕНО: Добавляет сообщение с файлом БЕЗ автоматической отправки
     */
    override suspend fun addUserMessageWithFile(
        conversationId: String,
        userMessage: String,
        fileAttachment: FileAttachment
    ) {
        // 1. Проверяем, существует ли диалог, и создаем его при необходимости
        val conversation = historyRepository.getConversation(conversationId)
        if (conversation == null) {
            // Создаем новый диалог с именем файла как заголовком
            val title = "Анализ файла: ${fileAttachment.fileName.take(50)}"
            historyRepository.createNewConversation(title)
        }

        // 2. Сохраняем полный файл в репозиторий
        fileRepository.saveTemporaryFile(fileAttachment)

        // 3. Создаем легковесные метаданные
        val metadata = FileAttachmentMetadata(
            fileId = fileAttachment.id,
            fileName = fileAttachment.fileName,
            fileExtension = fileAttachment.fileExtension,
            fileSize = fileAttachment.fileSize,
            mimeType = fileAttachment.mimeType,
            preview = truncateAtWordBoundary(fileAttachment.originalContent, FILE_PREVIEW_LENGTH)
        )

        // 4. Формируем краткое сообщение для истории
        val displayMessage = buildString {
            if (userMessage.isNotBlank()) {
                append(userMessage)
                append("\n\n")
            }
            append("📎 **Файл**: ${metadata.fileName}")
            append("\n**Размер**: ${formatFileSize(metadata.fileSize)}")
            append("\n\n**Превью**:\n```")
            if (fileAttachment.originalContent.length > FILE_PREVIEW_LENGTH) {
                append("...\n```\n*Полный файл будет передан при отправке запроса*")
            } else {
                append("\n```")
            }
        }

        // 5. Сохраняем в историю с метаданными
        val message = ChatMessage(
            role = ChatRole.USER,
            content = displayMessage,
            fileAttachment = metadata
        )

        historyRepository.addMessages(conversationId, listOf(message))
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Обрывает текст на границе слова
     */
    private fun truncateAtWordBoundary(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        val truncated = text.take(maxLength)
        val lastSpace = truncated.lastIndexOf(' ')

        return if (lastSpace > maxLength * 0.8) {
            truncated.substring(0, lastSpace)
        } else {
            truncated
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Правильное построение сообщений для API
     */
    private suspend fun buildMessagesForApi(
        conversationId: String,
        systemPrompt: String?
    ): ApiRequestPayload {
        val history = historyRepository.getHistoryFlow(conversationId)
            .first()
            .takeLast(MAX_HISTORY_SIZE)

        val messagesForApi = mutableListOf<ApiMessage>()
        val attachedFiles = mutableListOf<FileAttachment>()

        // 1. Системный промпт (если есть) ВСЕГДА первым
        systemPrompt?.let {
            messagesForApi.add(ApiMessage(role = "system", content = it))
        }

        // 2. Собираем файлы из истории
        for (message in history) {
            if (message.fileAttachment != null) {
                val fullFile = fileRepository.getTemporaryFile(message.fileAttachment.fileId)
                if (fullFile != null && attachedFiles.none { it.id == fullFile.id }) {
                    attachedFiles.add(fullFile)
                }
            }
        }

        // 3. Если есть файлы, добавляем СПЕЦИАЛЬНОЕ системное сообщение с инструкцией
        if (attachedFiles.isNotEmpty()) {
            val filesInstruction = buildString {
                append("📋 **Attached Files Context**\n\n")
                append("The user has attached ${attachedFiles.size} file(s) for analysis. ")
                append("Read and analyze the content below:\n\n")

                attachedFiles.forEachIndexed { index, file ->
                    append("--- FILE ${index + 1}: ${file.fileName} ---\n")
                    append("Type: ${file.mimeType}\n")
                    append("Size: ${formatFileSize(file.fileSize)}\n")
                    append("Content:\n${file.originalContent}\n")
                    append("--- END OF FILE ${index + 1} ---\n\n")
                }

                append("🔍 **Instructions**: Carefully read all attached files and provide a comprehensive analysis based on the user's request.")
            }

            messagesForApi.add(ApiMessage(role = "system", content = filesInstruction))
        }

        // 4. Добавляем историю сообщений (БЕЗ полного контента файлов)
        for (message in history) {
            val messageContent = if (message.fileAttachment != null) {
                // Убираем превью из истории, т.к. файл уже в system message
                message.content.substringBefore("**Превью**:").trim()
            } else {
                message.content
            }

            messagesForApi.add(
                ApiMessage(
                    role = message.role.toApiRole(),  // ✅ Используем toApiRole()
                    content = messageContent
                )
            )
        }

        return ApiRequestPayload(
            messages = messagesForApi,
            attachedFiles = attachedFiles
        )
    }

    /**
     * ✅ ИСПРАВЛЕНО: Правильный маппинг ролей
     */
    private suspend fun runStreamingAttempt(
        model: String,
        messages: List<ApiMessage>,
        apiKey: String,
        messageId: String,
        estimatedTokens: Int
    ) {
        var receivedAnyData = false
        var streamError: DomainError? = null

        val chatMessages = messages.map { apiMsg ->
            ChatMessage(
                role = ChatRole.fromApiRole(apiMsg.role),
                content = apiMsg.content
            )
        }

        modelsRepository.getChatCompletionStream(model, chatMessages, apiKey)
            .onCompletion { cause ->
                if (cause != null) {
                    streamError = when (cause) {
                        is DomainError -> cause
                        else -> DomainError.Generic(cause.localizedMessage ?: "Unknown flow error")
                    }
                    return@onCompletion
                }

                if (!receivedAnyData && streamError == null) {
                    streamError = DomainError.Generic("Stream completed without emitting any data.")
                }
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

                    DataResult.Loading -> { /* Игнорируем */
                    }
                }
            }

        streamError?.let { throw it }
    }

    /**
     * Определяет расширение файла для markdown подсветки синтаксиса
     */
    private fun getFileExtensionForMarkdown(extension: String): String {
        return when (extension.lowercase()) {
            "kt", "kotlin" -> "kotlin"
            "java" -> "java"
            "js", "javascript" -> "javascript"
            "ts", "typescript" -> "typescript"
            "py", "python" -> "python"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "md", "markdown" -> "markdown"
            "html" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "less" -> "less"
            "php" -> "php"
            "rb", "ruby" -> "ruby"
            "go" -> "go"
            "rs", "rust" -> "rust"
            "cpp", "c++", "cxx", "cc" -> "cpp"
            "c" -> "c"
            "cs", "csharp" -> "csharp"
            "swift" -> "swift"
            "sh", "bash", "shell" -> "bash"
            "sql" -> "sql"
            "dockerfile" -> "dockerfile"
            "makefile", "mk" -> "makefile"
            "ini", "conf", "config" -> "ini"
            "properties", "prop" -> "properties"
            "toml" -> "toml"
            "gradle" -> "gradle"
            "kts" -> "kotlin"
            else -> "" // Без подсветки для неизвестных типов
        }
    }

}
