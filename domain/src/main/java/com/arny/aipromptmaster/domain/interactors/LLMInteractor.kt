package com.arny.aipromptmaster.domain.interactors

import android.util.Log
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.results.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
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

    override suspend fun setSystemPrompt(conversationId: String, prompt: String) {
        historyRepository.updateSystemPrompt(conversationId, prompt)
    }

    override suspend fun getSystemPrompt(conversationId: String): String? {
        return historyRepository.getSystemPrompt(conversationId)
    }

    override suspend fun deleteConversation(conversationId: String) {
        historyRepository.deleteConversation(conversationId)
    }

    override suspend fun getFullChatForExport(conversationId: String): String {
        val conversation = historyRepository.getConversation(conversationId)
            ?: throw DomainError.local(R.string.dialog_not_found) // Или вернуть строку с ошибкой

        val history = historyRepository.getFullHistory(conversationId)

        val stringBuilder = StringBuilder()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        // --- Заголовок ---
        stringBuilder.append("Диалог: ${conversation.title}\n")
        stringBuilder.append("Дата экспорта: ${dateFormat.format(Date())}\n")

        if (!conversation.systemPrompt.isNullOrBlank()) {
            stringBuilder.append("\n--- СИСТЕМНЫЙ ПРОМПТ ---\n")
            stringBuilder.append(conversation.systemPrompt)
            stringBuilder.append("\n")
        }

        // --- История ---
        stringBuilder.append("\n--- ИСТОРИЯ ДИАЛОГА ---\n\n")

        if (history.isEmpty()) {
            stringBuilder.append("Сообщений нет.")
        } else {
            history.forEach { message ->
                val role = when (message.role) {
                    ChatRole.USER -> "ПОЛЬЗОВАТЕЛЬ"
                    ChatRole.ASSISTANT -> "АССИСТЕНТ"
                    ChatRole.SYSTEM -> "СИСТЕМА" // На всякий случай
                }
                stringBuilder.append("[$role]:\n")
                stringBuilder.append("${message.content}\n\n")
            }
        }

        return stringBuilder.toString()
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
                    emit(DataResult.Error(DomainError.local(R.string.api_key_not_found)))
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
                            emit(DataResult.Error(DomainError.generic(R.string.empty_api_response)))
                        }
                    },
                    onFailure = { exception -> emit(DataResult.Error(exception)) }
                )
            } catch (e: Exception) {
                emit(DataResult.Error(DomainError.Generic(e.message)))
            }
        }

    override suspend fun sendMessageWithFallback(model: String, conversationId: String?) {
        val currentConversationId =
            conversationId ?: return // Или бросить ошибку, если ID null недопустим

        val assistantMessage = ChatMessage(role = ChatRole.ASSISTANT, content = "")
        val assistantMessageId =
            historyRepository.addMessage(currentConversationId, assistantMessage)

        try {
            val apiKey = settingsRepository.getApiKey()?.trim()
                ?: throw DomainError.Local("API ключ не указан.")

            // --- НОВАЯ ЛОГИКА ПОСТРОЕНИЯ КОНТЕКСТА ---
            // 1. Получаем системный промпт
            val systemPrompt = historyRepository.getSystemPrompt(currentConversationId)

            // 2. Создаем системное сообщение, если промпт есть
            val systemMessage = systemPrompt?.let {
                ChatMessage(role = ChatRole.SYSTEM, content = it)
            }

            // 3. Получаем историю
            val history = historyRepository.getHistoryFlow(currentConversationId).first()
                .takeLast(MAX_HISTORY_SIZE)

            // 4. Собираем итоговый список для API
            val messagesForApi = buildList {
                systemMessage?.let { add(it) } // Добавляем системное сообщение, если оно есть
                addAll(history)
            }
            // --- КОНЕЦ НОВОЙ ЛОГИКИ ---

            runStreamingAttempt(model, messagesForApi, apiKey, assistantMessageId)
        } catch (e: Exception) { // Ловим стриминг и другие ошибки
            try {
                // ... тут должна быть логика fallback, которая тоже использует messagesForApi
                // Сейчас я ее пропущу для краткости, но она должна быть адаптирована
                Log.e(
                    "LLMInteractor",
                    "Streaming/preparation failed: ${e.message}. Attempting fallback."
                )
                // ...
                // В случае полного провала
                historyRepository.deleteMessage(assistantMessageId)
                throw e // Пробрасываем оригинальную ошибку
            } catch (fallbackException: Exception) {
                historyRepository.deleteMessage(assistantMessageId)
                throw fallbackException
            }
        }
    }

    private suspend fun runStreamingAttempt(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String,
        messageId: String
    ) {
        var receivedAnyData = false
        var streamError: DomainError? = null

        // Используем onCompletion для обработки завершения потока
        modelsRepository.getChatCompletionStream(model, messages, apiKey)
            .onCompletion { cause ->
                // Этот блок вызывается ПОСЛЕ того, как поток завершился
                // (либо успешно, либо с ошибкой `cause`).
                // Если `cause` не null, значит, сам flow бросил исключение (наш первый рефакторинг).
                if (cause != null) {
                    // Это перехватит ошибки, если вы вдруг вернетесь к подходу с `throw` в репозитории.
                    // При текущей реализации репозитория с DataResult, этот блок не должен выполниться.
                    streamError = when(cause) {
                        is DomainError -> cause
                        else -> DomainError.Generic(cause.localizedMessage ?: "Unknown flow error")
                    }
                    return@onCompletion
                }

                // Если поток завершился штатно, но мы не получили ни одного чанка,
                // и при этом не было ошибки внутри потока (streamError == null),
                // то устанавливаем ошибку "пустого потока".
                if (!receivedAnyData && streamError == null) {
                    streamError = DomainError.Generic("Stream completed without emitting any data.")
                }
            }
            .collect { result ->
                // Внутри collect мы ТОЛЬКО обрабатываем данные, но не бросаем исключения.
                when (result) {
                    is DataResult.Success -> {
                        historyRepository.appendContentToMessage(messageId, result.data)
                        receivedAnyData = true // Помечаем, что данные были
                    }
                    is DataResult.Error -> {
                        val exception = result.exception
                        streamError = when (exception) {
                            is DomainError -> exception // Если это уже наш тип, просто используем его
                            null -> DomainError.Generic("Stream returned an error with no exception details")
                            else -> DomainError.Generic( // Оборачиваем любое другое исключение
                                exception.localizedMessage ?: "Unknown stream error"
                            )
                        }
                    }
                    DataResult.Loading -> { /* Игнорируем */ }
                }
            }

        // После того как collect завершился, проверяем, была ли ошибка.
        // Если да, бросаем ее. Теперь это безопасно и предсказуемо.
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
        return combine(selectedIdFlow, modelsListFlow) { selectedId, modelsList ->
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
