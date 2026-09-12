package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.TaskResultEntity

@Dao
interface TaskResultDao {
    @Query("SELECT * FROM task_results WHERE taskId = :taskId ORDER BY createdAt DESC")
    suspend fun listByTask(taskId: String): List<TaskResultEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskResultEntity)
}
