package com.arny.aiprompts.domain.files

/**
 * Platform-specific обработчик файлов для чтения и конвертации в Base64.
 * Используется для подготовки файлов к отправке в LLM API.
 */
expect class PlatformFileHandler {
    /**
     * Читает изображение и конвертирует его в Base64.
     * 
     * @param uriString URI файла (platform-specific)
     * @return Base64 строка
     */
    suspend fun readImageToBase64(uriString: String): String

    /**
     * Читает текстовый файл.
     * 
     * @param uriString URI файла
     * @return Содержимое файла как строка
     */
    suspend fun readText(uriString: String): String

    /**
     * Определяет MIME тип файла по расширению или содержимому.
     * 
     * @param uriString URI файла
     * @return MIME тип (например, "image/jpeg", "text/plain")
     */
    fun getMimeType(uriString: String): String?

    /**
     * Копирует файл во внутреннее хранилище приложения.
     * Необходимо для сохранения файлов между сессиями.
     * 
     * @param sourceUri Исходный URI
     * @param targetName Имя целевого файла
     * @return URI скопированного файла во внутреннем хранилище
     */
    suspend fun copyToInternalStorage(sourceUri: String, targetName: String): String

    /**
     * Проверяет существование файла.
     */
    fun fileExists(uriString: String): Boolean

    /**
     * Получает имя файла из URI.
     */
    fun getFileName(uriString: String): String?

    /**
     * Получает размер файла в байтах.
     */
    fun getFileSize(uriString: String): Long
}

/**
 * Фабрика для создания PlatformFileHandler.
 * Используется в DI.
 */
expect object PlatformFileHandlerFactory {
    fun create(): PlatformFileHandler
}
