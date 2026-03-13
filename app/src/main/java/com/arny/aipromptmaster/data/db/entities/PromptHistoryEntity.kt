package com.arny.aipromptmaster.data.db.entities
/*

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "prompt_history",
    foreignKeys = [
        ForeignKey(
            entity = PromptEntity::class,
            parentColumns = ["_id"],
            childColumns = ["prompt_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["prompt_id"]),
        Index(value = ["modified_by"]),
        Index(value = ["modified_at"])
    ]
)
data class PromptHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "prompt_id")
    val promptId: String,

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
*/
