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
        val rootDir = findProjectRootDir()
            ?: throw Exception("Не удалось найти корневую директорию проекта")

        val promptsDir = File(rootDir, "prompts")
        if (!promptsDir.exists()) {
            promptsDir.mkdirs()
        }

        val category = promptJson.category?.takeIf { it.isNotBlank() } ?: "general"
        val categoryDir = File(promptsDir, category)

        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }

        val targetFile = File(categoryDir, "${promptJson.id}.json")
        val jsonString = json.encodeToString(promptJson)
        targetFile.writeText(jsonString, StandardCharsets.UTF_8)

        return targetFile
    }

    private fun findProjectRootDir(): File? {
        // Начинаем с текущей рабочей директории
        var currentDir = File(System.getProperty("user.dir"))
        // Ищем корень проекта (где лежит папка .git), поднимаясь вверх по дереву
        // Ограничиваем поиск 10 уровнями, чтобы избежать бесконечного цикла
        repeat(10) {
            // Если нашли .git, значит, это корень репозитория
            if (File(currentDir, ".git").exists()) {
                return currentDir
            }
            // Если дошли до корня диска, останавливаемся
            if (currentDir.parentFile == null) {
                return null
            }
            // Поднимаемся на уровень выше
            currentDir = currentDir.parentFile
        }
        return null
    }

    override suspend fun getPromptFiles(): List<PlatformFile> {
        val rootDir = findProjectRootDir()
        if (rootDir == null) {
            log("❌ Не удалось найти корневую директорию проекта (.git)")
            return emptyList()
        }

        val promptsDir = File(rootDir, "prompts")

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