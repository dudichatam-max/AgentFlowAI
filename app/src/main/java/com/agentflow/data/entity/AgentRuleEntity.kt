package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_rules",
    foreignKeys = [ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("agentId"), Index("priority"), Index("enabled")],
)
data class AgentRuleEntity(
    @PrimaryKey val id: String,
    val agentId: String, val name: String, val instruction: String, val priority: String,
    val enabled: Boolean, val createdAt: Long, val updatedAt: Long,
)
