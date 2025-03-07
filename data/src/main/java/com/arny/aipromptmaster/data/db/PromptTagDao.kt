package com.arny.aipromptmaster.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PromptTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: PromptTagCrossRef)

    @Query("DELETE FROM prompt_tags WHERE prompt_id = :promptId")
    suspend fun deleteByPromptId(promptId: Int)
}