package com.arny.aipromptmaster.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.daos.PromptTagDao
import com.arny.aipromptmaster.data.db.daos.PromptVersionDao
import com.arny.aipromptmaster.data.db.daos.TagDao
import com.arny.aipromptmaster.data.db.entities.PromptEntity
import com.arny.aipromptmaster.data.db.entities.PromptHistoryEntity
import com.arny.aipromptmaster.data.db.entities.PromptRatingEntity
import com.arny.aipromptmaster.data.db.entities.PromptTagCrossRef
import com.arny.aipromptmaster.data.db.entities.PromptVersionEntity
import com.arny.aipromptmaster.data.db.entities.TagEntity

@Database(
    entities = [
        PromptEntity::class,
        TagEntity::class,
        PromptVersionEntity::class,
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
    abstract fun promptVersionDao(): PromptVersionDao

    companion object {
        const val DBNAME = "AiPromptMasterDB"
    }
}