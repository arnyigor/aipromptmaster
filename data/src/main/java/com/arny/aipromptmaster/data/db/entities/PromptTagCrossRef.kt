package com.arny.aipromptmaster.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey

// Связующая таблица для промптов и тегов
@Entity(
    tableName = "prompt_tag_cross_ref",
    primaryKeys = ["promptId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = PromptEntity::class,
            parentColumns = ["id"],
            childColumns = ["promptId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PromptTagCrossRef(
    val promptId: String,
    val tagId: String
)