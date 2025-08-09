package com.arny.aiprompts.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "prompts",
    indices = [
        Index(value = ["title"]),
        Index(value = ["category"]),
        Index(value = ["status"]),
        Index(value = ["is_favorite"]),
        Index(value = ["is_local"]),
    ]
)
data class PromptEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "content_ru")
    val contentRu: String = "",

    @ColumnInfo(name = "content_en")
    val contentEn: String = "",

    @ColumnInfo(name = "variables_json")
    val variablesJson: String = "{}",

    @ColumnInfo(name = "compatible_models")
    val compatibleModels: String = "",

    @ColumnInfo(name = "category")
    val category: String = "",

    @ColumnInfo(name = "tags")
    val tags: String = "",

    @ColumnInfo(name = "is_local")
    val isLocal: Boolean = true,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "rating")
    val rating: Float = 0f,

    @ColumnInfo(name = "rating_votes")
    val ratingVotes: Int = 0,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "author")
    val author: String = "",

    @ColumnInfo(name = "author_id")
    val authorId: String = "",

    @ColumnInfo(name = "source")
    val source: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "version")
    val version: String = "1.0.0",

    @ColumnInfo(name = "created_at")
    val createdAt: String = "",

    @ColumnInfo(name = "modified_at")
    val modifiedAt: String = "",
)