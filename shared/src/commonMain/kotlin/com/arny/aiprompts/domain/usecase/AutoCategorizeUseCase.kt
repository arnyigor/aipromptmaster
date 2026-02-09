package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.model.*
import com.arny.aiprompts.domain.usecase.PromptClassifier
import kotlin.time.ExperimentalTime

/**
 * UseCase for automatic categorization of prompts using TF-IDF classifier.
 */
class AutoCategorizeUseCase {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.35
        private const val DEFAULT_CATEGORY = "imported"
        
        // Mapping from classifier categories to folder names
        private val CATEGORY_MAPPING = mapOf(
            "Создание изображений" to "creative",
            "Написание кода" to "technology",
            "Анализ и суммаризация текста" to "general",
            "Перевод" to "general",
            "Undefined" to "imported"
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
    fun getAvailableCategories(): List<String> = CATEGORY_MAPPING.values.distinct()
}