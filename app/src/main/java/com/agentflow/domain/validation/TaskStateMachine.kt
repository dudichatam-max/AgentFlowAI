package com.agentflow.domain.validation
import com.agentflow.domain.model.TaskStatus

object TaskStateMachine {
    private val allowed: Map<TaskStatus, Set<TaskStatus>> = mapOf(
        TaskStatus.PENDING to setOf(TaskStatus.READY, TaskStatus.WAITING_FOR_DEPENDENCY, TaskStatus.WAITING_FOR_USER, TaskStatus.CANCELLED, TaskStatus.SKIPPED),
        TaskStatus.READY to setOf(TaskStatus.RUNNING, TaskStatus.COMPLETED, TaskStatus.WAITING_FOR_DEPENDENCY, TaskStatus.WAITING_FOR_USER, TaskStatus.BLOCKED, TaskStatus.CANCELLED, TaskStatus.SKIPPED),
        TaskStatus.WAITING_FOR_DEPENDENCY to setOf(TaskStatus.READY, TaskStatus.BLOCKED, TaskStatus.CANCELLED, TaskStatus.SKIPPED),
        TaskStatus.RUNNING to setOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.RETRYING, TaskStatus.WAITING_FOR_USER, TaskStatus.WAITING_FOR_AGENT, TaskStatus.BLOCKED, TaskStatus.READY, TaskStatus.CANCELLED),
        TaskStatus.WAITING_FOR_USER to setOf(TaskStatus.READY, TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.BLOCKED),
        TaskStatus.WAITING_FOR_AGENT to setOf(TaskStatus.READY, TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.BLOCKED),
        TaskStatus.RETRYING to setOf(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED),
        TaskStatus.BLOCKED to setOf(TaskStatus.READY, TaskStatus.CANCELLED, TaskStatus.SKIPPED, TaskStatus.FAILED),
        TaskStatus.COMPLETED to setOf(TaskStatus.READY),
        TaskStatus.FAILED to setOf(TaskStatus.RETRYING, TaskStatus.READY, TaskStatus.CANCELLED),
        TaskStatus.CANCELLED to emptySet(),
        TaskStatus.SKIPPED to emptySet(),
    )
    fun canTransition(from: TaskStatus, to: TaskStatus): Boolean =
        from == to || allowed[from].orEmpty().contains(to)
    fun transition(from: TaskStatus, to: TaskStatus): TaskStatus {
        if (!canTransition(from, to)) throw DomainException.InvalidTransition(from, to)
        return to
    }
    fun isTerminal(status: TaskStatus): Boolean =
        status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED || status == TaskStatus.SKIPPED
}
