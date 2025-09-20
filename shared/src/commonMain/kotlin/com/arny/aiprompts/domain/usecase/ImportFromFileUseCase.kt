package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.model.PromptMetadata
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.model.PromptContent
import kotlinx.serialization.json.Json

enum class ImportFormat {
    JSON,
    CSV,
    MARKDOWN,
    PLAIN_TEXT
}

class ImportFromFileUseCase(
    private val promptsRepository: IPromptsRepository
) {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    suspend operator fun invoke(
        fileContent: String,
        format: ImportFormat
    ): Result<Int> {
        return runCatching {
            val prompts = when (format) {
                ImportFormat.JSON -> parseJson(fileContent)
                ImportFormat.CSV -> parseCsv(fileContent)
                ImportFormat.MARKDOWN -> parseMarkdown(fileContent)
                ImportFormat.PLAIN_TEXT -> parsePlainText(fileContent)
            }

            promptsRepository.savePrompts(prompts)
            prompts.size
        }
    }

    private fun parseJson(content: String): List<Prompt> {
        return try {
            val promptJsons = json.decodeFromString<List<PromptJson>>(content)
            promptJsons.map { it.toDomain() }
        } catch (_: Exception) {
            val promptJson = json.decodeFromString<PromptJson>(content)
            listOf(promptJson.toDomain())
        }
    }

    private fun parseCsv(content: String): List<Prompt> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        val headers = lines.first().split(",").map { it.trim() }
        val dataLines = lines.drop(1)

        return dataLines.mapNotNull { line ->
            try {
                val values = parseCsvLine(line)
                if (values.size < headers.size) return@mapNotNull null

                val title = values.getOrNull(headers.indexOf("Title"))?.removeSurrounding("\"") ?: ""
                val description = values.getOrNull(headers.indexOf("Description"))?.removeSurrounding("\"")
                val category = values.getOrNull(headers.indexOf("Category")) ?: "imported"
                val tagsStr = values.getOrNull(headers.indexOf("Tags")) ?: ""
                val contentRu = values.getOrNull(headers.indexOf("Content RU"))?.removeSurrounding("\"")
                val contentEn = values.getOrNull(headers.indexOf("Content EN"))?.removeSurrounding("\"")

                if (title.isBlank()) return@mapNotNull null

                Prompt(
                    id = "",
                    title = title,
                    description = description?.takeIf { it.isNotBlank() },
                    content = PromptContent(
                        ru = contentRu?.takeIf { it.isNotBlank() }.orEmpty(),
                        en = contentEn?.takeIf { it.isNotBlank() }.orEmpty()
                    ),
                    category = category,
                    tags = tagsStr.split(";").map { it.trim() }.filter { it.isNotBlank() },
                    status = "active",
                    isLocal = false,
                    isFavorite = false,
                    rating = 0.0f,
                    ratingVotes = 0,
                    compatibleModels = emptyList(),
                    metadata = PromptMetadata(
                        author = null,
                        source = "imported",
                        notes = ""
                    ),
                    version = "1.0.0",
                    createdAt = null,
                    modifiedAt = null
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = ""
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' && !inQuotes -> inQuotes = true
                char == '"' && inQuotes -> inQuotes = false
                char == ',' && !inQuotes -> {
                    result.add(current)
                    current = ""
                }
                else -> current += char
            }
        }

        result.add(current)
        return result
    }

    private fun parseMarkdown(content: String): List<Prompt> {
        val prompts = mutableListOf<Prompt>()
        val sections = content.split("---").filter { it.trim().isNotBlank() }

        sections.forEach { section ->
            try {
                val lines = section.lines()
                var title = ""
                var description: String? = null
                var category = "imported"
                val tags = mutableListOf<String>()
                var contentRu: String? = null
                var contentEn: String? = null

                var currentSection = ""
                var currentContent = StringBuilder()

                lines.forEach { line ->
                    when {
                        line.startsWith("# ") && title.isBlank() -> {
                            title = line.removePrefix("# ").trim()
                        }
                        line.startsWith("**Описание:**") -> {
                            description = line.removePrefix("**Описание:**").trim()
                        }
                        line.startsWith("**Категория:**") -> {
                            category = line.removePrefix("**Категория:**").trim()
                        }
                        line.startsWith("**Теги:**") -> {
                            val tagsStr = line.removePrefix("**Теги:**").trim()
                            tags.addAll(tagsStr.split(",").map { it.trim() })
                        }
                        line.startsWith("## Русский контент") -> {
                            currentSection = "ru"
                            currentContent = StringBuilder()
                        }
                        line.startsWith("## English content") -> {
                            currentSection = "en"
                            currentContent = StringBuilder()
                        }
                        line.trim().isNotBlank() && currentSection.isNotBlank() -> {
                            if (!line.startsWith("##")) {
                                currentContent.appendLine(line)
                            }
                        }
                    }
                }

                when (currentSection) {
                    "ru" -> contentRu = currentContent.toString().trim()
                    "en" -> contentEn = currentContent.toString().trim()
                }

                if (title.isNotBlank()) {
                    prompts.add(
                        Prompt(
                            id = "",
                            title = title,
                            description = description,
                            content = PromptContent(
                                ru = contentRu.orEmpty(),
                                en = contentEn.orEmpty()
                            ),
                            category = category,
                            tags = tags,
                            status = "active",
                            isLocal = false,
                            isFavorite = false,
                            rating = 0.0f,
                            ratingVotes = 0,
                            compatibleModels = emptyList(),
                            metadata = PromptMetadata(
                                author = null,
                                source = "imported",
                                notes = ""
                            ),
                            version = "1.0.0",
                            createdAt = null,
                            modifiedAt = null
                        )
                    )
                }
            } catch (_: Exception) {
                // Пропускаем невалидные секции
            }
        }

        return prompts
    }

    private fun parsePlainText(content: String): List<Prompt> {
        val prompts = mutableListOf<Prompt>()
        val sections = content.split("================================")
            .filter { it.trim().isNotBlank() }

        sections.forEach { section ->
            try {
                val lines = section.lines()
                var title = ""
                var description: String? = null
                var category = "imported"
                val tags = mutableListOf<String>()
                var contentRu: String? = null
                var contentEn: String? = null

                var currentSection = ""
                var currentContent = StringBuilder()

                lines.forEach { line ->
                    when {
                        line.startsWith("ЗАГОЛОВОК: ") -> {
                            title = line.removePrefix("ЗАГОЛОВОК: ").trim()
                        }
                        line.startsWith("ОПИСАНИЕ: ") -> {
                            description = line.removePrefix("ОПИСАНИЕ: ").trim()
                        }
                        line.startsWith("КАТЕГОРИЯ: ") -> {
                            category = line.removePrefix("КАТЕГОРИЯ: ").trim()
                        }
                        line.startsWith("ТЕГИ: ") -> {
                            val tagsStr = line.removePrefix("ТЕГИ: ").trim()
                            tags.addAll(tagsStr.split(",").map { it.trim() })
                        }
                        line.startsWith("РУССКИЙ КОНТЕНТ:") -> {
                            currentSection = "ru"
                            currentContent = StringBuilder()
                        }
                        line.startsWith("ENGLISH CONTENT:") -> {
                            currentSection = "en"
                            currentContent = StringBuilder()
                        }
                        currentSection.isNotBlank() && !line.startsWith("РУССКИЙ КОНТЕНТ:") && !line.startsWith("ENGLISH CONTENT:") -> {
                            currentContent.appendLine(line)
                        }
                    }
                }

                when (currentSection) {
                    "ru" -> contentRu = currentContent.toString().trim()
                    "en" -> contentEn = currentContent.toString().trim()
                }

                if (title.isNotBlank()) {
                    prompts.add(
                        Prompt(
                            id = "",
                            title = title,
                            description = description,
                            content = PromptContent(
                                ru = contentRu.orEmpty(),
                                en = contentEn.orEmpty()
                            ),
                            category = category,
                            tags = tags,
                            status = "active",
                            isLocal = false,
                            isFavorite = false,
                            rating = 0.0f,
                            ratingVotes = 0,
                            compatibleModels = emptyList(),
                            metadata = PromptMetadata(
                                author = null,
                                source = "imported",
                                notes = ""
                            ),
                            version = "1.0.0",
                            createdAt = null,
                            modifiedAt = null
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        return prompts
    }
}