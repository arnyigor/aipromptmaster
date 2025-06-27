package com.arny.aipromptmaster.presentation.ui.chat

sealed class AiChatMessageType {
    object USER : AiChatMessageType()
    object ASSISTANT : AiChatMessageType()
    object LOADING : AiChatMessageType()
    object ERROR : AiChatMessageType()
}
