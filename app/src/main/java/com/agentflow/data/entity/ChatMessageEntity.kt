package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(entity = ChatSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("createdAt"), Index("status")],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val provider: String?,
    val model: String?,
    val latencyMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val errorCode: String?,
    val generationGroupId: String?,
    val bookmarked: Boolean,
)
