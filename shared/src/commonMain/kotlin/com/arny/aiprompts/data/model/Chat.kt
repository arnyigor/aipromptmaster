package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: String,
    val name: String,
    val timestamp: Long,
    val lastMessage: String
)

// Модель сессии чата
@Serializable
data class ChatSession(
    val id: String,
    val name: String,
    val timestamp: Long,
    val lastMessage: String,
    val messages: List<ChatMessage> = emptyList()
)