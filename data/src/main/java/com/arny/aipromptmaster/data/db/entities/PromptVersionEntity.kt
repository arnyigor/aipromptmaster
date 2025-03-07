package com.arny.aipromptmaster.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "prompt_versions")
data class PromptVersionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "prompt_id")
    val promptId: String,

    val version: Int,

    val template: String,

    val variables: Map<String, String>?,

    @ColumnInfo(name = "modified_at")
    val modifiedAt: Date = Date(),

    @ColumnInfo(name = "modified_by")
    val modifiedBy: String?
)