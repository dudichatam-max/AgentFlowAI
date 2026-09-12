package com.agentflow.domain.task

import com.agentflow.domain.model.CreatedByType
import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskReadinessEvaluatorTest {
    private fun task(id: String, status: TaskStatus = TaskStatus.PENDING) = Task(
        id, "m", null, CreatedByType.ENGINE, "e", "ag", id, "d", status, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun optionalDoesNotBlock() {
        val a = task("a", TaskStatus.PENDING)
        val b = task("b")
        val graph = TaskGraph(
            listOf(a, b),
            listOf(TaskDependency("d", "m", "b", "a", DependencyType.OPTIONAL, 1)),
        )
        assertThat(TaskReadinessEvaluator.evaluate(b, graph, MissionStatus.EXECUTING)).isEqualTo(TaskStatus.READY)
    }

    @Test
    fun requiredIncompleteWaits() {
        val a = task("a", TaskStatus.RUNNING)
        val b = task("b")
        val graph = TaskGraph(listOf(a, b), listOf(TaskDependency("d", "m", "b", "a", DependencyType.REQUIRED, 1)))
        assertThat(TaskReadinessEvaluator.evaluate(b, graph, MissionStatus.EXECUTING)).isEqualTo(TaskStatus.WAITING_FOR_DEPENDENCY)
    }

    @Test
    fun requiredFailedBlocks() {
        val a = task("a", TaskStatus.FAILED)
        val b = task("b")
        val graph = TaskGraph(listOf(a, b), listOf(TaskDependency("d", "m", "b", "a", DependencyType.REQUIRED, 1)))
        assertThat(TaskReadinessEvaluator.evaluate(b, graph, MissionStatus.EXECUTING)).isEqualTo(TaskStatus.BLOCKED)
    }
}
