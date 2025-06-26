package com.arny.aipromptmaster.presentation.ui.chathistory

data class Chat(
    val id: String,
    val name: String,
    val timestamp: Long,
    val lastMessage: String
)
