package com.arny.aipromptmaster.domain.models

data class Conversation(
    val id: String,
    val title: String,
    val systemPrompt: String?
)