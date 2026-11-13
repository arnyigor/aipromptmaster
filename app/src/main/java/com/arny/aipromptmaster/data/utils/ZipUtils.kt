package com.arny.aipromptmaster.data.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipInputStream

object ZipUtils {

    /**
     * @param onProgress вызывается по мере чтения архива: (прочитано байт, всего байт или -1,
     * если размер неизвестен). Нужен для индикатора распаковки.
     */
    suspend fun extractZip(
        inputStream: InputStream,
        extractDir: File,
        totalBytes: Long = -1L,
        onProgress: ((read: Long, total: Long) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!extractDir.exists()) {
                extractDir.mkdirs()
            }

            val counting = if (onProgress == null) inputStream else CountingInputStream(inputStream) { read ->
                onProgress(read, totalBytes)
            }
            ZipInputStream(BufferedInputStream(counting)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry

                while (entry != null) {
                    val file = File(extractDir, entry.name)
                    require(file.canonicalPath.startsWith(extractDir.canonicalPath + File.separator)) {
                        "ZIP entry escapes extraction directory"
                    }

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

    /** Поток, сообщающий о прочитанных байтах (для прогресса распаковки). */
    private class CountingInputStream(
        private val delegate: InputStream,
        private val onRead: (Long) -> Unit,
    ) : FilterInputStream(delegate) {
        private var read = 0L

        override fun read(): Int = super.read().also { if (it >= 0) onRead(++read) }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) { read += it; onRead(read) } }
    }

    suspend fun readJsonFilesFromDirectory(
        directory: File
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val jsonFiles = mutableListOf<Pair<String, String>>()

        directory.walkTopDown()
            .filter { it.isFile && it.extension == "json" && it.name != com.arny.promptcontract.CatalogManifest.FILE_NAME }
            .forEach { file ->
                val category = file.parentFile?.name ?: "uncategorized"
                val content = file.readText()
                jsonFiles.add(category to content)
            }

        jsonFiles
    }
}
