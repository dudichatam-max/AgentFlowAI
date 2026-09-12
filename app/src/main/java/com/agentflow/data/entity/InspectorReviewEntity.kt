package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspector_reviews",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["missionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("missionId"), Index("taskId"), Index("status")],
)
data class InspectorReviewEntity(
    @PrimaryKey val id: String,
    val missionId: String,
    val taskId: String?,
    val inspectorAgentId: String,
    val status: String,
    val summary: String,
    val reason: String,
    val severity: String,
    val createdAt: Long,
    val rejectedArtifactId: String?,
    val rejectedArtifactVersion: Int?,
)
