package com.arny.aiprompts.data.model

data class Chat(
    val id: String,
    val name: String,
    val timestamp: Long,
    val lastMessage: String
)