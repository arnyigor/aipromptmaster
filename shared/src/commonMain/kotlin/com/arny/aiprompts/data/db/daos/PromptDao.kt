package com.arny.aiprompts.data.db.daos

import androidx.room.*
import com.arny.aiprompts.data.db.entities.PromptEntity
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
    suspend fun getAllPrompts(): List<PromptEntity>

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun getPromptsCount(): Int

    @Query("SELECT _id FROM prompts")
    suspend fun getAllPromptIds(): List<String>

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

    /**
     * Получает отфильтрованный и отсортированный список промптов.
     * Этот метод адаптирован для работы с тегами, хранящимися в виде строки.
     *
     * ВНИМАНИЕ: Этот запрос может быть медленным при большом количестве тегов в фильтре.
     * Он будет заменен на более простой, если список тегов пуст.
     */
    @Query(
        """
        SELECT * FROM prompts
        WHERE
            /* 1. Фильтр по поисковому запросу */
            (:searchQuery = '' OR 
                title LIKE '%' || :searchQuery || '%' OR
                description LIKE '%' || :searchQuery || '%' OR
                content_ru LIKE '%' || :searchQuery || '%' OR
                content_en LIKE '%' || :searchQuery || '%' OR
                tags LIKE '%' || :searchQuery || '%'
            )
            /* 2. Фильтр по категории */
            AND (:category IS NULL OR category = :category)
            /* 3. Фильтр по статусу (включая избранное) */
            AND (
                CASE 
                    WHEN :status = 'favorite' THEN is_favorite = 1
                    WHEN :status IS NULL THEN 1
                    ELSE status = :status
                END
            )
            /* 4. Фильтр по тегам - динамически строится в репозитории */
            AND (:tagCondition)
        ORDER BY is_favorite DESC, modified_at DESC
        LIMIT :limit OFFSET :offset
    """
    )
    suspend fun getPromptsWithTagCondition(
        searchQuery: String,
        category: String?,
        status: String?,
        tagCondition: String, // Сюда будем передавать готовую строку с условиями
        limit: Int,
        offset: Int
    ): List<PromptEntity>

    // Вспомогательный метод для случая, когда тегов нет
    @Query(
        """
        SELECT * FROM prompts
        WHERE
            (:searchQuery = '' OR 
                title LIKE '%' || :searchQuery || '%' OR
                description LIKE '%' || :searchQuery || '%' OR
                content_ru LIKE '%' || :searchQuery || '%' OR
                content_en LIKE '%' || :searchQuery || '%' OR
                tags LIKE '%' || :searchQuery || '%'
            )
            AND (:category IS NULL OR category = :category)
            AND (
                CASE 
                    WHEN :status = 'favorite' THEN is_favorite = 1
                    WHEN :status IS NULL THEN 1
                    ELSE status = :status
                END
            )
        ORDER BY is_favorite DESC, modified_at DESC
        LIMIT :limit OFFSET :offset
    """
    )
    suspend fun getPromptsWithoutTags(
        searchQuery: String,
        category: String?,
        status: String?,
        limit: Int,
        offset: Int
    ): List<PromptEntity>


    @Query("UPDATE prompts SET is_favorite = NOT is_favorite WHERE _id = :promptId")
    suspend fun toggleFavoriteStatus(promptId: String)

    @Query("""
        SELECT DISTINCT TRIM(tag) as tag
        FROM (
            SELECT TRIM(value) as tag
            FROM prompts,
                 json_each('["' || REPLACE(tags, ',', '","') || '"]')
            WHERE tags IS NOT NULL AND tags != ''
        )
        WHERE tag != ''
        ORDER BY tag
    """)
    suspend fun getAllUniqueTags(): List<String>
}
