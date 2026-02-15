package com.arny.aiprompts.domain.analysis

/**
 * Maps 4pda forum categories to application categories and tags.
 * Provides category-to-tags mapping for prompt categorization.
 */
object CategoryTagMapper {

    /**
     * Application categories (folder names in /prompts).
     * These are the valid categories that exist in the app.
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
     * Map of 4pda category names to application category names.
     * This is the main mapping for categorization.
     */
    private val CATEGORY_MAPPING = mapOf(
        // Universal/Meta
        "Универсальные / Метапромпты" to "general",
        "Универсальные" to "general",
        "Метапромпты" to "general",

        // Bypass/Censorship
        "Обход цензуры и ограничений" to "technology",
        "Jailbreak" to "technology",

        // Education
        "Обучение / Языки / Учёба" to "education",
        "Обучение" to "education",
        "Языки" to "education",
        "Учёба" to "education",

        // Business/Work
        "Работа / Бизнес / Профессии / Финансы" to "business",
        "Работа" to "business",
        "Бизнес" to "business",
        "Профессии" to "business",
        "Финансы" to "business",

        // Creative
        "Творчество / Медиа / Контент / Персонажи" to "creative",
        "Творчество" to "creative",
        "Медиа" to "creative",
        "Контент" to "creative",
        "Персонажи" to "creative",

        // Specialized
        "Узкоспециализированные" to "technology",
        "Специализированные" to "technology",

        // Fresh/Weekly digests - map to general
        "СВЕЖИЕ ПРОМПТЫ" to "general",
        "ЕЖЕНЕДЕЛЬНЫЕ ДАЙДЖЕСТЫ ПРОМПТОВ (ВЫХОДЯТ ПО ПОНЕДЕЛЬНИКАМ)" to "general",
        "Дайджесты" to "general",

        // Additional mappings based on content
        "Юридические" to "legal",
        "Медицина" to "healthcare",
        "Здоровье" to "healthcare",
        "Наука" to "science",
        "Развлечения" to "entertainment",
        "Игры" to "entertainment",
        "Маркетинг" to "marketing",
        "Реклама" to "marketing",
        "SEO" to "marketing",
        "Экология" to "environment",
        "Окружающая среда" to "environment",
        "Модели" to "model_specific",
        "AI Models" to "model_specific",
        "Задачи" to "common_tasks",
        "Утилиты" to "common_tasks"
    )

    /**
     * Map of 4pda category names to application tags.
     */
    private val CATEGORY_TO_TAGS = mapOf(
        "Универсальные / Метапромпты" to listOf(
            "universal", "metaprompt", "gpt", "claude", "general"
        ),
        "Обход цензуры и ограничений" to listOf(
            "jailbreak", "censorship", "bypass", "restriction"
        ),
        "Обучение / Языки / Учёба" to listOf(
            "education", "learning", "language", "study", "tutor"
        ),
        "Работа / Бизнес / Профессии / Финансы" to listOf(
            "business", "work", "professional", "finance", "career", "productivity"
        ),
        "Творчество / Медиа / Контент / Персонажи" to listOf(
            "creative", "content", "media", "character", "roleplay", "storytelling", "writing"
        ),
        "Узкоспециализированные" to listOf(
            "specialized", "niche", "expert", "specific"
        ),
        "СВЕЖИЕ ПРОМПТЫ" to listOf(
            "fresh", "new", "latest", "recent"
        ),
        "ЕЖЕНЕДЕЛЬНЫЕ ДАЙДЖЕСТЫ ПРОМПТОВ (ВЫХОДЯТ ПО ПОНЕДЕЛЬНИКАМ)" to listOf(
            "weekly", "digest", "collection", "compilation"
        )
    )

    /**
     * Map 4pda category name to application category.
     * This is the main function for categorization.
     *
     * @param category4pda The category name as it appears on 4pda forum
     * @return Application category name (one of APP_CATEGORIES)
     */
    fun mapToAppCategory(category4pda: String?): String {
        if (category4pda.isNullOrBlank()) return "general"

        // Direct mapping
        CATEGORY_MAPPING[category4pda]?.let { return it }

        // Try partial matching
        CATEGORY_MAPPING.forEach { (key, value) ->
            if (category4pda.contains(key, ignoreCase = true) ||
                key.contains(category4pda, ignoreCase = true)) {
                return value
            }
        }

        // Auto-detect from keywords in category name
        return autoDetectCategory(category4pda)
    }

