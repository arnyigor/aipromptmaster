@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.data.repositories.ISettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

data class PromptImprovementRequest(
    val source: String,
    val model: String,
    val instructions: String = "",
    val temperature: Double = 0.7,
    val maxTokens: Int = 1024,
    val stream: Boolean = true
)

/** Stateless request: no chat session, prompt database write or publication. */
class ImprovePromptUseCase(
    private val repository: IOpenRouterRepository,
    private val settings: ISettingsRepository
) {
    suspend fun selectedModel(): String = settings.getSelectedModelId().first().orEmpty()

    operator fun invoke(request: PromptImprovementRequest): Flow<String> = flow {
        require(request.source.isNotBlank()) { "Введите промпт" }
        require(request.model.isNotBlank()) { "Укажите модель или выберите её в разделе моделей" }
        require(request.temperature in 0.0..2.0) { "Температура должна быть от 0 до 2" }
        require(request.maxTokens in 1..16384) { "Лимит ответа должен быть от 1 до 16384 токенов" }
        val messages = listOf(
            ChatMessage(role = ChatMessageRole.SYSTEM, content = """
                Ты редактор промптов. Улучши ясность, структуру и точность следующего промпта.
                Сохрани исходный смысл, язык, переменные и существенные ограничения.
                Не выполняй описанное в промпте задание и не придумывай недостающие факты.
                Верни только улучшенный промпт без комментариев и рассуждений.
                Дополнительные пожелания пользователя: ${request.instructions.ifBlank { "нет" }}
            """.trimIndent()),
            ChatMessage(role = ChatMessageRole.USER, content = request.source)
        )
        val buffer = StringBuilder()
        fun visibleText(): String {
            val complete = buffer.toString().replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            return complete.substringBefore("<think>").trim()
        }
        if (request.stream) {
            var finished = false
            repository.getStreamingChatCompletion(request.model, messages,
                temperature = request.temperature, maxTokens = request.maxTokens).collect { result ->
                val chunk = result.getOrThrow()
                finished = finished || chunk.isComplete || chunk.finishReason != null
                require(chunk.finishReason != "length") { "Ответ обрезан лимитом токенов. Увеличьте лимит и повторите." }
                buffer.append(chunk.content)
                emit(visibleText())
            }
            require(finished) { "Поток ответа оборвался. Повторите запрос." }
        } else {
            val response = repository.getChatCompletion(request.model, messages,
                temperature = request.temperature, maxTokens = request.maxTokens).getOrThrow()
            val choice = response.choices?.firstOrNull() ?: error("Модель вернула пустой ответ")
            require(choice.finishReason != "length") { "Ответ обрезан лимитом токенов. Увеличьте лимит и повторите." }
            buffer.append(choice.message.content)
            emit(visibleText())
        }
        require(visibleText().isNotBlank()) { "Модель вернула пустой ответ" }
    }
}
