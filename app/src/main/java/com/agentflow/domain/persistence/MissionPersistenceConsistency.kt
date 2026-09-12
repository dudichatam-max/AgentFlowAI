package com.agentflow.domain.persistence

import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionArtifact
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskResult
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.reference.ContentHasher

object MissionPersistenceConsistency {
    fun check(
        mission: Mission,
        tasks: List<Task>,
        results: List<TaskResult>,
        events: List<MissionEvent>,
        artifacts: List<Pair<MissionArtifact, String?>>,
    ): PersistenceReport {
        val errors = linkedSetOf<String>()
        val recoverable = linkedSetOf<String>()
        val taskIds = tasks.map { it.id }.toSet()
        val resultByTask = results.groupBy { it.taskId }
        tasks.filter { it.missionId != mission.id }.forEach {
            errors += "task ${it.id} belongs to ${it.missionId}, expected ${mission.id}"
        }
        results.filter { it.missionId != mission.id }.forEach {
            errors += "result ${it.id} belongs to ${it.missionId}, expected ${mission.id}"
        }
        results.filter { it.taskId !in taskIds }.forEach {
            errors += "result ${it.id} references missing task ${it.taskId}"
        }
        tasks.forEach { task ->
            val hasResult = !resultByTask[task.id].isNullOrEmpty()
            when {
                task.status == TaskStatus.COMPLETED && !hasResult -> errors += "completed task ${task.id} has no result"
                task.status == TaskStatus.RUNNING && hasResult -> recoverable += task.id
            }
        }
        events.forEach { event ->
            event.relatedTaskId?.let { taskId ->
                if (taskId !in taskIds) errors += "event ${event.id} references missing task $taskId"
            }
        }
        artifacts.forEach { (artifact, content) ->
            if (artifact.missionId != mission.id) errors += "artifact ${artifact.id} belongs to ${artifact.missionId}, expected ${mission.id}"
            else if (content == null) errors += "artifact ${artifact.id} content is missing"
            else if (artifact.contentHash != null && ContentHasher.sha256(content) != artifact.contentHash) errors += "artifact ${artifact.id} content hash mismatch"
        }
        val critical = errors.filterNot { it.startsWith("event ") }
        return PersistenceReport(errors.toList(), critical, recoverable.toList())
    }
}

data class PersistenceReport(val errors: List<String>, val criticalErrors: List<String>, val recoverableTaskIds: List<String>)
