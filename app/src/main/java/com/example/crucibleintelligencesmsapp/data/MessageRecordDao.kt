package com.example.crucibleintelligencesmsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageRecord): Long

    @Query("SELECT * FROM message_record WHERE entityId = :entityId ORDER BY timestamp DESC")
    suspend fun getMessagesForEntity(entityId: Long): List<MessageRecord>

    @Query("SELECT * FROM message_record WHERE entityId = :entityId ORDER BY timestamp ASC")
    fun observeMessagesForEntity(entityId: Long): Flow<List<MessageRecord>>
}
