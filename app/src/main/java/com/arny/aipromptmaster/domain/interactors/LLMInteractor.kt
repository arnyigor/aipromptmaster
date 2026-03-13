package com.arny.aipromptmaster.domain.interactors

import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.data.utils.FileUtils.formatFileSize
import com.arny.aipromptmaster.data.utils.collectWithThrottling
import com.arny.aipromptmaster.domain.models.ApiMessage
import com.arny.aipromptmaster.domain.models.ApiRequestPayload
import com.arny.aipromptmaster.domain.models.ApiRequestWithFiles
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileReference
import com.arny.aipromptmaster.domain.models.MessageState
import com.arny.aipromptmaster.domain.models.StreamResult
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil

class LLMInteractor(
    private val routerRepository: IOpenRouterRepository,
    private val modelRepository: ModelRepository,
    private val settingsRepository: ISettingsRepository,
    private val historyRepository: IChatHistoryRepository,
    private val fileRepository: IFileRepository
) : ILLMInteractor {

    var capturedError: DomainError? = null // 1. Переменная для ошибки

    override fun observeStreamingBuffer(): StateFlow<Map<String, String>> =
        historyRepository.observeStreamingBuffer()

    private companion object {
        /**
         * Максимальный лимит токенов для истории
         */
        const val MAX_HISTORY_TOKENS = 32000 // TODO - вынести в настройки с описанием

        /**
         * Символы на один токен – можно сделать конфигом.
         */
        const val TOKEN_RATIO = 2.5f

        /**
         * Текст плейсхолдера для сообщения "Думает..."
         */
        const val THINKING_PLACEHOLDER = "Думает..."
    }

    override suspend fun createNewConversation(title: String): String =
        historyRepository.createNewConversation(title)

    override suspend fun setSystemPrompt(conversationId: String, prompt: String) {
        historyRepository.updateSystemPrompt(conversationId, prompt)
    }

    override suspend fun getSystemPrompt(conversationId: String): String =
        historyRepository.getSystemPrompt(conversationId).orEmpty()

    override suspend fun setSystemPromptWithChatCreation(
        conversationId: String?,
        prompt: String,
        chatTitle: String
    ): String {
        val targetConversationId = conversationId ?: createNewConversation(chatTitle)
        setSystemPrompt(targetConversationId, prompt)
        return targetConversationId
    }

    override fun getChatList(): Flow<List<Chat>> = historyRepository.getChatList()

    override fun getChatHistoryFlow(conversationId: String?): Flow<List<ChatMessage>> =
        historyRepository.getHistoryFlow(conversationId.orEmpty())

    override suspend fun clearChat(conversationId: String?) {
        historyRepository.clearHistory(conversationId.orEmpty())
    }

    override suspend fun deleteConversation(conversationId: String) {
        historyRepository.deleteConversation(conversationId)
    }

    override fun cancelCurrentRequest() {
        routerRepository.cancelCurrentRequest()
        historyRepository.cleanStreamingBuffer()
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

    override suspend fun deleteMessage(id: String) {
        historyRepository.deleteMessage(id)
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
        conversationId: String?,
        userMessageText: String,
        attachedFiles: List<FileAttachment>,
        skipUserMessageCreation: Boolean
    ) {
        val chatId = conversationId ?: throw DomainError.local(R.string.error_no_conversation_id)

        // --- ЛОГИКА СОХРАНЕНИЯ (Save Logic) ---
        // ШАГ 1: Сохраняем сообщение пользователя (если не skip)
        if (!skipUserMessageCreation) {
            if (attachedFiles.isEmpty()) {
                historyRepository.addMessage(
                    chatId,
                    ChatMessage(role = ChatRole.USER, content = userMessageText)
                )
            } else {
                saveUserMessageWithFiles(chatId, userMessageText, attachedFiles)
            }
        }

        // Получаем API ключ и модель ДО создания сообщения
        val apiKey = settingsRepository.getApiKey().takeIf { !it.isNullOrBlank() }
            ?: throw DomainError.local(R.string.api_key_not_found)

        // Получаем полный объект модели для проверки multimodal возможностей
        val selectedModel = modelRepository.getSelectedModel()
        val requestedModelId = selectedModel?.id.takeIf { !it.isNullOrBlank() }
            ?: throw DomainError.local(R.string.no_model_selected)

        // ШАГ 2: Создаем плейсхолдер ответа с указанием модели
        val assistantMsgId = historyRepository.addMessage(
            conversationId = chatId,
            message = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = THINKING_PLACEHOLDER,
                modelId = requestedModelId,
                state = MessageState.SENDING
            )
        )

        try {

            val history = historyRepository.getFullHistory(chatId)
            try {
                // Получаем стрим от репозитория/API с прикрепленными файлами и моделью
                val streamFlow: Flow<DataResult<StreamResult>> = routerRepository.getChatCompletionStream(
                    model = requestedModelId,
                    messages = history,
                    apiKey = apiKey,
                    attachedFiles = attachedFiles,
                    llmModel = selectedModel
                )

                // Переменная для хранения актуальной модели из ответа
                var actualModelId: String? = null

                // Фильтруем только успешные чанки для аккумулятора, ошибки обрабатываем отдельно
                // Преобразуем Flow<DataResult<StreamResult>> в Flow<StreamResult> для удобства,
                // выбрасывая исключения при ошибках.
                val contentFlow = streamFlow.map { result ->
                    when (result) {
                        is DataResult.Success -> {
                            // Сохраняем modelId из ответа если он есть и отличается от запрошенного
                            result.data.modelId?.let { modelId ->
                                if (modelId != requestedModelId && actualModelId == null) {
                                    actualModelId = modelId
                                }
                            }
                            result.data
                        }
                        is DataResult.Error -> throw result.error // Прервет collectWithThrottling
                        DataResult.Loading -> StreamResult(content = "")
                    }
                }

                // 2. ЗАПУСК ТРОТТЛИНГА
                val finalContent = contentFlow.collectWithThrottling(
                    initialValue = StringBuilder(), // Используем StringBuilder как аккумулятор
                    periodMillis = 300L, // Обновляем БД ~3 раза в секунду
                    accumulator = { builder, newChunk ->
                        builder.append(newChunk.content) // Эффективное добавление без лишних аллокаций
                    },
                    onUpdate = { fullContentBuilder ->
                        // Эта лямбда вызывается раз в 300мс
                        // Тримим контент перед сохранением чтобы убрать ведущие/ trailing пробелы
                        val content = fullContentBuilder.toString().trim()
                        val displayContent = if (content == THINKING_PLACEHOLDER || content.isBlank()) {
                            ""
                        } else {
                            content
                        }
                        historyRepository.updateMessageContent(
                            messageId = assistantMsgId,
                            newContent = displayContent
                        )
                    }
                )

                // 3. По успешному завершении стрима
                val finalContentStr = finalContent.toString().trim()

                // Удаляем плейсхолдер если контент пустой
                if (finalContentStr.isBlank() || finalContentStr == THINKING_PLACEHOLDER) {
                    historyRepository.deleteMessage(assistantMsgId)
                } else {
                    // Обновляем контент финально (уже тримированный)
                    historyRepository.updateMessageContent(assistantMsgId, finalContentStr)

                    // Обновляем modelId если пришел отличный от запрошенного
                    actualModelId?.let { modelId ->
                        historyRepository.updateMessageModelId(assistantMsgId, modelId)
                    }
                }

            } catch (e: Exception) {
                // Обработка ошибок
                Timber.e(e, "Streaming failed")
                // Удаляем плейсхолдер при ошибке
                historyRepository.deleteMessage(assistantMsgId)
                throw e // Пробрасываем, чтобы ViewModel показала ошибку
            }

        } catch (e: Exception) {
            // Удаляем плейсхолдер при ошибке
            historyRepository.deleteMessage(assistantMsgId)
            throw e
        }
    }

    private suspend fun saveUserMessageWithFiles(
        chatId: String,
        text: String,
        files: List<FileAttachment>
    ) {
        if (files.isEmpty()) {
            historyRepository.addMessage(chatId, ChatMessage(role = ChatRole.USER, content = text))
            return
        }

        // 1. Первый файл забирает текст сообщения
        val firstFile = files.first()
        fileRepository.saveTemporaryFile(firstFile) // Сохраняем полный контент

        historyRepository.addMessage(
            conversationId = chatId,
            message = ChatMessage(
                role = ChatRole.USER,
                content = text,
                fileAttachment = firstFile.toMetadata() // Extension function
            )
        )

        // 2. Остальные файлы без текста
        files.drop(1).forEach { file ->
            fileRepository.saveTemporaryFile(file)
            historyRepository.addMessage(
                conversationId = chatId,
                message = ChatMessage(
                    role = ChatRole.USER,
                    content = "", // Пустое сообщение-контейнер
                    fileAttachment = file.toMetadata()
                )
            )
        }
    }

    private suspend fun buildMessagesForApi(
        conversationId: String,
        conversationSystemPrompt: String?
    ): ApiRequestPayload {
        // 1️⃣ Получаем историю с ограничением токенов
        val historyEntities = getHistoryWithTokenLimit(conversationId)

        val messagesForApi = mutableListOf<ApiMessage>()
        val filesToAttach = mutableListOf<FileAttachment>()

        // 2️⃣ Проверяем наличие SYSTEM‑сообщения
        val hasSystemMessage = historyEntities.any { it.role == ChatRole.SYSTEM }

        if (!hasSystemMessage && !conversationSystemPrompt.isNullOrBlank()) {
            messagesForApi.add(
                ApiMessage(role = "system", content = conversationSystemPrompt)
            )
        }

        // 3️⃣ Добавляем остальные сообщения (пользователь/ассистент)
        historyEntities.forEach { entity ->
            val content = if (entity.fileAttachment != null) {
                // Оставляем только пользовательский текст
                entity.content.trim()
            } else {
                entity.content
            }
            messagesForApi.add(
                ApiMessage(role = entity.role.toApiRole(), content = content)
            )
        }

        // 4️⃣ Сбор файлов из истории (если нужны)
        historyEntities.mapNotNull { it.fileAttachment }.forEach { attachment ->
            fileRepository.getTemporaryFile(attachment.fileId)?.let { filesToAttach.add(it) }
        }
        return ApiRequestPayload(
            messages = messagesForApi,
            attachedFiles = filesToAttach
        )
    }

    /**
     * Общий метод обработки потоков чата.
     *
     * @param streamFlow Flow, который будет эмитить `DataResult<StreamResult>` от модели.
     * @param messageId id сообщения в БД, куда писать промежуточные результаты и UI‑буфер.
     */
    private suspend fun runStreaming(
        streamFlow: Flow<DataResult<StreamResult>>,
        messageId: String
    ) {
        // Буфер для отображения в UI во время потока
        var fullContent = ""

        // Инициализируем пустой буфер, чтобы пользователь видел "пробел" сразу.
        historyRepository.updateStreamingBuffer(mapOf(messageId to ""))

        // Переменная, куда будем сохранять первый возникший DomainError (если он появится)
        var firstDomainError: DomainError? = null

        streamFlow
            .onStart { /* ничего не делаем – буфер уже пуст */ }
            .onCompletion { cause ->
                // Убираем запись из буфера, чтобы UI перестало показывать "пустую" строку.
                historyRepository.updateStreamingBuffer(
                    historyRepository.observeStreamingBuffer().value - messageId
                )

                when {
                    // Если поток был отменён – пробрасываем исключение дальше,
                    // чтобы ViewModel смогла корректно обработать cancel().
                    cause is CancellationException -> throw cause

                    // Если ранее в потоке уже возникло DataResult.Error, он хранится в firstDomainError.
                    firstDomainError != null -> throw firstDomainError!!

                    // Любая другая причина завершения – считаем ошибкой сервера.
                    cause != null -> throw DomainError.Generic(cause.localizedMessage ?: "Unknown error")

                    // Поток завершился без ошибок:
                    // если fullContent пустой, это действительно "пустой ответ" от модели,
                    // иначе всё ок и ничего не делаем.
                    fullContent.isEmpty() ->
                        throw DomainError.Generic("Stream completed without emitting any data.")

                    else -> {
                        /* успешное завершение – просто продолжаем работу */
                    }
                }
            }
            .collect { result ->
                when (result) {
                    is DataResult.Success -> {
                        // Добавляем новый кусок к полному ответу.
                        fullContent += result.data.content

                        // Обновляем буфер, чтобы пользователь видел прогресс в реальном времени.
                        historyRepository.updateStreamingBuffer(
                            historyRepository.observeStreamingBuffer().value + (messageId to fullContent)
                        )
                    }

                    is DataResult.Error -> {
                        // Запоминаем первый возникший DomainError; он будет брошен в onCompletion.
                        if (firstDomainError == null) firstDomainError = result.error
                    }

                    DataResult.Loading -> { /* игнорируем – поток уже загружает данные */ }
                }
            }

        // Если дошли до сюда без исключений, значит всё прошло успешно.
        // Тримим контент перед сохранением в БД
        val trimmedContent = fullContent.trim()
        if (trimmedContent.isBlank()) {
            // Если контент пустой после трима - не сохраняем
            return
        }
        historyRepository.updateMessageContent(messageId, trimmedContent)
    }


    /**
     * Wrapper around [runStreaming] for requests that include files.
     */
    private suspend fun runStreamingWithFiles(
        model: String,
        payload: ApiRequestPayload,
        apiKey: String,
        messageId: String
    ) {
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

        val streamFlow =
            routerRepository.getChatCompletionStreamWithFiles(requestWithFiles, apiKey)

        runStreaming(streamFlow, messageId)
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

    suspend fun getHistoryWithTokenLimit(conversationId: String): List<ChatMessage> {
        val allMessages = historyRepository.getHistoryFlow(conversationId).first()

        var usedTokens = 0
        val selected = mutableListOf<ChatMessage>()

        for (msg in allMessages.reversed()) {          // от новых к старым
            val tokenCount = ceil(msg.content.length / TOKEN_RATIO).toInt()
            if (usedTokens + tokenCount > MAX_HISTORY_TOKENS) break

            usedTokens += tokenCount
            selected.add(0, msg)                       // вставляем в начало → правильный порядок
        }
        return selected
    }

    /* ---------- Оценка токенов текущего ввода ---------- */

    override suspend fun estimateTokens(
        inputText: String,
        attachedFiles: List<FileAttachment>,
        systemPrompt: String?,
        recentMessages: List<ChatMessage>
    ): Int {
        val ratio = TOKEN_RATIO
        var total = 0

        // Текст пользователя
        if (inputText.isNotBlank()) {
            total += ceil(inputText.length / ratio).toInt()
        }

        // Прикреплённые файлы (только текстовые)
        attachedFiles.filter { it.mimeType.startsWith("text/") }
            .forEach { file ->
                total += ceil(file.originalContent.length / ratio).toInt()
            }

        // Системный промпт
        if (!systemPrompt.isNullOrBlank()) {
            total += ceil(systemPrompt.length / ratio).toInt()
        }

        // Последние 10 сообщений из истории (чтобы не перегрузить UI)
        recentMessages.takeLast(10).forEach { msg ->
            total += ceil(msg.content.length / ratio).toInt()
        }

        return total
    }



}
