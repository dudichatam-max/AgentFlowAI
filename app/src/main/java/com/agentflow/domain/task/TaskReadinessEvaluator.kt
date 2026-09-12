package com.agentflow.domain.task

import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskStatus

object TaskReadinessEvaluator {
    fun evaluate(task: Task, graph: TaskGraph, missionStatus: MissionStatus): TaskStatus {
        if (task.status in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.SKIPPED)) return task.status
        if (missionStatus != MissionStatus.EXECUTING && missionStatus != MissionStatus.RESEARCHING) {
            return if (task.status == TaskStatus.RUNNING) task.status else task.status
        }
        val deps = graph.dependencies.filter { it.taskId == task.id }
        val required = deps.filter { it.type == DependencyType.REQUIRED }
        required.forEach { dep ->
            val parent = graph.task(dep.dependsOnTaskId) ?: return TaskStatus.BLOCKED
            when (parent.status) {
                TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.SKIPPED -> return TaskStatus.BLOCKED
                TaskStatus.COMPLETED -> Unit
                else -> return TaskStatus.WAITING_FOR_DEPENDENCY
            }
        }
        if (task.assignedAgentId.isBlank()) return TaskStatus.BLOCKED
        return TaskStatus.READY
    }

    fun readyTasks(graph: TaskGraph, missionStatus: MissionStatus): List<Task> =
        graph.tasks.filter {
            evaluate(it, graph, missionStatus) == TaskStatus.READY &&
                it.status !in setOf(TaskStatus.RUNNING, TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.SKIPPED)
        }
}
