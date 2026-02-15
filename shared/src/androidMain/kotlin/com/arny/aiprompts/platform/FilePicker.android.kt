package com.arny.aiprompts.platform

import android.content.Context
import android.net.Uri
import com.arny.aiprompts.data.model.PlatformFile
import com.arny.aiprompts.data.model.createPlatformFile

/**
 * Android реализация FilePicker.
 * Использует Activity Result API для выбора файлов.
 * 
 * Примечание: Этот класс не использует @Composable напрямую.
 * Для использования в Compose, используйте Activity Result API напрямую
 * или создайте Composable обертку на уровне приложения.
 */
class AndroidFilePicker(
    private val onFilePicked: (PlatformFile) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) : FilePicker {
    
    override fun launch() {
        // Этот метод не работает на Android без Activity
        // Используйте Activity Result API напрямую
    }

    override fun launchMultiple() {
        // Этот метод не работает на Android без Activity
    }
}

/**
 * Вспомогательная функция для создания PlatformFile из URI на Android.
 * 
 * @param context Android Context
 * @param uriString URI файла (content://...)
 * @return PlatformFile
 */
fun createPlatformFileFromUri(context: Context, uriString: String): PlatformFile {
    // На Android URI - это content:// схема
    // Для работы с ней нужен Context, чтобы открыть InputStream
    // PlatformFile на Android - это обертка над java.io.File
    
    // Копируем файл во временную директорию если это content:// URI
    return if (uriString.startsWith("content://")) {
        val uri = Uri.parse(uriString)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uriString")
        
        // Создаем временный файл
        val tempFile = java.io.File(context.cacheDir, "temp_file_${System.currentTimeMillis()}")
        tempFile.outputStream().use { output ->
            inputStream.copyTo(output)
        }
        
        createPlatformFile(tempFile.absolutePath)
    } else {
        // Это может быть file:// URI
        val file = java.io.File(uriString.substringAfter("file://"))
        createPlatformFile(file.absolutePath)
    }
}
