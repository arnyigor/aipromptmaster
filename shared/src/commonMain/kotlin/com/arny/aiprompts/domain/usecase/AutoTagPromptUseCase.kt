package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.repositories.IOpenRouterRepository
import com.arny.aiprompts.utils.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * UseCase для автоматического тегирования и категоризации промптов с помощью LLM.
 *
 * Отправляет текст промпта в LLM с системным промптом, требующим вернуть JSON
 * с категорией, тегами и описанием. Используется при создании/редактировании промптов.
 *
 * @property openRouterRepository Репозиторий для запросов к LLM API
 * @property json JSON сериализатор для парсинга ответа
 */
class AutoTagPromptUseCase(
    private val openRouterRepository: IOpenRouterRepository,
    private val json: Json
) {
    /**
     * Системный промпт для классификации.
     * Требует от модели вернуть строго JSON без лишнего текста.
     */
    private val systemPrompt = """
        You are an expert prompt classifier. Your task is to analyze the user's prompt and return ONLY a JSON object.
        
        Categories should be one of: Coding, Writing, Business, Creative, Education, Analysis, Fun, General, Healthcare, Legal, Technology
        
        Return exactly this JSON structure:
        {
            "category": "CategoryName",
            "tags": ["tag1", "tag2", "tag3"],
            "summary": "Brief 1-sentence description"
        }
        
        Rules:
        - Tags should be lowercase, no spaces (use hyphens)
        - Provide 2-5 relevant tags
        - Category must be from the list above
        - Return ONLY the JSON, no markdown, no explanations
    """.trimIndent()

    /**
     * Выполняет анализ промпта и возвращает результат классификации.
     *
     * @param promptContent Текст промпта для анализа
     * @param modelId ID модели для использования (опционально, по умолчанию gemini-flash)
     * @return Result с PromptAnalysisResult или ошибка
     */
    suspend operator fun invoke(
        promptContent: String,
        modelId: String = "google/gemini-flash-1.5"
    ): Result<PromptAnalysisResult> {
        if (promptContent.isBlank()) {
            return Result.failure(IllegalArgumentException("Prompt content is empty"))
        }

        return try {
            Logger.d("AutoTag", "Analyzing prompt with model: $modelId")

            val messages = listOf(
                ChatMessage(
                    id = "system",
                    role = ChatMessageRole.SYSTEM,
                    content = systemPrompt,
                    timestamp = 0
                ),
                ChatMessage(
                    id = "user",
                    role = ChatMessageRole.USER,
                    content = promptContent,
                    timestamp = 0
                )
            )

            val response = openRouterRepository.getChatCompletion(
                model = modelId,
                messages = messages
            )

            response.fold(
                onSuccess = { completion ->
                    val content = completion.choices?.firstOrNull()?.message?.content
                        ?: return Result.failure(Exception("Empty response from model"))

                    Logger.d("AutoTag", "Raw response: $content")

                    // Очистка от markdown блоков если модель их добавила
                    val cleanJson = content
                        .replace("```json", "")
                        .replace("```", "")
                        .trim()

                    try {
                        val result = json.decodeFromString(
                            PromptAnalysisResult.serializer(),
                            cleanJson
                        )
                        Logger.d("AutoTag", "Parsed result: $result")
                        Result.success(result)
                    } catch (e: Exception) {
                        Logger.e(e, "AutoTag", "Failed to parse JSON: $cleanJson")
                        Result.failure(Exception("Failed to parse model response: ${e.message}"))
                    }
                },
                onFailure = { error ->
                    Logger.e(error, "AutoTag", "API request failed")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Logger.e(e, "AutoTag", "Analysis failed")
            Result.failure(e)
        }
    }

    /**
     * Выполняет анализ с fallback на локальную классификацию если LLM недоступен.
     * Использует простое keyword-based определение категории.
     */
    fun analyzeLocal(promptContent: String): PromptAnalysisResult {
        val content = promptContent.lowercase()

        // Keyword mappings
        val categoryKeywords = mapOf(
            "Coding" to listOf("code", "programming", "function", "class", "api", "debug", "refactor", "kotlin", "java", "python"),
            "Writing" to listOf("write", "essay", "blog", "article", "story", "email", "letter", "content"),
            "Business" to listOf("business", "marketing", "strategy", "plan", "proposal", "meeting", "presentation"),
            "Creative" to listOf("creative", "art", "design", "imagine", "brainstorm", "idea", "concept"),
            "Education" to listOf("explain", "teach", "learn", "tutorial", "guide", "how to", "study"),
            "Analysis" to listOf("analyze", "research", "data", "statistics", "review", "compare", "evaluate"),
            "Healthcare" to listOf("health", "medical", "doctor", "patient", "diagnosis", "treatment"),
            "Legal" to listOf("legal", "law", "contract", "agreement", "terms", "policy"),
            "Technology" to listOf("tech", "software", "hardware", "ai", "ml", "cloud", "database")
        )

        // Find best matching category
        var bestCategory = "General"
        var maxMatches = 0

        categoryKeywords.forEach { (category, keywords) ->
            val matches = keywords.count { content.contains(it) }
            if (matches > maxMatches) {
                maxMatches = matches
                bestCategory = category
            }
        }

        // Generate tags based on found keywords
        val tags = categoryKeywords[bestCategory]
            ?.filter { content.contains(it) }
            ?.take(3)
            ?: listOf(bestCategory.lowercase())

        return PromptAnalysisResult(
            category = bestCategory,
            tags = tags.ifEmpty { listOf(bestCategory.lowercase()) },
            summary = "Auto-classified $bestCategory prompt"
        )
    }
}

/**
 * Результат анализа промпта.
 */
@Serializable
data class PromptAnalysisResult(
    val category: String,
    val tags: List<String>,
    val summary: String? = null
)
