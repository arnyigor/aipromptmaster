package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arny.aipromptmaster.data.db.entities.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    /* ----------------------- Чтение ------------------------------------ */

    @Query(
        """
        SELECT * FROM models
        WHERE (:searchQuery = '' OR LOWER(name) LIKE '%' || :searchQuery || '%'
               OR LOWER(id)   LIKE '%' || :searchQuery || '%')
          AND (:onlyFree = 0 OR pricingPrompt = '0' OR pricingPrompt = '0.0' OR pricingPrompt = 'Free')
          AND (:onlyFavorites = 0 OR isFavorite = 1)
          AND (:onlyAvailable = 0 OR isAvailable = 1)
        ORDER BY
            isSelected DESC,
            isFavorite DESC,
            CASE WHEN :sortBy = 'RATING' THEN
                CASE WHEN rating IS NULL THEN 1 ELSE 0 END
            ELSE 0 END DESC,
            CASE WHEN :sortBy = 'RATING' THEN COALESCE(rating, -1) ELSE 0 END DESC,
            CASE WHEN :sortBy = 'NAME' THEN name ELSE '' END ASC,
            CASE WHEN :sortBy = 'CONTEXT' THEN contextLength ELSE 0 END DESC,
            CASE WHEN :sortBy = 'PRICE' THEN CAST(pricingPrompt AS REAL) ELSE 999999 END ASC,
            CASE WHEN :sortBy = 'AVAILABILITY' THEN
                CASE WHEN isAvailable = 1 THEN 0 WHEN isAvailable = 0 THEN 2 ELSE 1 END
            ELSE 0 END ASC,
            CASE WHEN :sortBy = 'CHECKED' THEN
                CASE WHEN isAvailable IS NULL THEN 1 ELSE 0 END
            ELSE 0 END ASC,
            CASE WHEN :sortBy = 'AVAILABLE_FIRST' THEN
                CASE WHEN isAvailable = 1 THEN 0 WHEN isAvailable = 0 THEN 2 ELSE 1 END
            ELSE 0 END ASC
        """
    )
    fun getModels(
        searchQuery: String,
        onlyFree: Boolean,
        onlyFavorites: Boolean,
        onlyAvailable: Boolean,
        sortBy: String
    ): Flow<List<ModelEntity>>

    @Query("SELECT COUNT(*) FROM models")
    suspend fun getModelsCount(): Int

    @Query("SELECT id FROM models WHERE isFavorite = 1")
    suspend fun getFavoriteIds(): List<String>

    @Query("SELECT id FROM models WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedModelId(): String?

    @Query("SELECT * FROM models WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedModel(): ModelEntity?

    // Flow‑based getter for selected model, emitted on changes.
    @Query("SELECT * FROM models WHERE isSelected = 1 LIMIT 1")
    fun getSelectedModelFlow(): Flow<ModelEntity?>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<ModelEntity>)

    // --- Пакетные операции по флагам
    @Query("UPDATE models SET isFavorite = :isFavorite WHERE id = :modelId")
    suspend fun updateFavoriteStatus(modelId: String, isFavorite: Boolean): Int

    @Query("UPDATE models SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE models SET isSelected = 1 WHERE id = :modelId")
    suspend fun setSelection(modelId: String)

    /* ----------------- Транзакции ----------------------------------- */

    @Transaction
    suspend fun selectModel(modelId: String) {
        clearSelection()
        setSelection(modelId)
    }

    @Transaction
    suspend fun syncModels(newModels: List<ModelEntity>) {
        val favorites = getFavoriteIds().toSet()
        val selectedId = getSelectedModelId()

        val mergedModels = newModels.map { model ->
            model.copy(
                isFavorite = favorites.contains(model.id),
                isSelected = model.id == selectedId
            )
        }
        insertModels(mergedModels)
    }

    /* --------------------- НЕПРЕДВАРИТЕЛЬНО ДЕЙСТВУЮЩИЙ -------------------------------- */

    /**
     * Возвращает конкретную модель по её `id`.
     *
     * @return ModelEntity? – если запись с таким id отсутствует, возвращаем null.
     */
    @Query("SELECT * FROM models WHERE id = :modelId LIMIT 1")
    suspend fun getModelById(modelId: String): ModelEntity?

    /**
     * Возвращает все free модели.
     */
    @Query("SELECT * FROM models WHERE pricingPrompt = '0' OR pricingPrompt = '0.0' OR pricingPrompt = 'Free'")
    suspend fun getFreeModels(): List<ModelEntity>

    /**
     * Обновляет статус доступности модели.
     */
    @Query("UPDATE models SET isAvailable = :isAvailable WHERE id = :modelId")
    suspend fun updateModelAvailability(modelId: String, isAvailable: Boolean): Int

    /**
     * Обновляет статус доступности, время отклика и рейтинг модели.
     */
    @Query("""
        UPDATE models SET 
            isAvailable = :isAvailable,
            availabilityResponseTimeMs = :responseTimeMs,
            rating = :rating,
            lastAvailabilityCheck = :checkTime
        WHERE id = :modelId
    """)
    suspend fun updateModelRating(
        modelId: String,
        isAvailable: Boolean,
        responseTimeMs: Long,
        rating: Float,
        checkTime: Long
    ): Int
}
