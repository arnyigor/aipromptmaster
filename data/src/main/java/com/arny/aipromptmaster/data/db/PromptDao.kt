package com.arny.aipromptmaster.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Delete
    suspend fun delete(prompt: PromptEntity)

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getPromptById(id: Int): PromptEntity?

    @Query("SELECT * FROM prompts ORDER BY created_at DESC")
    fun getAllPrompts(): Flow<List<PromptEntity>>

    // Пример запроса с JOIN
    @Transaction
    @Query("SELECT * FROM prompts")
    fun getPromptsWithTags(): Flow<List<PromptWithTags>>
}