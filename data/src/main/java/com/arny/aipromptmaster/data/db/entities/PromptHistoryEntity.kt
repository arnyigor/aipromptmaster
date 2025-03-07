package com.arny.aipromptmaster.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_history")
data class PromptHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "prompt_id")
    val promptId: Int,

    @ColumnInfo(name = "version")
    val version: Int,

    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long
)