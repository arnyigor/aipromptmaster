package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.aipromptmaster.data.db.entities.PromptHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface PromptHistoryDao {
    @Query("""
        SELECT * FROM prompt_history 
        WHERE prompt_id = :promptId 
        ORDER BY modified_at DESC
    """)
    fun getPromptHistory(promptId: String): Flow<List<PromptHistoryEntity>>
    
    @Query("""
        SELECT * FROM prompt_history 
        WHERE modified_by = :userId 
        ORDER BY modified_at DESC
    """)
    fun getUserHistory(userId: String): Flow<List<PromptHistoryEntity>>
    
    @Query("""
        SELECT * FROM prompt_history 
        WHERE modified_at BETWEEN :startDate AND :endDate 
        ORDER BY modified_at DESC
    """)
    fun getHistoryByDateRange(startDate: Date, endDate: Date): Flow<List<PromptHistoryEntity>>
    
    @Query("""
        SELECT DISTINCT prompt_id, title, modified_at 
        FROM prompt_history 
        WHERE change_type = :changeType
        ORDER BY modified_at DESC 
        LIMIT :limit
    """)
    fun getRecentChanges(changeType: String, limit: Int = 50): Flow<List<PromptHistoryEntity>>
    
    @Query("""
        SELECT * FROM prompt_history 
        WHERE prompt_id = :promptId 
        AND modified_at <= :date 
        ORDER BY modified_at DESC 
        LIMIT 1
    """)
    suspend fun getPromptVersionAtDate(promptId: String, date: Date): PromptHistoryEntity?
    
    @Insert
    suspend fun addHistoryEntry(entry: PromptHistoryEntity)
    
    @Insert
    suspend fun addHistoryEntries(entries: List<PromptHistoryEntity>)
    
    @Query("""
        DELETE FROM prompt_history 
        WHERE modified_at < :date
    """)
    suspend fun deleteHistoryBefore(date: Date)
    
    @Query("""
        SELECT COUNT(*) FROM prompt_history 
        WHERE prompt_id = :promptId
    """)
    suspend fun getHistoryCount(promptId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PromptHistoryEntity)

    @Query("""
        SELECT * FROM prompt_history 
        WHERE prompt_id = :promptId 
        ORDER BY modified_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPromptHistory(
        promptId: String,
        limit: Int,
        offset: Int
    ): List<PromptHistoryEntity>

    @Query("""
        SELECT * FROM prompt_history 
        WHERE (:changeType IS NULL OR change_type = :changeType)
        ORDER BY modified_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRecentChanges(
        changeType: String?,
        limit: Int,
        offset: Int
    ): List<PromptHistoryEntity>

    @Query("DELETE FROM prompt_history WHERE modified_at < :date")
    suspend fun deleteOlderThan(date: Date)
} 