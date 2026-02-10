package com.arny.aiprompts.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Миграция базы данных с версии 1 на версию 2.
 * 
 * Добавляет таблицы для хранения сессий чата и сообщений:
 * - chat_sessions: хранит метаданные сессий чата
 * - chat_messages: хранит сообщения внутри сессий
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Создаем таблицу сессий чата
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                system_prompt TEXT,
                temperature REAL NOT NULL DEFAULT 0.7,
                max_tokens INTEGER NOT NULL DEFAULT 2048,
                top_p REAL NOT NULL DEFAULT 0.9,
                context_window INTEGER NOT NULL DEFAULT 10,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                is_archived INTEGER NOT NULL DEFAULT 0,
                model_id TEXT
            )
            """.trimIndent()
        )
        
        // Создаем индексы для chat_sessions
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS index_chat_sessions_updated_at 
            ON chat_sessions(updated_at)
            """.trimIndent()
        )
        
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS index_chat_sessions_is_archived 
            ON chat_sessions(is_archived)
            """.trimIndent()
        )
        
        // Создаем таблицу сообщений чата
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                status TEXT NOT NULL,
                model_id TEXT,
                token_count INTEGER,
                edited_at INTEGER,
                order_index INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        
        // Создаем индексы для chat_messages
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS index_chat_messages_session_id 
            ON chat_messages(session_id)
            """.trimIndent()
        )
        
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp 
            ON chat_messages(timestamp)
            """.trimIndent()
        )
        
        connection.execute(
            """
            CREATE INDEX IF NOT EXISTS index_chat_messages_order_index 
            ON chat_messages(order_index)
            """.trimIndent()
        )
    }
}

/**
 * Вспомогательная функция для выполнения SQL запросов
 */
private fun SQLiteConnection.execute(sql: String) {
    prepare(sql).use { statement ->
        statement.step()
    }
}

/**
 * Список всех миграций для базы данных.
 * Используется при создании DatabaseBuilder.
 */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
