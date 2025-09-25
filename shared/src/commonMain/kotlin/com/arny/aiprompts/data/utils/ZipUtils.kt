package com.arny.aiprompts.data.utils

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

object ZipUtils {

    fun extractZip(byteArray: ByteArray, destinationDir: File): Result<Unit> {
        return runCatching {
            destinationDir.mkdirs()
            byteArray.inputStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val filePath = File(destinationDir, entry.name)
                        if (entry.isDirectory) {
                            filePath.mkdirs()
                        } else {
                            filePath.parentFile?.mkdirs()
                            filePath.outputStream().use { zipIn.copyTo(it) }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        }
    }

    fun readJsonFilesFromDirectory(dir: File): Map<String, String> {
        val jsonFiles = mutableMapOf<String, String>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "json") {
                val category = file.parentFile?.name ?: "unknown"
                val content = file.readText()
                jsonFiles[category] = content
            }
        }
        return jsonFiles
    }
}