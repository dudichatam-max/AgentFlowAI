package com.agentflow.domain.task

import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency

data class TaskGraph(
    val tasks: List<Task>,
    val dependencies: List<TaskDependency>,
) {
    fun task(id: String): Task? = tasks.firstOrNull { it.id == id }

    fun requiredDependenciesOf(taskId: String): List<TaskDependency> =
        dependencies.filter { it.taskId == taskId && it.type == com.agentflow.domain.model.DependencyType.REQUIRED }

    fun optionalDependenciesOf(taskId: String): List<TaskDependency> =
        dependencies.filter { it.taskId == taskId && it.type == com.agentflow.domain.model.DependencyType.OPTIONAL }

    fun depth(): Int {
        val adj = mutableMapOf<String, MutableList<String>>()
        dependencies.forEach { adj.getOrPut(it.dependsOnTaskId) { mutableListOf() }.add(it.taskId) }
        val memo = mutableMapOf<String, Int>()
        fun walk(id: String, stack: Set<String>): Int {
            if (id in stack) return Int.MAX_VALUE / 4
            memo[id]?.let { return it }
            val next = adj[id].orEmpty()
            val d = if (next.isEmpty()) 1 else 1 + next.maxOf { walk(it, stack + id) }
            memo[id] = d
            return d
        }
        return tasks.maxOfOrNull { walk(it.id, emptySet()) } ?: 0
    }
}
