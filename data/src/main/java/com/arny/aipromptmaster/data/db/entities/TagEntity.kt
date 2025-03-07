package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// Таблица тегов
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val color: String // Hex-код цвета
)
