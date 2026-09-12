package com.agentflow.domain
import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.validation.DependencyGraph
import com.agentflow.domain.validation.DomainException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyGraphTest {
    private fun dep(task: String, on: String) = TaskDependency(
        id = "$task-$on", missionId = "m1", taskId = task, dependsOnTaskId = on,
        type = DependencyType.REQUIRED, createdAt = 1L,
    )
    @Test fun detectsSimpleCycle() {
        assertTrue(DependencyGraph.hasCycle(listOf(dep("B", "A"), dep("A", "B"))))
    }
    @Test fun acyclicDiamondIsValid() {
        val deps = listOf(dep("C", "A"), dep("C", "B"))
        assertFalse(DependencyGraph.hasCycle(deps))
        DependencyGraph.validateNew(deps.take(1), deps[1], setOf("A", "B", "C"))
    }
    @Test(expected = DomainException.InvalidDependency::class)
    fun rejectsSelfDependency() {
        DependencyGraph.validateNew(emptyList(), dep("A", "A"), setOf("A"))
    }
    @Test(expected = DomainException.CycleDetected::class)
    fun rejectsCycleOnInsert() {
        val existing = listOf(dep("B", "A"), dep("C", "B"))
        DependencyGraph.validateNew(existing, dep("A", "C"), setOf("A", "B", "C"))
    }
    @Test fun readyWhenDependenciesCompleted() {
        val ready = DependencyGraph.readyTaskIds(
            taskIds = setOf("A", "B", "C"), completedIds = setOf("A", "B"),
            deps = listOf(dep("C", "A"), dep("C", "B")),
        )
        assertTrue(ready.contains("C"))
        assertTrue(ready.contains("A"))
    }
}
