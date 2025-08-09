package com.arny.aiprompts.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(val model: String, val messages: List<ChatMessage>)