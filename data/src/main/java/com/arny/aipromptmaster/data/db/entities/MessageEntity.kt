package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "messages",
    // 1. Объявляем внешний ключ
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE // При удалении диалога удалять и сообщения
        )
    ],
    // 2. Объявляем индекс, который мы создаем в миграции
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // например, "user" или "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
