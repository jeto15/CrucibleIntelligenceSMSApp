package com.example.crucibleintelligencesmsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: EntityProfile): Long

    @Query("SELECT * FROM entity_profile WHERE id = :id")
    suspend fun getProfileById(id: Long): EntityProfile?

    @Query("SELECT * FROM entity_profile WHERE id = :id")
    fun observeProfileById(id: Long): Flow<EntityProfile?>

    @Query("SELECT * FROM entity_profile WHERE phoneNumber = :phoneNumber OR normalizedNumber = :phoneNumber LIMIT 1")
    suspend fun getProfileByPhoneNumber(phoneNumber: String): EntityProfile?
}
