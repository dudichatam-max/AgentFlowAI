package com.agentflow.domain.mission

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskResult

class InMemoryMissionStore : MissionStore {
    val missions = linkedMapOf<String, Mission>()
    val tasks = linkedMapOf<String, Task>()
    val deps = linkedMapOf<String, TaskDependency>()
    val results = mutableListOf<TaskResult>()
    val events = mutableListOf<MissionEvent>()
    val agents = linkedMapOf<String, Agent>()
    val rules = mutableListOf<AgentRule>()
    val references = mutableListOf<Reference>()
    val loopGuard = mutableMapOf<Pair<String, String>, Int>()

    override suspend fun getMission(id: String) = missions[id]
    override suspend fun insertMission(mission: Mission) {
        missions[mission.id] = mission
    }
    override suspend fun updateMission(mission: Mission) {
        missions[mission.id] = mission
    }
    override suspend fun listActiveMissions() =
        missions.values.filter { it.status !in setOf(MissionStatus.APPROVED, MissionStatus.FAILED, MissionStatus.CANCELLED) }

    override suspend fun getTask(id: String) = tasks[id]
    override suspend fun listTasks(missionId: String) = tasks.values.filter { it.missionId == missionId }
    override suspend fun insertTask(task: Task) {
        tasks[task.id] = task
    }
    override suspend fun updateTask(task: Task) {
        tasks[task.id] = task
    }

    override suspend fun listDependencies(missionId: String) = deps.values.filter { it.missionId == missionId }
    override suspend fun insertDependency(dep: TaskDependency) {
        deps[dep.id] = dep
    }
    override suspend fun deleteDependency(id: String) {
        deps.remove(id)
    }

    override suspend fun saveResult(result: TaskResult) {
        results += result
    }
    override suspend fun resultsForTask(taskId: String) = results.filter { it.taskId == taskId }
    override suspend fun resultsForMission(missionId: String) = results.filter { it.missionId == missionId }

    override suspend fun appendEvent(event: MissionEvent) {
        events += event
    }
    override suspend fun events(missionId: String) = events.filter { it.missionId == missionId }

    override suspend fun getAgent(id: String) = agents[id]
    override suspend fun listAgents(projectId: String) = agents.values.filter { it.projectId == projectId }
    override suspend fun listRules(agentId: String) = rules.filter { it.agentId == agentId }
    override suspend fun listReferences(projectId: String) = references.filter { it.projectId == projectId }
    override suspend fun loopGuardCounts(missionId: String): Map<String, Int> =
        loopGuard.filterKeys { it.first == missionId }.mapKeys { it.key.second }
    override suspend fun incrementLoopGuard(missionId: String, signature: String, now: Long): Int {
        val key = missionId to signature
        val next = (loopGuard[key] ?: 0) + 1
        loopGuard[key] = next
        return next
    }
    override suspend fun clearLoopGuard(missionId: String) {
        loopGuard.keys.removeAll { it.first == missionId }
    }

    override suspend fun <T> transaction(block: suspend () -> T): T {
        val snapMissions = LinkedHashMap(missions)
        val snapTasks = LinkedHashMap(tasks)
        val snapDeps = LinkedHashMap(deps)
        val snapResults = results.toList()
        val snapEvents = events.toList()
        val snapLoopGuard = LinkedHashMap(loopGuard)
        return try {
            block()
        } catch (t: Throwable) {
            missions.clear(); missions.putAll(snapMissions)
            tasks.clear(); tasks.putAll(snapTasks)
            deps.clear(); deps.putAll(snapDeps)
            results.clear(); results.addAll(snapResults)
            events.clear(); events.addAll(snapEvents)
            loopGuard.clear(); loopGuard.putAll(snapLoopGuard)
            throw t
        }
    }
}
