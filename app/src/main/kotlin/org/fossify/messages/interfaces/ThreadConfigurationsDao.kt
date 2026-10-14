package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.ThreadConfiguration

@Dao
interface ThreadConfigurationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(threadConfiguration: ThreadConfiguration): Long

    @Query("SELECT * FROM thread_configurations WHERE thread_id = :threadId")
    fun getConfiguration(threadId: Long): ThreadConfiguration?

    @Query("DELETE FROM thread_configurations WHERE thread_id = :threadId")
    fun delete(threadId: Long): Int
}
