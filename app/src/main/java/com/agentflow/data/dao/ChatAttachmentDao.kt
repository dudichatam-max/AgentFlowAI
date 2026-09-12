package com.agentflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.ChatAttachmentEntity

@Dao
interface ChatAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ChatAttachmentEntity)

    @Query("SELECT * FROM chat_attachments WHERE messageId = :messageId")
    suspend fun listForMessage(messageId: String): List<ChatAttachmentEntity>
}
