package com.arny.aiprompts.domain.analysis

/**
 * Maps 4pda forum categories to application tags.
 * Provides category-to-tags mapping for prompt categorization.
 */
object CategoryTagMapper {

    /**
     * Map of 4pda category names to application tags.
     * Categories are in Russian as they appear on the forum.
     */
    private val categoryToTags = mapOf(
        "Универсальные / Метапромпты" to listOf(
            "universal",
            "metaprompt",
            "gpt",
            "claude",
            "general"
        ),
        "Обход цензуры и ограничений" to listOf(
            "jailbreak",
            "censorship",
            "bypass",
            "restriction"
        ),
        "Обучение / Языки / Учёба" to listOf(
            "education",
            "learning",
            "language",
            "study",
            "tutor"
        ),
        "Работа / Бизнес / Профессии / Финансы" to listOf(
            "business",
            "work",
            "professional",
            "finance",
            "career",
            "productivity"
        ),
        "Творчество / Медиа / Контент / Персонажи" to listOf(
            "creative",
            "content",
            "media",
            "character",
            "roleplay",
            "storytelling",
            "writing"
        ),
        "Узкоспециализированные" to listOf(
            "specialized",
            "niche",
            "expert",
            "specific"
        ),
        "СВЕЖИЕ ПРОМПТЫ" to listOf(
            "fresh",
            "new",
            "latest",
            "recent"
        ),
        "ЕЖЕНЕДЕЛЬНЫЕ ДАЙДЖЕСТЫ ПРОМПТОВ (ВЫХОДЯТ ПО ПОНЕДЕЛЬНИКАМ)" to listOf(
            "weekly",
            "digest",
            "collection",
            " compilation"
        )
    )

    /**
     * Get tags for a given category name.
     * @param categoryName The category name as it appears on 4pda forum
     * @return List of tags associated with the category
     */
    fun getTagsForCategory(categoryName: String?): List<String> {
        if (categoryName == null) return emptyList()
        return categoryToTags[categoryName] ?: emptyList()
    }

    /**
     * Get all registered category names.
     * @return List of all category names
     */
    fun getAllCategories(): List<String> = categoryToTags.keys.toList()

    /**
     * Check if a category is registered.
     * @param categoryName The category name to check
     * @return True if category is registered
     */
    fun isKnownCategory(categoryName: String?): Boolean {
        if (categoryName == null) return false
        return categoryToTags.containsKey(categoryName)
    }

    /**
     * Get all available tags.
     * @return Set of all tags from all categories
     */
    fun getAllTags(): Set<String> = categoryToTags.values.flatten().toSet()

    /**
     * Get tags for a category with fallback to auto-tagging.
     * @param categoryName The category name
     * @param promptText The prompt text for auto-tagging
     * @return Combined list of category tags and auto-detected tags
     */
    fun getTagsWithAutoDetect(categoryName: String?, promptText: String): List<String> {
        val tags = mutableListOf<String>()
        
        // Add category tags
        tags.addAll(getTagsForCategory(categoryName))
        
        // Auto-detect tags based on content
        tags.addAll(autoDetectTags(promptText))
        
        return tags.distinct()
    }

    /**
     * Auto-detect tags based on prompt content.
     * Simplified version of ProcessScrapedPostsUseCase.extractTags
     */
    private fun autoDetectTags(text: String): List<String> {
        val tags = mutableListOf<String>()
        val lowerText = text.lowercase()
        
        when {
            // AI Models
            lowerText.contains("gpt") || lowerText.contains("chatgpt") -> tags.add("gpt")
            lowerText.contains("claude") -> tags.add("claude")
            lowerText.contains("gemini") -> tags.add("gemini")
            lowerText.contains("deepseek") -> tags.add("deepseek")
            lowerText.contains("grok") -> tags.add("grok")
            lowerText.contains("mistral") -> tags.add("mistral")
            
            // Content Types
            lowerText.contains("image") || lowerText.contains("нарисуй") || 
            lowerText.contains("рисунок") || lowerText.contains("изображение") -> tags.add("image")
            
            lowerText.contains("code") || lowerText.contains("код") || 
            lowerText.contains("программирован") || lowerText.contains("функция") -> tags.add("code")
            
            lowerText.contains("role") || lowerText.contains("роль") || 
            lowerText.contains("персонаж") || lowerText.contains("character") -> tags.add("roleplay")
            
            lowerText.contains("translate") || lowerText.contains("перевод") || 
            lowerText.contains("язык") -> tags.add("translation")
            
            lowerText.contains("анализ") || lowerText.contains("analysis") -> tags.add("analysis")
            
            lowerText.contains("текст") || lowerText.contains("text") || 
            lowerText.contains("писать") || lowerText.contains("write") -> tags.add("text")
            
            lowerText.contains("суммариз") || lowerText.contains("summary") -> tags.add("summary")
            
            lowerText.contains("вопрос") || lowerText.contains("question") || 
            lowerText.contains("ответ") || lowerText.contains("answer") -> tags.add("qa")
            
            // Special Features
            lowerText.contains("файл") || lowerText.contains("file") -> tags.add("file")
            lowerText.contains("таблиц") || lowerText.contains("table") -> tags.add("table")
            lowerText.contains("список") || lowerText.contains("list") -> tags.add("list")
            
            // Quality
            lowerText.contains("качеств") || lowerText.contains("quality") -> tags.add("quality")
            lowerText.contains("точн") || lowerText.contains("accuracy") -> tags.add("accuracy")
        }
        
        return tags
    }

    /**
     * Convert category name to slug for filename use.
     * @param categoryName The category name
     * @return URL-safe slug
     */
    fun categoryToSlug(categoryName: String?): String {
        if (categoryName == null) return "unknown"
        return categoryName
            .lowercase()
            .replace(Regex("[^a-zа-я0-9\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
    }
}
