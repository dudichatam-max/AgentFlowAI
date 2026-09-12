package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mission_artifact_pending",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["missionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("missionId")],
)
data class MissionArtifactPendingEntity(
    @PrimaryKey val id: String,
    val missionId: String,
    val type: String,
    val name: String,
    val path: String?,
    val contentHash: String?,
    val sizeBytes: Long,
    val mimeType: String?,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val content: String,
)
