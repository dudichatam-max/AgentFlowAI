package com.agentflow.domain.task

import com.agentflow.domain.mission.MissionExecutionPolicy
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.validation.DependencyGraph
import com.agentflow.domain.validation.DomainException

object TaskGraphValidator {
    fun validateNewDependency(
        graph: TaskGraph,
        candidate: TaskDependency,
        policy: MissionExecutionPolicy = MissionExecutionPolicy(),
    ) {
        if (candidate.missionId != graph.tasks.firstOrNull { it.id == candidate.taskId }?.missionId &&
            graph.tasks.any { it.id == candidate.taskId }
        ) {
            throw DomainException.InvalidDependency("cross-mission mismatch")
        }
        val known = graph.tasks.map { it.id }.toSet()
        DependencyGraph.validateNew(graph.dependencies, candidate, known)
        val next = graph.copy(dependencies = graph.dependencies + candidate)
        if (next.depth() > policy.maxGraphDepth) {
            throw DomainException.GraphDepthExceeded("depth ${next.depth()} > ${policy.maxGraphDepth}")
        }
    }

    fun validateTaskCount(tasks: List<Task>, policy: MissionExecutionPolicy) {
        if (tasks.size >= policy.maxTasksPerMission) {
            throw DomainException.TaskLimitExceeded("${tasks.size} >= ${policy.maxTasksPerMission}")
        }
    }

    fun validateSameMission(task: Task, missionId: String) {
        if (task.missionId != missionId) throw DomainException.InvalidAction("task ${task.id} not in mission")
    }
}
