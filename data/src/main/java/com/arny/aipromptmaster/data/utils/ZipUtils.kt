package com.arny.aipromptmaster.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipInputStream

object ZipUtils {

    suspend fun extractZip(
        inputStream: InputStream,
        extractDir: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!extractDir.exists()) {
                extractDir.mkdirs()
            }

            ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    val file = File(extractDir, entry.name)

                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        // Создаем родительские директории если их нет
                        file.parentFile?.mkdirs()

                        // Записываем файл
                        FileOutputStream(file).use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }

                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        }
    }

    suspend fun readJsonFilesFromDirectory(
        directory: File
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val jsonFiles = mutableListOf<Pair<String, String>>()

        directory.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                val category = file.parentFile?.name ?: "uncategorized"
                val content = file.readText()
                jsonFiles.add(category to content)
            }

        jsonFiles
    }
}
