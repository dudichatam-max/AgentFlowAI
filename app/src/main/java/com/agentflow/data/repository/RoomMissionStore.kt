package com.agentflow.data.repository

import com.agentflow.data.dao.MissionDao
import com.agentflow.data.dao.MissionEventDao
import com.agentflow.data.dao.TaskDao
import com.agentflow.data.dao.TaskDependencyDao
import com.agentflow.data.dao.TaskResultDao
import com.agentflow.data.dao.MissionLoopGuardDao
import com.agentflow.data.entity.MissionLoopGuardEntity
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskResult

import androidx.room.withTransaction

class RoomMissionStore(
    private val db: AgentFlowDatabase,
    private val missions: MissionDao = db.missionDao(),
    private val events: MissionEventDao = db.missionEventDao(),
    private val tasks: TaskDao = db.taskDao(),
    private val deps: TaskDependencyDao = db.taskDependencyDao(),
    private val results: TaskResultDao = db.taskResultDao(),
    private val agents: AgentRepository = AgentRepository(db),
    private val references: ReferenceRepository = ReferenceRepository(db.referenceDao()),
    private val loopGuard: MissionLoopGuardDao = db.missionLoopGuardDao(),
) : MissionStore {
    override suspend fun getMission(id: String): Mission? = missions.getById(id)?.toDomain()
    override suspend fun insertMission(mission: Mission) = missions.insert(mission.toEntity())
    override suspend fun updateMission(mission: Mission) = missions.update(mission.toEntity())
    override suspend fun listActiveMissions(): List<Mission> = missions.listActive().map { it.toDomain() }

    override suspend fun getTask(id: String) = tasks.getById(id)?.toDomain()
    override suspend fun listTasks(missionId: String) = tasks.listByMission(missionId).map { it.toDomain() }
    override suspend fun insertTask(task: Task) = tasks.insert(task.toEntity())
    override suspend fun updateTask(task: Task) = tasks.update(task.toEntity())

    override suspend fun listDependencies(missionId: String) = deps.listByMission(missionId).map { it.toDomain() }
    override suspend fun insertDependency(dep: TaskDependency) = deps.insert(dep.toEntity())
    override suspend fun deleteDependency(id: String) = deps.deleteById(id)

    override suspend fun saveResult(result: TaskResult) = results.insert(result.toEntity())
    override suspend fun resultsForTask(taskId: String) = results.listByTask(taskId).map { it.toDomain() }
    override suspend fun resultsForMission(missionId: String): List<TaskResult> =
        listTasks(missionId).flatMap { resultsForTask(it.id) }

    override suspend fun appendEvent(event: MissionEvent) = events.insert(event.toEntity())
    override suspend fun events(missionId: String) = events.allChronological(missionId).map { it.toDomain() }

    override suspend fun getAgent(id: String) = agents.getAgent(id)
    override suspend fun listAgents(projectId: String) = agents.getAgentsForProject(projectId)
    override suspend fun listRules(agentId: String) = agents.getAgentRules(agentId)
    override suspend fun listReferences(projectId: String) = references.listByProject(projectId)
    override suspend fun loopGuardCounts(missionId: String): Map<String, Int> =
        loopGuard.list(missionId).associate { it.signature to it.count }
    override suspend fun incrementLoopGuard(missionId: String, signature: String, now: Long): Int =
        db.withTransaction {
            val inserted = loopGuard.insertIfAbsent(MissionLoopGuardEntity(missionId, signature, 1, now))
            if (inserted == -1L) {
                loopGuard.increment(missionId, signature, now)
            }
            loopGuard.list(missionId).first { it.signature == signature }.count
        }
    override suspend fun clearLoopGuard(missionId: String) = loopGuard.clear(missionId)

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
}
