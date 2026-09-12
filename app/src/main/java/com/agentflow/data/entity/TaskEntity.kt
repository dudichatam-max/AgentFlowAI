package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["missionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("missionId"), Index("assignedAgentId"), Index("status"), Index("parentTaskId"), Index("priority")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val missionId: String, val parentTaskId: String?, val createdByType: String,
    val createdById: String, val assignedAgentId: String, val title: String, val description: String,
    val status: String, val priority: String, val inputMessageId: String?,
    val startedAt: Long?, val completedAt: Long?, val retryCount: Int,
    val createdAt: Long, val updatedAt: Long,
)
