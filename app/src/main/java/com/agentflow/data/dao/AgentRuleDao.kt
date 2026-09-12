package com.agentflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.AgentRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentRuleDao {
    @Query("SELECT * FROM agent_rules WHERE agentId = :agentId")
    fun observeByAgent(agentId: String): Flow<List<AgentRuleEntity>>

    @Query("SELECT * FROM agent_rules WHERE agentId = :agentId")
    suspend fun listByAgent(agentId: String): List<AgentRuleEntity>

    @Query("SELECT * FROM agent_rules WHERE agentId = :agentId AND enabled = 1")
    suspend fun listEnabled(agentId: String): List<AgentRuleEntity>

    @Query("SELECT * FROM agent_rules WHERE id = :id")
    suspend fun getById(id: String): AgentRuleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AgentRuleEntity)

    @Update
    suspend fun update(entity: AgentRuleEntity)

    @Query("DELETE FROM agent_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM agent_rules WHERE agentId = :agentId")
    suspend fun deleteByAgent(agentId: String)
}
