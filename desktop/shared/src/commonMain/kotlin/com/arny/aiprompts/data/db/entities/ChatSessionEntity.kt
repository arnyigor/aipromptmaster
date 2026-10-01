package com.arny.aiprompts.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity для хранения сессий чата в базе данных Room.
 * 
 * @property id Уникальный идентификатор сессии
 * @property name Название сессии (отображается в списке)
 * @property systemPrompt System prompt для данной сессии (может быть null)
 * @property temperature Температура генерации (0.0 - 2.0)
 * @property maxTokens Максимальное количество токенов
 * @property topP Top-p sampling параметр
 * @property contextWindow Размер окна контекста (количество сообщений)
 * @property createdAt Время создания сессии (timestamp)
 * @property updatedAt Время последнего обновления (timestamp)
 * @property isArchived Флаг архивирования сессии
 * @property modelId ID выбранной модели для сессии (null = использовать глобальную)
 */
@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["updated_at"]), // Для сортировки по времени
        Index(value = ["is_archived"]) // Для фильтрации архивных
    ]
)
data class ChatSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String?,

    @ColumnInfo(name = "temperature")
    val temperature: Float = 0.7f,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 2048,

    @ColumnInfo(name = "top_p")
    val topP: Float = 0.9f,

    @ColumnInfo(name = "context_window")
    val contextWindow: Int = 10,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "model_id")
    val modelId: String? = null
)
