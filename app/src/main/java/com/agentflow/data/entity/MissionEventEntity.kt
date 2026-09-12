package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mission_events",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["missionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("missionId"), Index("createdAt"), Index("type")],
)
data class MissionEventEntity(
    @PrimaryKey val id: String,
    val missionId: String, val type: String, val message: String,
    val relatedTaskId: String?, val relatedMessageId: String?, val createdAt: Long,
)
