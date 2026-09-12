package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.TaskDependencyEntity

@Dao
interface TaskDependencyDao {
    @Query("SELECT * FROM task_dependencies WHERE missionId = :missionId")
    suspend fun listByMission(missionId: String): List<TaskDependencyEntity>
    @Query("SELECT * FROM task_dependencies WHERE taskId = :taskId")
    suspend fun listForTask(taskId: String): List<TaskDependencyEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TaskDependencyEntity)
    @Query("DELETE FROM task_dependencies WHERE id = :id")
    suspend fun deleteById(id: String)
}
