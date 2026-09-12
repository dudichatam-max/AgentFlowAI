package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.ReferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {
    @Query("SELECT * FROM `references` WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeByProject(projectId: String): Flow<List<ReferenceEntity>>
    @Query("SELECT * FROM `references` WHERE agentId = :agentId ORDER BY updatedAt DESC")
    fun observeByAgent(agentId: String): Flow<List<ReferenceEntity>>
    @Query("SELECT * FROM `references` WHERE id = :id")
    suspend fun getById(id: String): ReferenceEntity?
    @Query("SELECT * FROM `references` WHERE projectId = :projectId ORDER BY updatedAt DESC")
    suspend fun listByProject(projectId: String): List<ReferenceEntity>
    @Query("SELECT * FROM `references` WHERE contentHash = :hash")
    suspend fun findByHash(hash: String): List<ReferenceEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReferenceEntity)
    @Update
    suspend fun update(entity: ReferenceEntity)
    @Query("DELETE FROM `references` WHERE id = :id")
    suspend fun deleteById(id: String)
}
