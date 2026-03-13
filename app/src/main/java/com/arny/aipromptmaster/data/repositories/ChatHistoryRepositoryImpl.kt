package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.Conversation
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ChatHistoryRepositoryImpl(
    private val chatDao: ChatDao
) : IChatHistoryRepository {

    private val _streamingBuffer = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun observeStreamingBuffer(): StateFlow<Map<String, String>> =
        _streamingBuffer.asStateFlow()

    override fun updateStreamingBuffer(map: Map<String, String>) {
        _streamingBuffer.value = map
    }

    override suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessageAndRefreshConversation(messageId)
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

    override suspend fun updateMessageModelId(messageId: String, modelId: String) {
        chatDao.updateMessageModelId(messageId, modelId)
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
        chatDao.updateSystemPromptAndAddMessage(conversationId, prompt)
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

    override suspend fun getFullHistory(conversationId: String): List<ChatMessage> =
        chatDao.getAllMessagesForConversation(conversationId).map { it.toDomainModel() }

    private fun MessageEntity.toDomainModel(): ChatMessage = ChatMessage(
        id = this.id,
        role = ChatRole.fromApiRole(this.role),
        content = this.content,
        modelId = this.modelId,
        fileAttachment = parseAttachmentsJson(this.attachmentsJson),
        timestamp = this.timestamp
    )

    private fun ChatMessage.toEntity(conversationId: String): MessageEntity = MessageEntity(
        id = this.id,
        conversationId = conversationId,
        role = this.role.toApiRole(),
        content = this.content,
        modelId = this.modelId,
        attachmentsJson = this.fileAttachment?.let { attachment ->
            try {
                AttachmentListSerializer.serialize(listOf(attachment))
            } catch (e: Exception) {
                ""
            }
        } ?: "",
        timestamp = this.timestamp
    )

    /**
     * Парсит JSON массив вложений из БД в FileAttachmentMetadata
     */
    private fun parseAttachmentsJson(jsonStr: String): FileAttachmentMetadata? {
        return if (jsonStr.isBlank()) null
        else try {
            val attachments = AttachmentListSerializer.deserialize(jsonStr)
            attachments.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Сериализатор списка FileAttachmentMetadata для сохранения в БД
     */
    private object AttachmentListSerializer {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class Wrapper(val attachments: List<FileAttachmentMetadata>)

        fun serialize(attachments: List<FileAttachmentMetadata>): String {
            return json.encodeToString(Wrapper.serializer(), Wrapper(attachments))
        }

        fun deserialize(jsonStr: String): List<FileAttachmentMetadata> {
            return try {
                json.decodeFromString(Wrapper.serializer(), jsonStr).attachments
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = this.id,
        title = this.title,
        systemPrompt = this.systemPrompt
    )

    override fun cleanStreamingBuffer() {
        _streamingBuffer.value = emptyMap()
    }
}
