package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.aipromptmaster.data.db.entities.ConversationFileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с файлами чата
 */
@Dao
interface ConversationFileDao {

    /**
     * Добавить файл к чату
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ConversationFileEntity): Long

    /**
     * Получить все файлы чата
     */
    @Query("SELECT * FROM conversation_files WHERE conversationId = :conversationId ORDER BY uploadedAt ASC")
    suspend fun getFilesByConversationId(conversationId: String): List<ConversationFileEntity>

    /**
     * Получить файлы чата как Flow
     */
    @Query("SELECT * FROM conversation_files WHERE conversationId = :conversationId ORDER BY uploadedAt ASC")
    fun getFilesByConversationIdFlow(conversationId: String): Flow<List<ConversationFileEntity>>

    /**
     * Получить файл по ID
     */
    @Query("SELECT * FROM conversation_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getFileById(fileId: String): ConversationFileEntity?

    /**
     * Удалить файл из чата
     */
    @Query("DELETE FROM conversation_files WHERE fileId = :fileId")
    suspend fun deleteFileById(fileId: String)

    /**
     * Удалить все файлы чата
     */
    @Query("DELETE FROM conversation_files WHERE conversationId = :conversationId")
    suspend fun deleteFilesByConversationId(conversationId: String)

    /**
     * Получить количество файлов в чате
     */
    @Query("SELECT COUNT(*) FROM conversation_files WHERE conversationId = :conversationId")
    suspend fun getFilesCount(conversationId: String): Int
}