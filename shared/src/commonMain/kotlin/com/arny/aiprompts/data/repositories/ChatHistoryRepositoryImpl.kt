package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatHistoryRepositoryImpl : IChatHistoryRepository {

    private val mutex = Mutex()
    private val _history = MutableStateFlow<List<ChatMessage>>(emptyList())

    override fun getHistoryFlow(): Flow<List<ChatMessage>> = _history.asStateFlow()

    override suspend fun addMessage(message: ChatMessage) = mutex.withLock {
        _history.update { currentHistory ->
            currentHistory + message
        }
    }

    override suspend fun updateMessage(message: ChatMessage) = mutex.withLock {
        _history.update { currentHistory ->
            currentHistory.map { if (it.id == message.id) message else it }
        }
    }

    override suspend fun deleteMessage(messageId: String) = mutex.withLock {
        _history.update { currentHistory ->
            currentHistory.filterNot { it.id == messageId }
        }
    }

    override suspend fun getMessage(messageId: String): ChatMessage? {
        return _history.value.find { it.id == messageId }
    }

    override suspend fun addMessages(messages: List<ChatMessage>) {
        messages.forEach { addMessage(it) }
    }

    override suspend fun clearHistory() = mutex.withLock {
        _history.value = emptyList()
    }
}