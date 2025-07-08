package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Query(
        """
        SELECT * FROM prompts 
        ORDER BY 
            is_local DESC, -- Сначала локальные
            modified_at DESC
    """
    )
    fun getAllPromptsFlow(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts")
    fun getAllPrompts(): List<PromptEntity>

    @Query("SELECT _id FROM prompts")
    fun getAllPromptIds(): List<String>

    @Query("DELETE FROM prompts WHERE _id IN (:ids)")
    suspend fun deletePromptsByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptEntity): Long

    @Update
    suspend fun updatePrompt(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE _id = :promptId")
    suspend fun delete(promptId: String)

    @Query("SELECT * FROM prompts WHERE _id = :promptId")
    suspend fun getById(promptId: String): PromptEntity?

    @Query(
        """
    SELECT *, 
    CASE 
        WHEN LOWER(title) LIKE LOWER('%' || :search || '%') THEN 3
        WHEN LOWER(content_ru) LIKE LOWER('%' || :search || '%') THEN 2
        WHEN LOWER(content_en) LIKE LOWER('%' || :search || '%') THEN 2
        WHEN LOWER(tags) LIKE LOWER('%' || :search || '%') THEN 1
        ELSE 0
    END as relevance
    FROM prompts
    WHERE 
    /* Поиск по всем полям */
    (:search = '' OR 
        LOWER(title) LIKE LOWER('%' || :search || '%') OR 
        LOWER(content_ru) LIKE LOWER('%' || :search || '%') OR 
        LOWER(content_en) LIKE LOWER('%' || :search || '%') OR
        LOWER(tags) LIKE LOWER('%' || :search || '%')
    )
    /* Фильтр по категории */
    AND (:category IS NULL OR LOWER(category) = LOWER(:category))
    /* Фильтр по статусу и избранному */
    AND (
        CASE 
            WHEN :status = 'favorite' THEN is_favorite = 1
            WHEN :status IS NULL THEN 1
            ELSE status = :status
        END
    )
    /* Фильтр по тегам */
    AND (:tags = '' OR LOWER(tags) LIKE LOWER('%' || :tags || '%'))
    /* Сортировка по релевантности и дате модификации */
    ORDER BY 
        is_favorite DESC,
        relevance DESC, 
        modified_at DESC
    LIMIT :limit OFFSET :offset
    """
    )
    suspend fun getPrompts(
        search: String = "",
        category: String? = null,
        status: String? = null,
        tags: String = "",
        limit: Int,
        offset: Int
    ): List<PromptEntity>

    @Query("SELECT DISTINCT category FROM prompts WHERE category IS NOT NULL AND category != ''")
    suspend fun getCategories(): List<String>

    @Query("SELECT tags FROM prompts")
    suspend fun getTags(): List<String>
}
