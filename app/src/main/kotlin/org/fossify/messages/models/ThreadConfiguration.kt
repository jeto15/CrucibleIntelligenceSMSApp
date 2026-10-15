package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "thread_configurations")
data class ThreadConfiguration(
    @PrimaryKey
    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "gender_energy")
    val genderEnergy: String = "Masculine",

    @ColumnInfo(name = "relationship_type")
    val relationshipType: String = "Dating/Romantic",

    @ColumnInfo(name = "relationship_goal")
    val relationshipGoal: String = "Build Attraction",

    @ColumnInfo(name = "score")
    val score: Int? = null,

    @ColumnInfo(name = "score_justification")
    val scoreJustification: String? = null,

    @ColumnInfo(name = "dossier_report_json")
    val dossierReportJson: String? = null,

    @ColumnInfo(name = "prompt_token_count")
    val promptTokenCount: Int = 0,

    @ColumnInfo(name = "candidates_token_count")
    val candidatesTokenCount: Int = 0,

    @ColumnInfo(name = "manual_notes")
    val manualNotes: String? = null,

    @ColumnInfo(name = "updated_timestamp")
    val updatedTimestamp: Long = System.currentTimeMillis()
)
