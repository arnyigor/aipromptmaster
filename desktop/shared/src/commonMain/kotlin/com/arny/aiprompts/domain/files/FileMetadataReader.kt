package com.arny.aiprompts.domain.files

import com.arny.aiprompts.data.parser.JsonSimpleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileMetadataReader {
    /**
     * Асинхронно сканирует все поддиректории в поисках .json файлов
     * и извлекает из них 'source_id'.
     *
     * @return Map<String, String> где ключ - source_id, значение - абсолютный путь к файлу.
     */
    suspend fun readAllSourceIds(promptsRootDir: File): Map<String, String> = withContext(Dispatchers.IO) {
        val sourceIdToFileMap = mutableMapOf<String, String>()

        promptsRootDir.walk()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                try {
                    val sourceId = JsonSimpleParser.parseSourceId(file.readText())
                    if (sourceId != null) {
                        sourceIdToFileMap[sourceId] = file.absolutePath
                    }
                } catch (e: Exception) {
                    println("⚠️ Не удалось прочитать metadata из файла: ${file.name}, ошибка: ${e.message}")
                }
            }

        return@withContext sourceIdToFileMap
    }
}