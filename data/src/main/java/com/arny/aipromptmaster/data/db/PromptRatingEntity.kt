package com.arny.aipromptmaster.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_ratings")
data class PromptRatingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "prompt_id")
    val promptId: Int,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "rating")
    val rating: Int
)
