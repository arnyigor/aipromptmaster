package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.aipromptmaster.data.db.entities.PromptTagCrossRef

@Dao
interface PromptTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: PromptTagCrossRef)

    @Query("DELETE FROM tags WHERE id = :promptId")
    suspend fun deleteByPromptId(promptId: String)
}