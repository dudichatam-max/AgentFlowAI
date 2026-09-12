package com.agentflow.domain.persistence

import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionArtifact
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskResult
import com.agentflow.domain.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MissionPersistenceConsistencyTest {
    private val mission = Mission("m1", "p1", "title", "description", MissionStatus.EXECUTING, 1, 2, createdBy = "user")
    private val task = Task("t1", "m1", null, com.agentflow.domain.model.CreatedByType.USER, "user", "a1", "task", "desc", TaskStatus.COMPLETED, com.agentflow.domain.model.Priority.NORMAL, null, null, 3, 0, 1, 2)

    @Test
    fun completedTaskWithoutResultIsInconsistent() {
        val report = MissionPersistenceConsistency.check(
            mission,
            listOf(task),
            emptyList(),
            emptyList(),
            emptyList(),
        )

        assertThat(report.errors).contains("completed task t1 has no result")
    }

    @Test
    fun resultForRunningTaskIsRecoverableNotAnError() {
        val running = task.copy(status = TaskStatus.RUNNING)
        val result = TaskResult("r1", "t1", "m1", "done", createdAt = 4)
        val report = MissionPersistenceConsistency.check(
            mission,
            listOf(running),
            listOf(result),
            emptyList(),
            emptyList(),
        )

        assertThat(report.recoverableTaskIds).containsExactly("t1")
        assertThat(report.errors).isEmpty()
    }

    @Test
    fun eventReferencingMissingTaskIsAnError() {
        val event = MissionEvent("e1", "m1", MissionEventType.TASK_COMPLETED, "missing", "missing-task", null, 5)
        val report = MissionPersistenceConsistency.check(
            mission,
            emptyList(),
            emptyList(),
            listOf(event),
            emptyList(),
        )

        assertThat(report.errors).contains("event e1 references missing task missing-task")
        assertThat(report.criticalErrors).isEmpty()
    }

    @Test
    fun artifactWithMismatchedContentHashIsAnError() {
        val artifact = MissionArtifact("a1", "m1", com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN, "plan", null, "expected", 3, "text/markdown", 1, 1, 2)
        val report = MissionPersistenceConsistency.check(
            mission,
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(artifact to "actual"),
        )

        assertThat(report.errors).contains("artifact a1 content hash mismatch")
    }
    @Test
    fun missionStatusAndEventRollbackTogetherOnTransactionFailure() = kotlinx.coroutines.test.runTest {
        val base = com.agentflow.domain.mission.InMemoryMissionStore()
        base.missions["m1"] = mission
        val failing = object : com.agentflow.domain.mission.MissionStore by base {
            override suspend fun <T> transaction(block: suspend () -> T): T =
                base.transaction {
                    block()
                    throw IllegalStateException("simulated crash")
                }
        }

        val updated = mission.copy(status = MissionStatus.FAILED, updatedAt = 9)
        val event = com.agentflow.domain.mission.event("m1", MissionEventType.MISSION_FAILED, "failure", now = 9)
        kotlin.runCatching { failing.persistMissionStatus(updated, event) }

        assertThat(base.missions["m1"]).isEqualTo(mission)
        assertThat(base.events).isEmpty()
    }

    @Test
    fun taskCompletionRollsBackResultTaskAndEventTogetherOnTransactionFailure() = kotlinx.coroutines.test.runTest {
        val base = com.agentflow.domain.mission.InMemoryMissionStore()
        val running = task.copy(status = TaskStatus.RUNNING)
        base.tasks[running.id] = running
        val failing = object : com.agentflow.domain.mission.MissionStore by base {
            override suspend fun <T> transaction(block: suspend () -> T): T =
                base.transaction {
                    block()
                    throw IllegalStateException("simulated crash")
                }
        }
        val result = TaskResult("r1", "t1", "m1", "done", createdAt = 4)
        val event = com.agentflow.domain.mission.event("m1", MissionEventType.TASK_COMPLETED, "task", "t1", 4)
        kotlin.runCatching { failing.persistTaskCompletion(running.copy(status = TaskStatus.COMPLETED), result, event) }

        assertThat(base.tasks["t1"]).isEqualTo(running)
        assertThat(base.results).isEmpty()
        assertThat(base.events).isEmpty()
    }

}
