package com.agentflow.data.repository
import androidx.room.withTransaction
import com.agentflow.data.dao.TaskDao
import com.agentflow.data.dao.TaskDependencyDao
import com.agentflow.data.dao.TaskResultDao
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskResult
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.validation.DependencyGraph
import com.agentflow.domain.validation.TaskStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskGraphRepository(
    private val db: AgentFlowDatabase,
    private val taskDao: TaskDao,
    private val dependencyDao: TaskDependencyDao,
    private val resultDao: TaskResultDao,
) {
    fun observeTasks(missionId: String): Flow<List<Task>> =
        taskDao.observeByMission(missionId).map { list -> list.map { it.toDomain() } }

    suspend fun listTasks(missionId: String): List<Task> = taskDao.listByMission(missionId).map { it.toDomain() }
    suspend fun get(id: String): Task? = taskDao.getById(id)?.toDomain()
    suspend fun insertTask(task: Task) { taskDao.insert(task.toEntity()) }
    suspend fun transition(taskId: String, to: TaskStatus, now: Long = System.currentTimeMillis()) {
        val current = taskDao.getById(taskId) ?: return
        val from = TaskStatus.valueOf(current.status)
        TaskStateMachine.transition(from, to)
        val startedAt = current.startedAt ?: if (to == TaskStatus.RUNNING) now else current.startedAt
        val completedAt = if (to == TaskStatus.COMPLETED || to == TaskStatus.FAILED) now else current.completedAt
        taskDao.update(current.copy(
            status = to.name, updatedAt = now, startedAt = startedAt, completedAt = completedAt,
            retryCount = if (to == TaskStatus.RETRYING) current.retryCount + 1 else current.retryCount,
        ))
    }
    suspend fun addDependency(dep: TaskDependency) {
        db.withTransaction {
            val tasks = taskDao.listByMission(dep.missionId).map { it.id }.toSet()
            val existing = dependencyDao.listByMission(dep.missionId).map { it.toDomain() }
            DependencyGraph.validateNew(existing, dep, tasks)
            dependencyDao.insert(dep.toEntity())
        }
    }
    suspend fun listDependencies(missionId: String): List<TaskDependency> =
        dependencyDao.listByMission(missionId).map { it.toDomain() }
    suspend fun saveResult(result: TaskResult) = resultDao.insert(result.toEntity())
    suspend fun resultsFor(taskId: String): List<TaskResult> = resultDao.listByTask(taskId).map { it.toDomain() }
}
