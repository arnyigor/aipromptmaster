package com.arny.aipromptmaster.domain.models

import java.math.BigDecimal
import java.util.UUID

/**
 * Представляет одно сообщение в чате между пользователем и ассистентом.
 *
 * @property id уникальный идентификатор сообщения
 * @property role роль отправителя: пользователь, ассистент или системное сообщение
 * @property content текстовое содержимое сообщения
 * @property fileAttachment метаданные прикреплённого файла (опционально)
 * @property timestamp время создания сообщения в миллисекундах с эпохи Unix
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val fileAttachment: FileAttachmentMetadata? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Легковесные метаданные файла, используемые в истории чата.
 *
 * Не содержат полный контент файла — только информацию для отображения и ссылку на полные данные.
 *
 * @property fileId идентификатор файла в репозитории
 * @property fileName имя файла (без пути)
 * @property fileExtension расширение файла (например, "txt", "pdf")
 * @property fileSize размер файла в байтах
 * @property mimeType MIME-тип файла (например, "text/plain")
 * @property preview краткое текстовое превью (обычно первые 500 символов)
 * @property uploadTimestamp время загрузки файла в миллисекундах
 */
data class FileAttachmentMetadata(
    val fileId: String,
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val mimeType: String,
    val preview: String,
    val uploadTimestamp: Long = System.currentTimeMillis()
)

/**
 * Полное представление файла, хранящееся в репозитории.
 *
 * Используется для редактирования, отправки в API и локального хранения.
 *
 * @property id уникальный идентификатор файла
 * @property fileName имя файла
 * @property fileExtension расширение файла
 * @property fileSize размер в байтах
 * @property mimeType MIME-тип
 * @property originalContent полное содержимое файла в виде строки
 * @property isEditable флаг, указывающий, можно ли редактировать содержимое
 */
data class FileAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val mimeType: String,
    val originalContent: String,
    val isEditable: Boolean = true
) {
    fun toMetadata(): FileAttachmentMetadata {
        val preview = if (originalContent.length > 500) {
            originalContent.take(500) + "..."
        } else {
            originalContent
        }
        return FileAttachmentMetadata(
            fileId = id,
            fileName = fileName,
            fileExtension = fileExtension,
            fileSize = fileSize,
            mimeType = mimeType,
            preview = preview,
            uploadTimestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Роль участника чата.
 *
 * Используется как в доменной модели, так и для сериализации в API.
 */
enum class ChatRole {
    /**
     * Сообщение от пользователя.
     */
    USER,

    /**
     * Ответ от ассистента (LLM).
     */
    ASSISTANT,

    /**
     * Системное сообщение (инструкции для модели).
     */
    SYSTEM;

    /**
     * Конвертирует роль в строку, совместимую с API LLM (в нижнем регистре).
     *
     * @return строковое представление роли: "user", "assistant" или "system"
     */
    fun toApiRole(): String = when (this) {
        USER -> "user"
        ASSISTANT -> "assistant"
        SYSTEM -> "system"
    }

    companion object {
        /**
         * Преобразует строку из API в соответствующее значение [ChatRole].
         *
         * Регистронезависимо.
         *
         * @param role строка из API (например, "User", "ASSISTANT")
         * @return соответствующий [ChatRole]
         * @throws IllegalArgumentException если роль неизвестна
         */
        fun fromApiRole(role: String): ChatRole = when (role.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            "system" -> SYSTEM
            else -> throw IllegalArgumentException("Unknown role: $role")
        }
    }
}

/**
 * Ответ от LLM API на запрос завершения чата.
 *
 * @property id уникальный идентификатор запроса
 * @property choices список возможных ответов (обычно один)
 * @property usage информация об использовании токенов (может отсутствовать)
 */
data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

/**
 * Один из возможных вариантов ответа от модели.
 *
 * @property message сформированное сообщение от ассистента
 * @property finishReason причина завершения генерации (например, "stop", "length")
 */
data class Choice(
    val message: ChatMessage,
    val finishReason: String? = null
)

/**
 * Статистика использования токенов в запросе.
 *
 * @property promptTokens количество токенов во входном промпте
 * @property completionTokens количество сгенерированных токенов
 * @property totalTokens общее количество токенов
 */
data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Модель LLM с метаданными для выбора пользователем.
 *
 * @property id уникальный идентификатор модели (например, "gpt-4o")
 * @property name отображаемое имя модели
 * @property description краткое описание возможностей
 * @property created временная метка создания модели (в секундах Unix)
 * @property contextLength максимальная длина контекста (в токенах)
 * @property pricingPrompt стоимость за 1M токенов входного контекста
 * @property pricingCompletion стоимость за 1M сгенерированных токенов
 * @property pricingImage стоимость за изображение (если поддерживается)
 * @property inputModalities поддерживаемые входные модальности (например, ["text", "image"])
 * @property outputModalities поддерживаемые выходные модальности (например, ["text"])
 * @property isSelected флаг, выбрана ли модель по умолчанию
 * @property isFavorite добавлена ли модель в избранное
 */
data class LlmModel(
    val id: String,
    val name: String,
    val description: String,
    val created: Long,
    val contextLength: BigDecimal,
    val pricingPrompt: BigDecimal,
    val pricingCompletion: BigDecimal,
    val pricingImage: BigDecimal?,
    val inputModalities: List<String>,
    val outputModalities: List<String>,
    val isSelected: Boolean,
    val isFavorite: Boolean = false
)

/**
 * Полезная нагрузка запроса к API с поддержкой файлов.
 *
 * @property messages список сообщений в формате API
 * @property attachedFiles полные данные прикреплённых файлов (для локальной обработки или отправки)
 */
data class ApiRequestPayload(
    val messages: List<ApiMessage>,
    val attachedFiles: List<FileAttachment> = emptyList()
)

/**
 * Сообщение в формате, совместимом с LLM API.
 *
 * @property role роль отправителя ("user", "assistant", "system")
 * @property content текст сообщения
 */
data class ApiMessage(
    val role: String,
    val content: String
)

/**
 * Запрос к API с явными ссылками на файлы.
 *
 * Используется при отправке файлов как отдельных объектов.
 *
 * @property model идентификатор модели
 * @property messages история сообщений
 * @property files список ссылок на файлы с их содержимым
 */
data class ApiRequestWithFiles(
    val model: String,
    val messages: List<ApiMessage>,
    val files: List<FileReference>
)

/**
 * Ссылка на файл для отправки в API.
 *
 * Содержит полные данные файла (в отличие от [FileAttachmentMetadata]).
 *
 * @property id уникальный идентификатор
 * @property name имя файла
 * @property content содержимое файла в виде строки
 * @property mimeType MIME-тип файла
 */
data class FileReference(
    val id: String,
    val name: String,
    val content: String,
    val mimeType: String
)
