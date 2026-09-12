package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "agent_messages", indices = [Index("missionId"), Index("taskId"), Index("sessionId"), Index("createdAt"), Index("messageType")])
data class AgentMessageEntity(
    @PrimaryKey val id: String,
    val missionId: String?, val taskId: String?, val sessionId: String?,
    val senderType: String, val senderId: String?, val recipientType: String, val recipientId: String?,
    val messageType: String, val content: String, val contractVersion: Int, val createdAt: Long,
    val providerId: String?, val modelId: String?, val latencyMs: Long?,
    val inputTokens: Int?, val outputTokens: Int?, val success: Boolean, val errorCode: String?,
)