    /**
     * Auto-detect category from category name keywords.
     */
    private fun autoDetectCategory(categoryName: String): String {
        val lower = categoryName.lowercase()

        return when {
            // Business
            lower.contains("бизнес") || lower.contains("работа") ||
            lower.contains("финанс") || lower.contains("карьера") ||
            lower.contains("office") || lower.contains("profession") -> "business"

            // Creative
            lower.contains("творч") || lower.contains("медиа") ||
            lower.contains("контент") || lower.contains("персонаж") ||
            lower.contains("creative") || lower.contains("writing") ||
            lower.contains("story") || lower.contains("art") -> "creative"

            // Education
            lower.contains("обучение") || lower.contains("учёба") ||
            lower.contains("язык") || lower.contains("education") ||
            lower.contains("learn") || lower.contains("study") ||
            lower.contains("tutor") || lower.contains("курс") -> "education"

            // Technology
            lower.contains("техн") || lower.contains("программ") ||
            lower.contains("код") || lower.contains("tech") ||
            lower.contains("code") || lower.contains("jailbreak") ||
            lower.contains(" bypass") || lower.contains("специализированные") -> "technology"

            // Marketing
            lower.contains("маркетинг") || lower.contains("реклама") ||
            lower.contains("seo") || lower.contains("marketing") ||
            lower.contains("smm") || lower.contains("продаж") -> "marketing"

            // Entertainment
            lower.contains("развлеч") || lower.contains("игр") ||
            lower.contains("фильм") || lower.contains("entertainment") ||
            lower.contains("game") || lower.contains("movie") -> "entertainment"

            // Healthcare
            lower.contains("медицин") || lower.contains("здоров") ||
            lower.contains("health") || lower.contains("medicine") ||
            lower.contains("doctor") -> "healthcare"

            // Legal
            lower.contains("юрид") || lower.contains("закон") ||
            lower.contains("право") || lower.contains("legal") ||
            lower.contains("law") || lower.contains("contract") -> "legal"

            // Science
            lower.contains("наука") || lower.contains("исслед") ||
            lower.contains("science") || lower.contains("research") -> "science"

            // Environment
            lower.contains("эколог") || lower.contains("окружающ") ||
            lower.contains("environment") || lower.contains("nature") ||
            lower.contains("green") -> "environment"

            // Model specific
            lower.contains("модель") || lower.contains("gpt") ||
            lower.contains("claude") || lower.contains("model") ||
            lower.contains("llm") || lower.contains("ai specific") -> "model_specific"

            // Common tasks
            lower.contains("задач") || lower.contains("утилит") ||
            lower.contains("tasks") || lower.contains("utility") ||
            lower.contains("tool") -> "common_tasks"

            // Default to general
            else -> "general"
        }
    }

    /**
     * Get tags for a given category name.
     * @param categoryName The category name as it appears on 4pda forum
     * @return List of tags associated with the category
     */
    fun getTagsForCategory(categoryName: String?): List<String> {
        if (categoryName == null) return emptyList()
        return CATEGORY_TO_TAGS[categoryName] ?: emptyList()
    }

    /**
     * Get all registered category names.
     * @return List of all category names
     */
    fun getAllCategories(): List<String> = CATEGORY_MAPPING.keys.toList()

    /**
     * Check if a category is registered.
     * @param categoryName The category name to check
     * @return True if category is registered
     */
    fun isKnownCategory(categoryName: String?): Boolean {
        if (categoryName == null) return false
        return CATEGORY_MAPPING.containsKey(categoryName)
    }

    /**
     * Get all available tags.
     * @return Set of all tags from all categories
     */
    fun getAllTags(): Set<String> = CATEGORY_TO_TAGS.values.flatten().toSet()

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

        // Add category-based tag from mapping
        val appCategory = mapToAppCategory(categoryName)
        if (appCategory !in tags) {
            tags.add(appCategory)
        }

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
