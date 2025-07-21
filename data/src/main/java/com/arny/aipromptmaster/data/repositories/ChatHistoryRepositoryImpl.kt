package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

class ChatHistoryRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : IChatHistoryRepository {

    override fun getHistoryFlow(conversationId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(conversationId).map { entities ->
            // Преобразуем List<MessageEntity> в List<ChatMessage>
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun addMessages(conversationId: String, messages: List<ChatMessage>) {
        val messageEntities = messages.map { it.toEntity(conversationId) }
        chatDao.insertMessages(messageEntities)
        chatDao.updateConversationTimestamp(conversationId, System.currentTimeMillis())
    }

    override fun getChatList(): Flow<List<Chat>> {
        return chatDao.getChatList()
    }

    override suspend fun clearHistory(conversationId: String) {
        chatDao.clearMessagesForConversation(conversationId)
    }

    override suspend fun createNewConversation(title: String): String {
        val newConversation = ConversationEntity(title = title)
        chatDao.insertConversation(newConversation)
        return newConversation.id
    }

    // Вспомогательные функции-мапперы (можно вынести в отдельный файл ChatMapper.kt)
    private fun MessageEntity.toDomainModel(): ChatMessage = ChatMessage(
        id = this.id,
        role = ChatRole.fromString(this.role),
        content = this.content
    )

    private fun ChatMessage.toEntity(conversationId: String): MessageEntity = MessageEntity(
        id = this.id,
        conversationId = conversationId,
        role = this.role.toString(),
        content = this.content
    )
}
