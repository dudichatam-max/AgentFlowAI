package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AgentEntity::class, parentColumns = ["id"], childColumns = ["agentId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("projectId"), Index("agentId"), Index("updatedAt"), Index("lastMessageAt")],
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val agentId: String,
    val title: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
)
