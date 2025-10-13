package com.arny.aipromptmaster.domain.models

data class Chat(
    val conversationId: String,
    val name: String,
    val timestamp: Long,
    val lastMessage: String?
)