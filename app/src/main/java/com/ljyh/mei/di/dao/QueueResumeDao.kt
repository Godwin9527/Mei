package com.ljyh.mei.di.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ljyh.mei.data.model.room.QueueResume
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueResumeDao {
    @Query("SELECT * FROM queue_resume WHERE queueKey = :queueKey")
    suspend fun get(queueKey: String): QueueResume?

    @Query("SELECT * FROM queue_resume")
    fun observeAll(): Flow<List<QueueResume>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QueueResume)

    @Query("DELETE FROM queue_resume WHERE queueKey = :queueKey")
    suspend fun delete(queueKey: String)

    @Query("DELETE FROM queue_resume")
    suspend fun clear()
}
