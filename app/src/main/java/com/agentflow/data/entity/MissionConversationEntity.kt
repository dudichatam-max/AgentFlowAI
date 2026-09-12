package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mission_conversations",
    foreignKeys = [ForeignKey(entity = MissionEntity::class, parentColumns = ["id"], childColumns = ["missionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("missionId"), Index("agentId")],
)
data class MissionConversationEntity(
    @PrimaryKey val id: String,
    val missionId: String, val agentId: String, val createdAt: Long, val updatedAt: Long,
)
