package com.arny.aiprompts.data.files

import android.content.Context
import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.domain.interfaces.FileDataSource
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

class FileDataSourceImpl(private val context: Context) : FileDataSource {
    // Используем Json с красивым форматированием для читаемости файлов
    private val json = Json { prettyPrint = true }

    override fun getParsedPromptsDirectory(): File {
        val dir = File(context.filesDir, "parsed_prompts")
        dir.mkdirs()
        return dir
    }

    override suspend fun savePromptJson(promptJson: PromptJson): File {
        // Сохраняем в папку prompts в выбранной категории
        val promptsDir = File(context.filesDir, "prompts")
        if (!promptsDir.exists()) {
            promptsDir.mkdirs()
        }

        // Используем категорию из promptJson, если она пустая - используем "general"
        val category = promptJson.category?.takeIf { it.isNotBlank() } ?: "general"
        val categoryDir = File(promptsDir, category)

        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }

        val targetFile = File(categoryDir, "${promptJson.id}.json")
        val jsonString = json.encodeToString(PromptJson.serializer(), promptJson)
        targetFile.writeText(jsonString, StandardCharsets.UTF_8)

        return targetFile
    }

    override suspend fun getPromptFiles(): List<PlatformFile> {
        val promptsDir = File(context.filesDir, "prompts")

        if (!promptsDir.exists() || !promptsDir.isDirectory) {
            return emptyList()
        }

        val jsonFiles = promptsDir.walk().filter { it.isFile && it.extension == "json" }.toList()

        return jsonFiles.map { PlatformFile(it) }
    }
}