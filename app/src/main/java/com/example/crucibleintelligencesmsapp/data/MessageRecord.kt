package com.example.crucibleintelligencesmsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_record")
data class MessageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityId: Long,
    val content: String,
    val timestamp: Long,
    val isIncoming: Boolean,
    val status: String = "SENT"
)
