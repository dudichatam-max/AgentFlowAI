package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.MissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeByProject(projectId: String): Flow<List<MissionEntity>>
    @Query("SELECT * FROM missions WHERE status NOT IN ('APPROVED','FAILED','CANCELLED') ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<MissionEntity>>
    @Query("SELECT * FROM missions WHERE id = :id")
    suspend fun getById(id: String): MissionEntity?
    @Query("SELECT * FROM missions WHERE status NOT IN ('APPROVED','FAILED','CANCELLED')")
    suspend fun listActive(): List<MissionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MissionEntity)
    @Update
    suspend fun update(entity: MissionEntity)
    @Query("DELETE FROM missions WHERE id = :id")
    suspend fun deleteById(id: String)
}
