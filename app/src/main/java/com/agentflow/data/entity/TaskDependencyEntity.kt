package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_dependencies",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["missionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("missionId"), Index("taskId"), Index("dependsOnTaskId"), Index(value = ["taskId", "dependsOnTaskId"], unique = true)],
)
data class TaskDependencyEntity(
    @PrimaryKey val id: String,
    val missionId: String, val taskId: String, val dependsOnTaskId: String,
    val type: String, val createdAt: Long,
)
