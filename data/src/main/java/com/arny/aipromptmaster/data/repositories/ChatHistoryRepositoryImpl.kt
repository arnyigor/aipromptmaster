package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatHistoryRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : IChatHistoryRepository {

    override suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessageById(messageId)
    }

    // В реализации репозитория истории
    override suspend fun addMessage(conversationId: String, message: ChatMessage): String {
        val entity = message.toEntity(conversationId) // Преобразуем в MessageEntity
        chatDao.insertMessage(entity) // Новый метод в DAO
        return entity.id
    }

    override suspend fun updateMessageContent(messageId: String, newContent: String) {
        chatDao.updateMessageContent(messageId, newContent)
    }

    override suspend fun appendContentToMessage(messageId: String, contentChunk: String) {
        chatDao.appendContentToMessage(messageId, contentChunk) // Новый метод в DAO
    }

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

    override suspend fun updateSystemPrompt(conversationId: String, prompt: String) {
        chatDao.updateSystemPrompt(conversationId, prompt)
    }

    override suspend fun deleteConversation(conversationId: String) {
        chatDao.deleteConversationById(conversationId)
    }

    override suspend fun getSystemPrompt(conversationId: String): String? {
        return chatDao.getSystemPrompt(conversationId)
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return chatDao.getConversation(conversationId)?.toDomain()
    }

    override suspend fun getFullHistory(conversationId: String): List<ChatMessage> {
        return chatDao.getAllMessagesForConversation(conversationId).map { it.toDomain() }
    }

    // Вспомогательные функции-мапперы (можно вынести в отдельный файл ChatMapper.kt)
    private fun MessageEntity.toDomainModel(): ChatMessage = ChatMessage(
        id = this.id,
        role = ChatRole.fromApiRole(this.role),
        content = this.content
    )

    private fun ChatMessage.toEntity(conversationId: String): MessageEntity = MessageEntity(
        id = this.id,
        conversationId = conversationId,
        role = this.role.toString(),
        content = this.content
    )

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = this.id,
        title = this.title,
        systemPrompt = this.systemPrompt
    )

    // У вас уже должен быть похожий маппер, убедитесь, что он есть
    private fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = this.id,
        role = ChatRole.fromApiRole(this.role),
        content = this.content
    )
}
