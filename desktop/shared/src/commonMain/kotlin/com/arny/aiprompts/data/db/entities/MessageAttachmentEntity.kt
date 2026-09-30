package com.arny.aiprompts.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity для хранения вложений сообщений чата в базе данных Room.
 * Поддерживает изображения, текстовые файлы, документы и код.
 *
 * @property id Уникальный идентификатор вложения
 * @property messageId ID сообщения (внешний ключ)
 * @property type Тип вложения: IMAGE, TEXT_FILE, DOCUMENT, CODE
 * @property fileName Оригинальное имя файла
 * @property mimeType MIME тип файла
 * @property filePath Путь к файлу во внутреннем хранилище приложения
 * @property fileSize Размер файла в байтах
 * @property createdAt Время создания вложения
 */
@Entity(
    tableName = "message_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE // При удалении сообщения удаляются все вложения
        )
    ],
    indices = [
        Index(value = ["message_id"]) // Для быстрого поиска вложений сообщения
    ]
)
data class MessageAttachmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "type")
    val type: String, // IMAGE, TEXT_FILE, DOCUMENT, CODE

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "file_path")
    val filePath: String, // Internal storage path

    @ColumnInfo(name = "file_size")
    val fileSize: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
