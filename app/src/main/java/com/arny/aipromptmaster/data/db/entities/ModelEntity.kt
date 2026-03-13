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
    /** При последнем синхронизировании. 0 – никогда не синхронизировалось */
    val lastUpdated: Long = 0L
)

