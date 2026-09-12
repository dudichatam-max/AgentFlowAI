package com.agentflow.domain.mission

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskResult

interface MissionStore {
    suspend fun getMission(id: String): Mission?
    suspend fun insertMission(mission: Mission)
    suspend fun updateMission(mission: Mission)
    suspend fun listActiveMissions(): List<Mission>

    suspend fun getTask(id: String): Task?
    suspend fun listTasks(missionId: String): List<Task>
    suspend fun insertTask(task: Task)
    suspend fun updateTask(task: Task)

    suspend fun listDependencies(missionId: String): List<TaskDependency>
    suspend fun insertDependency(dep: TaskDependency)
    suspend fun deleteDependency(id: String)

    suspend fun saveResult(result: TaskResult)

    suspend fun persistTaskCompletion(task: Task, result: TaskResult, completionEvent: MissionEvent) = transaction {
        saveResult(result)
        updateTask(task)
        appendEvent(completionEvent)
    }

    suspend fun persistTaskEvent(task: Task, taskEvent: MissionEvent) = transaction {
        updateTask(task)
        appendEvent(taskEvent)
    }

    suspend fun persistMissionStatus(mission: Mission, completionEvent: MissionEvent) = transaction {
        updateMission(mission)
        appendEvent(completionEvent)
    }
    suspend fun resultsForTask(taskId: String): List<TaskResult>
    suspend fun resultsForMission(missionId: String): List<TaskResult>

    suspend fun appendEvent(event: MissionEvent)
    suspend fun events(missionId: String): List<MissionEvent>

    suspend fun getAgent(id: String): Agent?
    suspend fun listAgents(projectId: String): List<Agent>
    suspend fun listRules(agentId: String): List<AgentRule>
    suspend fun listReferences(projectId: String): List<Reference>

    suspend fun loopGuardCounts(missionId: String): Map<String, Int>
    suspend fun incrementLoopGuard(missionId: String, signature: String, now: Long = System.currentTimeMillis()): Int
    suspend fun clearLoopGuard(missionId: String)

    suspend fun <T> transaction(block: suspend () -> T): T
}

fun event(
    missionId: String,
    type: MissionEventType,
    message: String,
    taskId: String? = null,
    now: Long = System.currentTimeMillis(),
) = MissionEvent(
    id = com.agentflow.domain.model.Ids.new(),
    missionId = missionId,
    type = type,
    message = message,
    relatedTaskId = taskId,
    createdAt = now,
)
