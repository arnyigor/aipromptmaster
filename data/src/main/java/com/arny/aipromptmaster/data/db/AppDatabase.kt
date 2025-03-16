package com.arny.aipromptmaster.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.entities.PromptEntity

@Database(
    entities = [
        PromptEntity::class,
    ],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao

    companion object {
        const val DBNAME = "AiPromptMasterDB"
    }
}