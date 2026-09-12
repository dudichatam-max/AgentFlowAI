package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "missions",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("status"), Index("updatedAt")],
)
data class MissionEntity(
    @PrimaryKey val id: String,
    val projectId: String, val title: String, val description: String, val status: String,
    val createdAt: Long, val updatedAt: Long, val startedAt: Long?, val completedAt: Long?,
    val createdBy: String,
)
