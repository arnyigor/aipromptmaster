package com.arny.aipromptmaster.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import com.arny.aipromptmaster.data.db.BlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM BlockEntity")
    fun getAll(): Flow<List<BlockEntity>>

    @Insert(onConflict = REPLACE)
    suspend fun insert(block: BlockEntity)
}