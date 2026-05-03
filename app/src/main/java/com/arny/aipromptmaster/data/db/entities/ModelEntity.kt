package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Int,
    val pricingPrompt: String, // Храним в милицентах: "100" = $0.001
    val pricingCompletion: String,
    val pricingImage: String?, // Nullable для моделей без multimodal
    val inputModalities: String, // JSON array: ["text","image"]
    val outputModalities: String,
    val isMultimodal: Boolean,
    val isFavorite: Boolean = false,
    val isFree: Boolean = false,
    val isSelected: Boolean = false,
    val sortPriority: Int = 0,
    val isAvailable: Boolean? = null, // null = не проверена, true = доступна, false = недоступна
    /** Время отклика в миллисекундах при проверке доступности */
    val availabilityResponseTimeMs: Long? = null,
    /** Рейтинг модели (0-100), рассчитывается после проверки */
    val rating: Float? = null,
    /** Время последней проверки доступности */
    val lastAvailabilityCheck: Long? = null,
    /** При последнем синхронизировании. 0 – никогда не синхронизировалось */
    val lastUpdated: Long = 0L
)

