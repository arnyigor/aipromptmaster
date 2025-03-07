package com.arny.aipromptmaster.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val username: String,

    val email: String,

    val role: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date()
)