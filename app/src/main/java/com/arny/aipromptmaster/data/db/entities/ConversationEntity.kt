package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arny.aipromptmaster.domain.models.Conversation
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val systemPrompt: String? = null
)
