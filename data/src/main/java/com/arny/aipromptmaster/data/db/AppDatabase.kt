package com.arny.aipromptmaster.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.entities.ConversationEntity
import com.arny.aipromptmaster.data.db.entities.MessageEntity
import com.arny.aipromptmaster.data.db.entities.PromptEntity

@Database(
    entities = [
        PromptEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
    ],
    autoMigrations = [
        AutoMigration(1, 2)
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun chatDao(): ChatDao

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
    }
}