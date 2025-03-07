package com.arny.aipromptmaster.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PromptEntity::class,
        TagEntity::class,
        PromptTagCrossRef::class,
        PromptHistoryEntity::class,
        PromptRatingEntity::class
    ],
    version = 1,
)
@TypeConverters(GsonTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun tagDao(): TagDao
    abstract fun promptTagDao(): PromptTagDao

    companion object {
        const val DBNAME = "AiPromptMasterDB"
    }
}