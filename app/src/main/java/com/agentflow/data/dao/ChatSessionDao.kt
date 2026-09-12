package com.agentflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ChatSessionEntity)

    @Update
    suspend fun update(entity: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    fun observeById(id: String): Flow<ChatSessionEntity?>

    @Query("SELECT * FROM chat_sessions WHERE agentId = :agentId ORDER BY lastMessageAt DESC")
    fun observeForAgent(agentId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE projectId = :projectId ORDER BY lastMessageAt DESC")
    fun observeForProject(projectId: String): Flow<List<ChatSessionEntity>>

    @Query("UPDATE chat_sessions SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, now: Long)

    @Query("UPDATE chat_sessions SET lastMessageAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun updateLastMessageAt(id: String, at: Long)
}
