package com.arny.aipromptmaster.domain.models

data class Chat(
    val id: String,
    val name: String,
    val timestamp: Long,
    val lastMessage: String
)