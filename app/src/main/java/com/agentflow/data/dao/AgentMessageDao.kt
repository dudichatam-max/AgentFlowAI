package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.AgentMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentMessageDao {
    @Query("SELECT * FROM agent_messages WHERE missionId = :missionId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun pageByMission(missionId: String, limit: Int, offset: Int): List<AgentMessageEntity>
    @Query("SELECT * FROM agent_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeSession(sessionId: String): Flow<List<AgentMessageEntity>>
    @Query("SELECT * FROM agent_messages WHERE id = :id")
    suspend fun getById(id: String): AgentMessageEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AgentMessageEntity)
}
