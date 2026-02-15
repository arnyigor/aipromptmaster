package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.model.*
import com.arny.aiprompts.domain.usecase.PromptClassifier
import kotlin.time.ExperimentalTime

/**
 * UseCase for automatic categorization of prompts using TF-IDF classifier.
 * Maps prompts to existing application categories.
 */
class AutoCategorizeUseCase {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.35
        private const val DEFAULT_CATEGORY = "general"

        /**
         * Application categories (folder names in /prompts).
         */
        val APP_CATEGORIES = listOf(
            "business",
            "common_tasks",
            "creative",
            "education",
            "entertainment",
            "environment",
            "general",
            "healthcare",
            "legal",
            "marketing",
            "model_specific",
            "science",
            "technology"
        )

        /**
         * Mapping from classifier categories to application folder names.
         */
        private val CATEGORY_MAPPING = mapOf(
            // Image generation
            "Создание изображений" to "creative",
            "Изображения" to "creative",
            "Image" to "creative",
            "Art" to "creative",
            "Drawing" to "creative",

            // Code
            "Написание кода" to "technology",
            "Программирование" to "technology",
            "Код" to "technology",
            "Code" to "technology",
            "Development" to "technology",
            "Programming" to "technology",

            // Analysis - general
            "Анализ и суммаризация текста" to "general",
            "Анализ" to "general",
            "Суммаризация" to "general",
            "Analysis" to "general",
            "Summary" to "general",

            // Translation - education
            "Перевод" to "education",
            "Translation" to "education",
            "Language" to "education",

            // Business
            "Бизнес" to "business",
            "Работа" to "business",
            "Финансы" to "business",
            "Business" to "business",
            "Work" to "business",
            "Finance" to "business",
            "Career" to "business",

            // Education
            "Обучение" to "education",
            "Учёба" to "education",
            "Education" to "education",
            "Learning" to "education",
            "Study" to "education",

            // Marketing
            "Маркетинг" to "marketing",
            "Реклама" to "marketing",
            "Marketing" to "marketing",
            "SEO" to "marketing",
            "Advertising" to "marketing",

            // Entertainment
            "Развлечения" to "entertainment",
            "Игры" to "entertainment",
            "Entertainment" to "entertainment",
            "Games" to "entertainment",

            // Healthcare
            "Медицина" to "healthcare",
            "Здоровье" to "healthcare",
            "Health" to "healthcare",
            "Medicine" to "healthcare",

            // Legal
            "Юриспруденция" to "legal",
            "Закон" to "legal",
            "Legal" to "legal",
            "Law" to "legal",

            // Science
            "Наука" to "science",
            "Science" to "science",
            "Research" to "science",

            // Environment
            "Экология" to "environment",
            "Environment" to "environment",
            "Nature" to "environment",

            // Model specific
            "Модели" to "model_specific",
            "Models" to "model_specific",
            "GPT" to "model_specific",
            "Claude" to "model_specific",

            // Common tasks
            "Задачи" to "common_tasks",
            "Утилиты" to "common_tasks",
            "Tasks" to "common_tasks",
            "Utilities" to "common_tasks",

            // Fallback
            "Undefined" to "general",
            "Unknown" to "general"
        )
    }

    data class CategoryResult(
        val category: String,
        val confidence: Double,
        val isConfident: Boolean
    )

    private var classifier: PromptClassifier? = null

    /**
     * Initialize classifier with reference data.
     * Should be called once before using categorize().
     */
    fun initialize(referencePrompts: List<ReferencePrompt>) {
        classifier = PromptClassifier(referencePrompts)
    }

    /**
     * Categorize prompt text and return appropriate category.
     */
    operator fun invoke(promptText: String): CategoryResult {
        val currentClassifier = classifier
            ?: throw IllegalStateException("Classifier not initialized. Call initialize() first.")

        if (promptText.isBlank()) {
            return CategoryResult(DEFAULT_CATEGORY, 0.0, false)
        }

        val result = currentClassifier.classify(promptText)
        val mappedCategory = CATEGORY_MAPPING[result.predictedCategory] ?: DEFAULT_CATEGORY
        val isConfident = result.confidence >= CONFIDENCE_THRESHOLD

        return CategoryResult(
            category = if (isConfident) mappedCategory else DEFAULT_CATEGORY,
            confidence = result.confidence,
            isConfident = isConfident
        )
    }

    /**
     * Get all available category folder names.
     */
    fun getAvailableCategories(): List<String> = APP_CATEGORIES

    /**
     * Map a category name to application category.
     * Uses both exact mapping and fuzzy matching.
     */
    fun mapToAppCategory(categoryName: String?): String {
        if (categoryName.isNullOrBlank()) return DEFAULT_CATEGORY

        // Direct mapping
        CATEGORY_MAPPING[categoryName]?.let { return it }

        // Case-insensitive search
        CATEGORY_MAPPING.forEach { (key, value) ->
            if (key.equals(categoryName, ignoreCase = true)) {
                return value
            }
        }

        // Partial matching
        CATEGORY_MAPPING.forEach { (key, value) ->
            if (categoryName.contains(key, ignoreCase = true) ||
                key.contains(categoryName, ignoreCase = true)) {
                return value
            }
        }

        // Check if it's already an app category
        if (categoryName.lowercase() in APP_CATEGORIES) {
            return categoryName.lowercase()
        }

        return DEFAULT_CATEGORY
    }
}
