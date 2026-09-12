package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE missionId = :missionId ORDER BY createdAt")
    fun observeByMission(missionId: String): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE missionId = :missionId")
    suspend fun listByMission(missionId: String): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?
    @Query("SELECT * FROM tasks WHERE missionId = :missionId AND status = :status")
    suspend fun listByStatus(missionId: String, status: String): List<TaskEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TaskEntity)
    @Update
    suspend fun update(entity: TaskEntity)
}
