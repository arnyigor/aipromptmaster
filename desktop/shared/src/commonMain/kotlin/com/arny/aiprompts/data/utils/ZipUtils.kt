package com.arny.aiprompts.data.utils

import java.io.File
import java.util.zip.ZipInputStream

object ZipUtils {

    fun extractZip(byteArray: ByteArray, destinationDir: File): Result<Unit> {
        return runCatching {
            destinationDir.mkdirs()

            var fileCount = 0
            var dirCount = 0

            byteArray.inputStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val filePath = File(destinationDir, entry.name)

                        // Защита от Zip Slip атаки
                        if (!filePath.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                            throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            filePath.mkdirs()
                            dirCount++
                        } else {
                            filePath.parentFile?.mkdirs()
                            filePath.outputStream().use { zipIn.copyTo(it) }
                            fileCount++
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            println("✅ [ZipUtils] Extracted $fileCount files, $dirCount directories to ${destinationDir.path}")
        }
    }

    /**
     * Читает все JSON файлы из директории рекурсивно.
     * ✅ ИСПРАВЛЕНИЕ: Теперь использует уникальный ключ для каждого файла.
     *
     * @return Map<uniqueKey, jsonContent>, где uniqueKey = "category/filename"
     */
    fun readJsonFilesFromDirectory(dir: File): Map<String, String> {
        val jsonFiles = mutableMapOf<String, String>()

        println("🔍 [ZipUtils] Scanning directory: ${dir.absolutePath}")

        dir.walkTopDown()
            .filter { file ->
                file.isFile &&
                        file.extension.equals("json", ignoreCase = true) &&
                        !file.name.startsWith(".") // Пропускаем скрытые файлы
            }
            .forEach { file ->
                try {
                    val category = file.parentFile?.name ?: "unknown"
                    val content = file.readText(Charsets.UTF_8)

                    // ✅ ИСПРАВЛЕНИЕ: Используем уникальный ключ
                    // Было: jsonFiles[category] = content  ← Перезаписывало!
                    // Стало: jsonFiles["category/filename"] = content
                    val uniqueKey = "${category}/${file.nameWithoutExtension}"
                    jsonFiles[uniqueKey] = content

                } catch (e: Exception) {
                    println("❌ [ZipUtils] Failed to read ${file.path}: ${e.message}")
                }
            }

        println("📊 [ZipUtils] Found ${jsonFiles.size} JSON files")

        // Выводим статистику по категориям
        val categoryCounts = jsonFiles.keys
            .groupBy { it.substringBefore("/") }
            .mapValues { it.value.size }

        categoryCounts.forEach { (category, count) ->
            println("   📁 $category: $count files")
        }

        return jsonFiles
    }
}
