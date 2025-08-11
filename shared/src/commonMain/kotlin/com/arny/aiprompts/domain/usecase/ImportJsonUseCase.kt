package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.data.mappers.toDomain
import com.arny.aiprompts.data.model.PromptJson
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import kotlinx.serialization.json.Json

class ImportJsonUseCase(
    private val repository: IPromptsRepository,
    private val fileDataSource: FileDataSource // Платформо-независимый источник файлов
) {
    private val jsonParser = Json { isLenient = true; ignoreUnknownKeys = true }

    suspend operator fun invoke(): Result<Int> {
        return runCatching {
            // 1. Получаем все JSON-файлы
            val jsonFiles = fileDataSource.getPromptFiles()

            // 2. Читаем и парсим каждый файл
            val prompts = jsonFiles.mapNotNull { file ->
                try {
                    val content = file.readText()
                    val promptJson = jsonParser.decodeFromString<PromptJson>(content)
                    // Используем маппер, который мы сейчас исправим
                    promptJson.toDomain()
                } catch (e: Exception) {
                    // Логируем ошибку для конкретного файла и продолжаем
                    println("Ошибка парсинга файла ${file.name}: ${e.message}")
                    null
                }
            }

            // 3. Сохраняем все успешно распарсенные промпты
            repository.savePrompts(prompts)

            // 4. Возвращаем количество
            prompts.size
        }
    }
}