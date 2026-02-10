package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.db.daos.ChatMessageDao
import com.arny.aiprompts.data.db.daos.ChatSessionDao
import com.arny.aiprompts.data.db.entities.ChatMessageEntity
import com.arny.aiprompts.data.db.entities.ChatSessionEntity
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Реализация репозитория сессий чата.
 * Использует Room DAO для работы с базой данных.
 *
 * @param sessionDao DAO для работы с сессиями
 * @param messageDao DAO для работы с сообщениями
 * @param json Сериализатор для MessageStatus
 */
class ChatSessionRepositoryImpl(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : IChatSessionRepository {

    // ==================== Сессии ====================

    override fun getAllSessions(): Flow<List<ChatSession>> {
        return sessionDao.getAllActiveSessions()
            .map { entities ->
                entities.map { it.toDomain(emptyList()) }
            }
    }

    override fun getSessionById(sessionId: String): Flow<ChatSession?> {
        val sessionFlow = sessionDao.getAllActiveSessions()
            .map { sessions -> sessions.find { it.id == sessionId } }
        
        val messagesFlow = messageDao.getMessagesForSession(sessionId)
        
        return combine(sessionFlow, messagesFlow) { sessionEntity, messageEntities ->
            sessionEntity?.toDomain(messageEntities.map { it.toDomain() })
        }
    }

    override suspend fun createSession(name: String, systemPrompt: String?): ChatSession {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            name = name,
            systemPrompt = systemPrompt,
            settings = ChatSettings(), // Настройки по умолчанию
            createdAt = now,
            updatedAt = now,
            isArchived = false,
            modelId = null,
            messages = emptyList()
        )
        sessionDao.insertSession(session.toEntity())
        return session
    }

    override suspend fun updateSession(session: ChatSession) {
        sessionDao.updateSession(session.toEntity())
    }

    override suspend fun renameSession(sessionId: String, newName: String) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteSession(sessionId: String) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.deleteSession(session)
    }

    override suspend fun archiveSession(sessionId: String) {
        sessionDao.archiveSession(sessionId)
    }

    override suspend fun unarchiveSession(sessionId: String) {
        sessionDao.unarchiveSession(sessionId)
    }

    // ==================== Сообщения ====================

    override fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForSession(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun addMessage(sessionId: String, message: ChatMessage) {
        val maxOrder = messageDao.getMaxOrderIndex(sessionId) ?: -1
        val entity = message.toEntity(sessionId).copy(orderIndex = maxOrder + 1)
        messageDao.insertMessage(entity)
        
        // Обновляем время сессии
        sessionDao.updateTimestamp(sessionId)
    }

    override suspend fun updateMessage(message: ChatMessage) {
        // Получаем существующее сообщение для сохранения sessionId
        val existing = messageDao.getMessageById(message.id) ?: return
        messageDao.updateMessage(message.toEntity(existing.sessionId))
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    override suspend fun clearSessionMessages(sessionId: String) {
        messageDao.deleteMessagesForSession(sessionId)
        sessionDao.updateTimestamp(sessionId)
    }

    override suspend fun getRecentMessagesForContext(sessionId: String, limit: Int): List<ChatMessage> {
        return messageDao.getLastMessages(sessionId, limit)
            .map { it.toDomain() }
            .reversed() // Возвращаем в хронологическом порядке
    }

    override suspend fun updateSystemPrompt(sessionId: String, systemPrompt: String?) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(
            session.copy(
                systemPrompt = systemPrompt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun searchSessions(query: String): Flow<List<ChatSession>> {
        return sessionDao.searchSessions(query)
            .map { entities -> entities.map { it.toDomain(emptyList()) } }
    }

    // ==================== Mappers ====================

    private fun ChatSessionEntity.toDomain(messages: List<ChatMessage>): ChatSession {
        return ChatSession(
            id = id,
            name = name,
            systemPrompt = systemPrompt,
            settings = ChatSettings(
                temperature = temperature,
                maxTokens = maxTokens,
                topP = topP,
                contextWindow = contextWindow
            ),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isArchived = isArchived,
            modelId = modelId,
            messages = messages
        )
    }

    private fun ChatSession.toEntity(): ChatSessionEntity {
        return ChatSessionEntity(
            id = id,
            name = name,
            systemPrompt = systemPrompt,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens,
            topP = settings.topP,
            contextWindow = settings.contextWindow,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isArchived = isArchived,
            modelId = modelId
        )
    }

    private fun ChatMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            role = when (role) {
                "user" -> ChatMessageRole.USER
                "assistant" -> ChatMessageRole.MODEL
                "system" -> ChatMessageRole.SYSTEM
                else -> ChatMessageRole.USER
            },
            content = content,
            timestamp = timestamp,
            status = json.decodeFromString<MessageStatus>(status),
            editedAt = editedAt,
            modelId = modelId,
            tokenCount = tokenCount
        )
    }

    private fun ChatMessage.toEntity(sessionId: String): ChatMessageEntity {
        return ChatMessageEntity(
            id = id,
            sessionId = sessionId,
            role = when (role) {
                ChatMessageRole.USER -> "user"
                ChatMessageRole.MODEL -> "assistant"
                ChatMessageRole.SYSTEM -> "system"
            },
            content = content,
            timestamp = timestamp,
            status = json.encodeToString(status),
            modelId = modelId,
            tokenCount = tokenCount,
            editedAt = editedAt,
            orderIndex = 0 // Устанавливается при вставке
        )
    }
}
