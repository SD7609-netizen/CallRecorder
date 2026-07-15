package com.callrecorder.android.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY dateMillis DESC")
    fun getAllRecordings(): LiveData<List<Recording>>

    @Query("SELECT * FROM recordings ORDER BY dateMillis DESC")
    suspend fun getAllSync(): List<Recording>

    @Insert
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Delete
    suspend fun delete(recording: Recording)

    @Query("DELETE FROM recordings WHERE dateMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
