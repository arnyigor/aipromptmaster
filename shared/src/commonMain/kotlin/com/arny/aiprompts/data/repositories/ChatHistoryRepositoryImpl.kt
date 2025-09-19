package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ChatHistoryRepositoryImpl constructor() : IChatHistoryRepository {

    private val _history = MutableStateFlow<List<ChatMessage>>(emptyList())

    override fun getHistoryFlow(): Flow<List<ChatMessage>> = _history.asStateFlow()

    override suspend fun addMessages(messages: List<ChatMessage>) {
        _history.update { currentHistory ->
            currentHistory + messages
        }
    }

    override suspend fun clearHistory() {
        _history.value = emptyList()
    }
}