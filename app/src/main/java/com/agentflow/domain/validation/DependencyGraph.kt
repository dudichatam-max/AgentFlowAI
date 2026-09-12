package com.agentflow.domain.validation
import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.TaskDependency

object DependencyGraph {
    fun validateNew(existing: List<TaskDependency>, candidate: TaskDependency, knownTaskIds: Set<String>) {
        if (candidate.taskId == candidate.dependsOnTaskId) {
            throw DomainException.InvalidDependency("self-dependency ${candidate.taskId}")
        }
        if (candidate.taskId !in knownTaskIds) throw DomainException.InvalidDependency("unknown task ${candidate.taskId}")
        if (candidate.dependsOnTaskId !in knownTaskIds) throw DomainException.InvalidDependency("unknown dependsOn ${candidate.dependsOnTaskId}")
        if (existing.any { it.taskId == candidate.taskId && it.dependsOnTaskId == candidate.dependsOnTaskId }) {
            throw DomainException.InvalidDependency("duplicate ${candidate.taskId} ← ${candidate.dependsOnTaskId}")
        }
        if (hasCycle(existing + candidate)) {
            throw DomainException.CycleDetected("${candidate.taskId} ← ${candidate.dependsOnTaskId}")
        }
    }

    fun hasCycle(deps: List<TaskDependency>): Boolean {
        val adj = mutableMapOf<String, MutableList<String>>()
        deps.forEach { adj.getOrPut(it.dependsOnTaskId) { mutableListOf() }.add(it.taskId) }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun dfs(node: String): Boolean {
            if (node in visiting) return true
            if (node in visited) return false
            visiting += node
            adj[node].orEmpty().forEach { if (dfs(it)) return true }
            visiting -= node
            visited += node
            return false
        }
        return deps.flatMap { listOf(it.taskId, it.dependsOnTaskId) }.toSet().any { dfs(it) }
    }

    fun readyTaskIds(taskIds: Set<String>, completedIds: Set<String>, deps: List<TaskDependency>): Set<String> {
        return taskIds.filter { id ->
            deps.filter { it.taskId == id && it.type == DependencyType.REQUIRED }
                .all { it.dependsOnTaskId in completedIds }
        }.toSet()
    }
}
