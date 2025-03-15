package com.arny.aipromptmaster.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.daos.PromptHistoryDao
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.data.db.entities.PromptHistoryEntity

@Database(
    entities = [
        PromptEntity::class,
        PromptHistoryEntity::class,
    ],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun promptHistoryDao(): PromptHistoryDao

    companion object {
        const val DBNAME = "AiPromptMasterDB"
    }
}