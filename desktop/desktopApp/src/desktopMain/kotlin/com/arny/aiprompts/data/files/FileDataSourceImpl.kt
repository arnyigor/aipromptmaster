package com.arny.aiprompts.data.files

import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.data.model.createPlatformFile
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
        val rootDir = File(System.getProperty("user.home"), ".aiprompts")

        val promptsDir = File(rootDir, "personal_prompts")
        if (!promptsDir.exists()) {
            promptsDir.mkdirs()
        }

        val category = promptJson.category?.takeIf { it.matches(Regex("[a-z0-9_-]+")) } ?: "general"
        require(promptJson.id?.matches(Regex("[A-Za-z0-9_-]+")) == true) { "Invalid prompt ID" }
        val categoryDir = File(promptsDir, category)

        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }

        val targetFile = File(categoryDir, "${promptJson.id}.json")
        val jsonString = json.encodeToString(promptJson.copy(isLocal = true))
        targetFile.writeText(jsonString, StandardCharsets.UTF_8)

        return targetFile
    }

    override suspend fun getPromptFiles(): List<PlatformFile> {
        val rootDir = File(System.getProperty("user.home"), ".aiprompts")
        if (rootDir == null) {
            log("❌ Не удалось найти корневую директорию проекта (.git)")
            return emptyList()
        }

        val promptsDir = File(rootDir, "personal_prompts")

        if (!promptsDir.exists() || !promptsDir.isDirectory) {
            log("⚠️ Папка 'prompts' не найдена: ${promptsDir.absolutePath}")
            return emptyList()
        }

        log("✅ [DEV] Найдена папка 'prompts': ${promptsDir.absolutePath}")

        val jsonFiles = promptsDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .toList()

        log("📊 [DEV] Найдено ${jsonFiles.size} JSON файлов")

        return jsonFiles.map { createPlatformFile(it.absolutePath) as PlatformFile }
    }

    // --- ИСПРАВЛЕНИЕ КОДИРОВКИ В ЛОГАХ ---
    // Вспомогательная функция для вывода в консоль с правильной кодировкой
    private fun log(message: String) {
        try {
            // Явно кодируем строку в UTF-8 и выводим байты
            System.out.write((message + "\n").toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            // Fallback, если что-то пошло не так
            println(message)
        }
    }
}