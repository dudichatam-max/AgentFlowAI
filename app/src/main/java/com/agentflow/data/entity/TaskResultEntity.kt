package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_results",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index("missionId")],
)
data class TaskResultEntity(
    @PrimaryKey val id: String,
    val taskId: String, val missionId: String, val content: String,
    val contractVersion: Int, val confidence: Double?, val createdAt: Long,
)
