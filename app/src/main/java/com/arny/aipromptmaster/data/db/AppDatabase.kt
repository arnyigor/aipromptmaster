package com.arny.aipromptmaster.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.daos.ModelDao
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.data.db.entities.ModelEntity
import com.arny.aipromptmaster.data.db.entities.PromptEntity

@Database(
    entities = [
        PromptEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ModelEntity::class,
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao

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

        // Early versions from the former data module lack models and message attachments.
        // Later exported versions 4/5 already contain them; reconcile both histories without dropping data.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val hasAttachments = db.query("PRAGMA table_info(messages)").use { cursor ->
                    val column = cursor.getColumnIndexOrThrow("name")
                    var found = false
                    while (cursor.moveToNext()) if (cursor.getString(column) == "attachments_json") found = true
                    found
                }
                if (!hasAttachments) db.execSQL("ALTER TABLE messages ADD COLUMN attachments_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS models (
                        id TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL,
                        contextLength INTEGER NOT NULL, pricingPrompt TEXT NOT NULL,
                        pricingCompletion TEXT NOT NULL, pricingImage TEXT,
                        inputModalities TEXT NOT NULL, outputModalities TEXT NOT NULL,
                        isMultimodal INTEGER NOT NULL, isFavorite INTEGER NOT NULL,
                        isFree INTEGER NOT NULL, isSelected INTEGER NOT NULL,
                        sortPriority INTEGER NOT NULL, lastUpdated INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем колонку model_id в таблицу messages
                db.execSQL("ALTER TABLE messages ADD COLUMN model_id TEXT")
            }
        }


val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем колонку isAvailable в таблицу models
                db.execSQL("ALTER TABLE models ADD COLUMN isAvailable INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Добавляем колонки для рейтинга и времени отклика
                db.execSQL("ALTER TABLE models ADD COLUMN availabilityResponseTimeMs INTEGER")
                db.execSQL("ALTER TABLE models ADD COLUMN rating REAL")
                db.execSQL("ALTER TABLE models ADD COLUMN lastAvailabilityCheck INTEGER")
            }
        }
    }
}
