package com.arny.aipromptmaster.data.db

import androidx.room.Entity

@Entity(
    tableName = "prompt_tags",
    primaryKeys = ["prompt_id", "tag_id"]
)
data class PromptTagCrossRef(
    val prompt_id: Int,
    val tag_id: Int
)