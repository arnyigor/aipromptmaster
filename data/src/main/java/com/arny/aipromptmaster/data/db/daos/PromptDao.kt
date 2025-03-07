package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.arny.aipromptmaster.data.db.PromptWithTags
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.domain.models.PromptCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY modified_at DESC")
    fun getAllPrompts(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE language = :language ORDER BY modified_at DESC")
    fun getPromptsByLanguage(language: String): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE category = :category ORDER BY modified_at DESC")
    fun getPromptsByCategory(category: PromptCategory): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE ai_model = :aiModel ORDER BY modified_at DESC")
    fun getPromptsByAiModel(aiModel: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE title LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%'
        OR template LIKE '%' || :query || '%'
        ORDER BY modified_at DESC
    """
    )
    fun searchPrompts(query: String): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE parent_id = :parentId ORDER BY version DESC")
    fun getPromptVersions(parentId: String): Flow<List<PromptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptEntity)

    @Update
    suspend fun updatePrompt(prompt: PromptEntity)

    @Delete
    suspend fun deletePrompt(prompt: PromptEntity)

    @Query("UPDATE prompts SET rating = :rating WHERE id = :promptId")
    suspend fun updateRating(promptId: String, rating: Float)

    @Transaction
    @Query("SELECT * FROM prompts ORDER BY modified_at DESC")
    fun getAllPromptsWithTags(): Flow<List<PromptWithTags>>

    @Query("SELECT * FROM prompts WHERE is_favorite = 1")
    fun getFavoritePrompts(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE ai_model = :model")
    fun getPromptsByModel(model: String): Flow<List<PromptEntity>>

    @Query("UPDATE prompts SET is_favorite = :isFavorite WHERE id = :promptId")
    suspend fun updateFavoriteStatus(promptId: String, isFavorite: Boolean)
}