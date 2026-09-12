package com.agentflow.domain
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.validation.DomainException
import com.agentflow.domain.validation.TaskStateMachine
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateMachineTest {
    @Test fun happyPath() {
        assertTrue(TaskStateMachine.canTransition(TaskStatus.PENDING, TaskStatus.READY))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.READY, TaskStatus.RUNNING))
        assertTrue(TaskStateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.COMPLETED))
    }
    @Test fun completedCanReopenForRevision() {
        assertTrue(TaskStateMachine.canTransition(TaskStatus.COMPLETED, TaskStatus.READY))
    }
    @Test(expected = DomainException.InvalidTransition::class)
    fun pendingCannotJumpToCompleted() {
        TaskStateMachine.transition(TaskStatus.PENDING, TaskStatus.COMPLETED)
    }
}
