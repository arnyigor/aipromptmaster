package com.arny.aiprompts.data.files

import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.domain.interfaces.FileDataSource
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

class FileDataSourceImpl : FileDataSource {
    // Используем Json с красивым форматированием для читаемости файлов
    private val json = Json { prettyPrint = true }

    override fun getParsedPromptsDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".aiprompts/parsed_prompts")
        dir.mkdirs()
        return dir
    }

    override suspend fun savePromptJson(promptJson: PromptJson): File {
        val saveDir = getParsedPromptsDirectory()
        val targetFile = File(saveDir, "${promptJson.id}.json")
        val jsonString = json.encodeToString(promptJson)
        targetFile.writeText(jsonString, StandardCharsets.UTF_8)
        return targetFile
    }
}