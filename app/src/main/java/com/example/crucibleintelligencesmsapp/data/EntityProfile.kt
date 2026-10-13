package com.example.crucibleintelligencesmsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entity_profile")
data class EntityProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String?,
    val phoneNumber: String,
    val normalizedNumber: String,
    val notes: String? = null
)
