package com.arny.aiprompts.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.arny.aiprompts.data.db.daos.ChatMessageDao
import com.arny.aiprompts.data.db.daos.ChatSessionDao
import com.arny.aiprompts.data.db.daos.PromptDao
import com.arny.aiprompts.data.db.entities.ChatMessageEntity
import com.arny.aiprompts.data.db.entities.ChatSessionEntity
import com.arny.aiprompts.data.db.entities.PromptEntity

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseCtor : RoomDatabaseConstructor<AppDatabase>

/**
 * Platform-specific function to get database instance.
 */
expect fun getAppDatabase(): AppDatabase

/**
 * Главный класс базы данных Room для приложения AI Prompts.
 *
 * Версия 2: Добавлены таблицы для чатов
 * - chat_sessions: хранит сессии чата с настройками
 * - chat_messages: хранит сообщения внутри сессий
 *
 * @property promptDao DAO для работы с промптами
 * @property chatSessionDao DAO для работы с сессиями чата
 * @property chatMessageDao DAO для работы с сообщениями чата
 */
@Database(
    entities = [
        PromptEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = true
)
@ConstructedBy(AppDatabaseCtor::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Возвращает DAO для работы с промптами.
     */
    abstract fun promptDao(): PromptDao

    /**
     * Возвращает DAO для работы с сессиями чата.
     */
    abstract fun chatSessionDao(): ChatSessionDao

    /**
     * Возвращает DAO для работы с сообщениями чата.
     */
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        /**
         * Имя файла базы данных.
         */
        const val DB_NAME = "AiPromptDB.db"
    }
}
