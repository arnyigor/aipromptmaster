package com.arny.aiprompts.data.files

import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.domain.interfaces.FileDataSource
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

class FileDataSourceImpl : FileDataSource {
    // Используем Json с красивым форматированием для читаемости файлов
    private val json = Json { prettyPrint = true }

    override fun getParsedPromptsDirectory(): File {
        val dir = File(System.getProperty("user.home"), ".aiprompts/parsed_prompts")
        dir.mkdirs()
        return dir
    }

    override suspend fun savePromptJson(promptJson: PromptJson): File {
        // Сохраняем в папку prompts в выбранной категории
        val rootDir = findProjectRootDir()
        if (rootDir == null) {
            log("Критическая ошибка: Не удалось найти корневую директорию проекта")
            throw Exception("Не удалось найти корневую директорию проекта")
        }

        val promptsDir = File(rootDir, "prompts")
        if (!promptsDir.exists()) {
            promptsDir.mkdirs()
            log("Создана директория prompts: ${promptsDir.absolutePath}")
        }

        // Используем категорию из promptJson, если она пустая - используем "imported"
        val category = promptJson.category?.takeIf { it.isNotBlank() } ?: "general"
        val categoryDir = File(promptsDir, category)

        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
            log("Создана директория категории: ${categoryDir.absolutePath}")
        }

        val targetFile = File(categoryDir, "${promptJson.id}.json")
        val jsonString = json.encodeToString(promptJson)
        targetFile.writeText(jsonString, StandardCharsets.UTF_8)

        log("Файл сохранен: ${targetFile.absolutePath}")
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
            log("Критическая ошибка: Не удалось найти корневую директорию проекта (где лежит .git).")
            return emptyList()
        }

        val promptsDir = File(rootDir, "prompts")

        if (!promptsDir.exists() || !promptsDir.isDirectory) {
            log("Папка 'prompts' не найдена по пути: ${promptsDir.absolutePath}")
            return emptyList()
        }

        log("Найдена папка 'prompts': ${promptsDir.absolutePath}")

        val javaFiles = promptsDir.walk().filter { it.isFile && it.extension == "json" }.toList()
        log("Найдено ${javaFiles.size} json файлов.")

        return javaFiles.map { PlatformFile(it) }
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