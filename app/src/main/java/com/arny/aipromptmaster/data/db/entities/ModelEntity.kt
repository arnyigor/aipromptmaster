package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity

/**
 * Модель провайдера. Ключ составной: одинаковый `id` у разных провайдеров — это разные записи.
 * `providerId` у записей, созданных до появления ключа, нормализуется в `openrouter`.
 */
@Entity(tableName = "models", primaryKeys = ["providerId", "id"])
data class ModelEntity(
    /** Профиль провайдера-владельца; `openrouter` для legacy-записей. */
    val providerId: String = "openrouter",
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

