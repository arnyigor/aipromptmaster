package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date

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
    fun getAllPrompts(): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE is_local = :isLocal
        ORDER BY modified_at DESC
    """
    )
    fun getPromptsByType(isLocal: Boolean): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        ORDER BY modified_at DESC 
        LIMIT :pageSize OFFSET :offset
    """
    )
    fun getPromptsPaged(pageSize: Int, offset: Int): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE content_ru != '' OR content_en != '' 
        ORDER BY modified_at DESC
    """
    )
    fun getPromptsWithContent(): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE category = :category 
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsByCategory(category: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE (',' || compatible_models || ',') LIKE '%,' || :model || ',%'
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsByModel(model: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE (LOWER(title) LIKE '%' || LOWER(:query) || '%' 
           OR LOWER(description) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(content_ru) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(content_en) LIKE '%' || LOWER(:query) || '%')
           AND (:isLocal IS NULL OR is_local = :isLocal)
        ORDER BY 
            is_local DESC,
            CASE 
                WHEN LOWER(title) LIKE '%' || LOWER(:query) || '%' THEN 1
                WHEN LOWER(description) LIKE '%' || LOWER(:query) || '%' THEN 2
                WHEN LOWER(content_ru) LIKE '%' || LOWER(:query) || '%' THEN 3
                WHEN LOWER(content_en) LIKE '%' || LOWER(:query) || '%' THEN 4
                ELSE 6
            END,
            modified_at DESC
    """
    )
    fun searchPrompts(query: String, isLocal: Boolean? = null): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE (',' || LOWER(tags) || ',') LIKE '%,' || LOWER(:tag) || ',%'
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsByTag(tag: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE (:tagsString IS NULL OR 
            LOWER(tags) LIKE '%' || LOWER(:tagsString) || '%')
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsByTags(tagsString: String?): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE author LIKE '%' || :author || '%'
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsByAuthor(author: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE source LIKE '%' || :source || '%'
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsBySource(source: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE status = :status
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getPromptsByStatus(status: String): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE is_favorite = 1
        ORDER BY 
            is_local DESC,
            modified_at DESC
    """
    )
    fun getFavoritePrompts(): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE rating >= :minRating
        ORDER BY 
            is_local DESC,
            rating DESC, 
            modified_at DESC
    """
    )
    fun getPromptsByMinRating(minRating: Float): Flow<List<PromptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptEntity): Long

    @Update
    suspend fun updatePrompt(prompt: PromptEntity)

    @Delete
    suspend fun deletePrompt(prompt: PromptEntity)

    @Query(
        """
        UPDATE prompts 
        SET rating = :rating, 
            rating_votes = rating_votes + 1,
            modified_at = :modifiedAt
        WHERE _id = :promptId
    """
    )
    suspend fun updateRating(promptId: String, rating: Float, modifiedAt: Date = Date())

    @Query(
        """
        UPDATE prompts 
        SET is_favorite = :isFavorite,
            modified_at = :modifiedAt
        WHERE _id = :promptId
    """
    )
    suspend fun updateFavoriteStatus(promptId: String, isFavorite: Boolean, modifiedAt: Date = Date())

    @Query(
        """
        UPDATE prompts 
        SET tags = 
            CASE 
                WHEN tags = '' THEN :tag
                WHEN (',' || tags || ',') LIKE '%,' || :tag || ',%' THEN tags
                ELSE tags || ',' || :tag
            END,
            modified_at = :modifiedAt
        WHERE _id = :promptId
    """
    )
    suspend fun addTag(promptId: String, tag: String, modifiedAt: Date = Date())

    @Query(
        """
        UPDATE prompts 
        SET tags = TRIM(REPLACE(',' || tags || ',', ',' || :tag || ',', ','), ','),
            modified_at = :modifiedAt
        WHERE _id = :promptId
    """
    )
    suspend fun removeTag(promptId: String, tag: String, modifiedAt: Date = Date())

    @Query(
        """
        UPDATE prompts 
        SET is_local = :isLocal,
            status = :status,
            modified_at = :modifiedAt
        WHERE _id = :promptId
    """
    )
    suspend fun updatePromptType(
        promptId: String,
        isLocal: Boolean,
        status: String,
        modifiedAt: Date = Date()
    )

    @Query(
        """
        UPDATE prompts 
        SET status = :status,
            modified_at = :modifiedAt
        WHERE _id = :promptId
    """
    )
    suspend fun updateStatus(promptId: String, status: String, modifiedAt: Date = Date())

    @Query(
        """
        SELECT COUNT(*) 
        FROM prompts 
        WHERE is_local = 0 
        AND status IN (:statuses)
    """
    )
    fun getUnsyncedPromptsCount(vararg statuses: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: PromptEntity): Long

    @Update
    suspend fun update(prompt: PromptEntity)

    @Query("DELETE FROM prompts WHERE _id = :promptId")
    suspend fun delete(promptId: String)

    @Query("SELECT * FROM prompts WHERE _id = :promptId")
    suspend fun getById(promptId: String): PromptEntity?

    @Query("SELECT * FROM prompts")
    fun getAll(): Flow<List<PromptEntity>>

    @Query(
        """
        SELECT * FROM prompts 
        WHERE (:search = '' OR title LIKE '%' || :search || '%' OR content_ru LIKE '%'|| '%' OR content_en LIKE '%' ||
         :search || '%')
        AND (:category IS NULL OR category = :category)
        AND (:status IS NULL OR status = :status)
        AND (:tags = '' OR tags LIKE '%' || :tags || '%')
        ORDER BY modified_at DESC
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

}
