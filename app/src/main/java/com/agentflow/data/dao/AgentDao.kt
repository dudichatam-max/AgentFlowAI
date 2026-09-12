package com.agentflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.AgentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents WHERE projectId = :projectId ORDER BY name")
    fun observeByProject(projectId: String): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    fun observeById(id: String): Flow<AgentEntity?>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getById(id: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE projectId = :projectId ORDER BY name")
    suspend fun listByProject(projectId: String): List<AgentEntity>

    @Query("SELECT * FROM agents WHERE projectId = :projectId AND status != 'DISABLED'")
    suspend fun listEnabled(projectId: String): List<AgentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AgentEntity)

    @Update
    suspend fun update(entity: AgentEntity)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun deleteById(id: String)
}
