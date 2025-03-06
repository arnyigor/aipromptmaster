package com.arny.aipromptmaster.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BlockEntity(
    @PrimaryKey val id: String,
    val type: String, // "TEXT" или "PARAM"
    val content: String,
    val key: String?,
    val value: String?,
    val paramType: String? // для ParameterBlock
)