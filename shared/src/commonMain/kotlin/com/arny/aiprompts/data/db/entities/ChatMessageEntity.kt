package com.arny.aiprompts.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity для хранения сообщений чата в базе данных Room.
 * 
 * @property id Уникальный идентификатор сообщения
 * @property sessionId ID сессии чата (внешний ключ)
 * @property role Роль отправителя: "user", "assistant", "system"
 * @property content Текст сообщения
 * @property timestamp Время создания сообщения
 * @property status Статус сообщения (сериализованный JSON)
 * @property modelId ID модели, которая сгенерировала ответ
 * @property tokenCount Количество токенов (если доступно)
 * @property editedAt Время редактирования (null если не редактировалось)
 * @property orderIndex Порядковый номер для сортировки в сессии
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE // При удалении сессии удаляются все сообщения
        )
    ],
    indices = [
        Index(value = ["session_id"]), // Для быстрого поиска сообщений сессии
        Index(value = ["timestamp"]), // Для сортировки по времени
        Index(value = ["order_index"]) // Для сортировки по порядку
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "role")
    val role: String, // "user", "assistant", "system"

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "status")
    val status: String, // Сериализованный MessageStatus (JSON)

    @ColumnInfo(name = "model_id")
    val modelId: String? = null,

    @ColumnInfo(name = "token_count")
    val tokenCount: Int? = null,

    @ColumnInfo(name = "edited_at")
    val editedAt: Long? = null,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0
)
