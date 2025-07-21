package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE // При удалении диалога удалятся и все его сообщения
    )]
)
data class MessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String, // Внешний ключ для связи с диалогом
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
