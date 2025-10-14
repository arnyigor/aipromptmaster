package com.arny.aipromptmaster.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.daos.ConversationFileDao
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.ConversationFileEntity
import com.arny.aipromptmaster.data.db.entities.PromptEntity

@Database(
    entities = [
        PromptEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ConversationFileEntity::class,
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun chatDao(): ChatDao
    abstract fun conversationFileDao(): ConversationFileDao

    companion object {
        const val DBNAME = "AiPromptMasterDB"

        /**
         * Ручная миграция с версии 1 на 2.
         * Создает таблицы 'conversations' и 'messages'.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL для создания таблицы диалогов
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `lastUpdated` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // SQL для создания таблицы сообщений
                // ВАЖНО: Я предполагаю структуру MessageEntity.
                // Адаптируй ее под свою реальную сущность.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `id` TEXT NOT NULL, 
                        `conversationId` TEXT NOT NULL, 
                        `role` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // Создаем индекс для быстрой выборки сообщений по conversationId
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем новую колонку 'systemPrompt' в таблицу 'conversations'
                db.execSQL("ALTER TABLE conversations ADD COLUMN systemPrompt TEXT")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем новую колонку. NOT NULL и DEFAULT '[]' важны для стабильности.
                db.execSQL("ALTER TABLE prompts ADD COLUMN prompt_variants_json TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /**
         * 🔥 МИГРАЦИЯ 4 -> 5: Создание таблицы conversation_files
         * Удаление поля fileAttachment из chat_messages
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Создаем новую таблицу для файлов
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversation_files (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        fileId TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        fileExtension TEXT NOT NULL,
                        fileSize INTEGER NOT NULL,
                        mimeType TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        preview TEXT,
                        uploadedAt INTEGER NOT NULL,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 2. Создаем индексы
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_conversation_files_conversationId
                    ON conversation_files(conversationId)
                """.trimIndent())

                database.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_conversation_files_fileId
                    ON conversation_files(fileId)
                """.trimIndent())

                // 3. Мигрируем существующие файлы из сообщений (если есть поле fileAttachment)
                try {
                    // Проверяем, есть ли столбец fileAttachment
                    val cursor = database.query("PRAGMA table_info(messages)")
                    var hasFileAttachment = false

                    while (cursor.moveToNext()) {
                        val columnName = cursor.getString(cursor.getColumnIndex("name"))
                        if (columnName == "fileAttachment") {
                            hasFileAttachment = true
                            break
                        }
                    }
                    cursor.close()

                    if (hasFileAttachment) {
                        // Создаем временную таблицу без fileAttachment
                        database.execSQL("""
                            CREATE TABLE messages_new (
                                id TEXT NOT NULL PRIMARY KEY,
                                conversationId TEXT NOT NULL,
                                role TEXT NOT NULL,
                                content TEXT NOT NULL,
                                timestamp INTEGER NOT NULL,
                                FOREIGN KEY(conversationId) REFERENCES conversations(id) ON DELETE CASCADE
                            )
                        """.trimIndent())

                        // Копируем данные (без fileAttachment)
                        database.execSQL("""
                            INSERT INTO messages_new (id, conversationId, role, content, timestamp)
                            SELECT id, conversationId, role, content, timestamp
                            FROM messages
                        """.trimIndent())

                        // Удаляем старую таблицу
                        database.execSQL("DROP TABLE messages")

                        // Переименовываем новую таблицу
                        database.execSQL("ALTER TABLE messages_new RENAME TO messages")

                        // Воссоздаем индексы
                        database.execSQL("""
                            CREATE INDEX IF NOT EXISTS index_messages_conversationId
                            ON messages(conversationId)
                        """.trimIndent())
                    }
                } catch (e: Exception) {
                    // Если миграция не удалась, логируем ошибку
                    android.util.Log.e("Migration", "Error migrating file attachments", e)
                }
            }
        }
    }
}