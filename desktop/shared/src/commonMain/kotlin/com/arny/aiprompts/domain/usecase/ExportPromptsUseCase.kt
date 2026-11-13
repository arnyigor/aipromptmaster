@file:OptIn(ExperimentalTime::class)

package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.data.mappers.toExportJson
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

enum class ExportFormat {
    JSON,
    CSV,
    MARKDOWN,
    PLAIN_TEXT
}

class ExportPromptsUseCase(
    private val promptsRepository: IPromptsRepository
) {
    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(
        format: ExportFormat,
        category: String? = null,
        tags: List<String> = emptyList(),
        searchQuery: String = ""
    ): Result<String> {
        return runCatching {
            val prompts = promptsRepository.getPrompts(
                search = searchQuery,
                category = category,
                tags = tags,
                limit = Int.MAX_VALUE
            )

            when (format) {
                ExportFormat.JSON -> exportAsJson(prompts)
                ExportFormat.CSV -> exportAsCsv(prompts)
                ExportFormat.MARKDOWN -> exportAsMarkdown(prompts)
                ExportFormat.PLAIN_TEXT -> exportAsPlainText(prompts)
            }
        }
    }

    private fun exportAsJson(prompts: List<Prompt>): String =
        json.encodeToString(prompts.map { it.toExportJson() })

    private fun exportAsCsv(prompts: List<Prompt>): String {
        if (prompts.isEmpty()) return ""

        val headers = listOf(
            "ID",
            "Title",
            "Description",
            "Category",
            "Tags",
            "Content RU",
            "Content EN",
            "Created At"
        )
        val csvLines = mutableListOf(headers.joinToString(","))

        prompts.forEach { prompt ->
            val line = listOf(
                prompt.id,
                "\"${prompt.title.replace("\"", "\"\"")}\"",
                "\"${prompt.description?.replace("\"", "\"\"") ?: ""}\"",
                prompt.category,
                prompt.tags.joinToString(";"),
                "\"${prompt.content?.ru?.replace("\"", "\"\"") ?: ""}\"",
                "\"${prompt.content?.en?.replace("\"", "\"\"") ?: ""}\"",
                prompt.createdAt?.toString() ?: ""
            )
            csvLines.add(line.joinToString(","))
        }

        return csvLines.joinToString("\n")
    }

    private fun exportAsMarkdown(prompts: List<Prompt>): String {
        val markdown = StringBuilder()

        prompts.forEach { prompt ->
            markdown.append("# ${prompt.title}\n\n")

            if (!prompt.description.isNullOrBlank()) {
                markdown.append("**Описание:** ${prompt.description}\n\n")
            }

            markdown.append("**Категория:** ${prompt.category}\n")
            markdown.append("**Теги:** ${prompt.tags.joinToString(", ")}\n")
            markdown.append("**ID:** ${prompt.id}\n\n")

            prompt.content?.ru?.let { ruContent ->
                markdown.append("## Русский контент\n\n${ruContent}\n\n")
            }

            prompt.content?.en?.let { enContent ->
                markdown.append("## English content\n\n${enContent}\n\n")
            }

            markdown.append("---\n\n")
        }

        return markdown.toString()
    }

    private fun exportAsPlainText(prompts: List<Prompt>): String {
        val text = StringBuilder()

        prompts.forEach { prompt ->
            text.append("================================\n")
            text.append("ЗАГОЛОВОК: ${prompt.title}\n")

            if (!prompt.description.isNullOrBlank()) {
                text.append("ОПИСАНИЕ: ${prompt.description}\n")
            }

            text.append("КАТЕГОРИЯ: ${prompt.category}\n")
            text.append("ТЕГИ: ${prompt.tags.joinToString(", ")}\n")
            text.append("ID: ${prompt.id}\n\n")

            prompt.content?.ru?.let { ruContent ->
                text.append("РУССКИЙ КОНТЕНТ:\n${ruContent}\n\n")
            }

            prompt.content?.en?.let { enContent ->
                text.append("ENGLISH CONTENT:\n${enContent}\n\n")
            }

            text.append("\n")
        }

        return text.toString()
    }
}