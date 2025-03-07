package com.arny.aipromptmaster.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arny.aipromptmaster.domain.models.PromptCategory
import com.arny.aipromptmaster.domain.models.PromptStatus
import java.util.Date
import java.util.UUID

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String,

    @ColumnInfo(name = "description")
    val description: String?,

    val template: String,

    @ColumnInfo(name = "variables")
    val variables: Map<String, String>?,

    @ColumnInfo(name = "ai_model")
    val aiModel: String,

    val category: PromptCategory,

    val language: String = "",

    val tags: List<String> = emptyList(),

    @ColumnInfo(name = "is_private")
    val isPrivate: Boolean = false,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    val rating: Float = 0f,

    @ColumnInfo(name = "success_rate")
    val successRate: Float? = null,

    val status: PromptStatus,

    val settings: Map<String, Any>?,

    @ColumnInfo(name = "author_id")
    val authorId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "modified_at")
    val modifiedAt: Date = Date(),

    @ColumnInfo(name = "parent_id")
    val parentId: String?,

    val version: Int = 1
)