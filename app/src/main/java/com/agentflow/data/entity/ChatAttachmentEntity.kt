package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_attachments",
    foreignKeys = [
        ForeignKey(entity = ChatMessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("messageId")],
)
data class ChatAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val path: String,
)
