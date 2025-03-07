package com.arny.aipromptmaster.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arny.aipromptmaster.data.db.entities.PromptVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptVersionDao {
    @Query("SELECT * FROM prompt_versions WHERE prompt_id = :promptId ORDER BY version DESC LIMIT 10")
    fun getVersionsForPrompt(promptId: String): Flow<List<PromptVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: PromptVersionEntity)
}