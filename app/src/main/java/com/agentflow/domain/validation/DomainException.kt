package com.agentflow.domain.validation
sealed class DomainException(message: String) : IllegalStateException(message) {
    class InvalidTransition(from: Any, to: Any) : DomainException("Illegal transition $from → $to")
    class CycleDetected(detail: String) : DomainException("Cycle detected: $detail")
    class InvalidDependency(detail: String) : DomainException("Invalid dependency: $detail")
    class MissionNotCompletable(detail: String) : DomainException("Mission cannot complete: $detail")
    class PolicyViolation(detail: String) : DomainException("Policy violation: $detail")
    class MissionNotFound(id: String) : DomainException("Mission not found: $id")
    class TaskNotFound(id: String) : DomainException("Task not found: $id")
    class AgentNotFound(id: String) : DomainException("Agent not found: $id")
    class AgentNotInProject(detail: String) : DomainException("Agent not in project: $detail")
    class TaskLimitExceeded(detail: String) : DomainException("Task limit exceeded: $detail")
    class GraphDepthExceeded(detail: String) : DomainException("Graph depth exceeded: $detail")
    class MissionCancelled(id: String) : DomainException("Mission cancelled: $id")
    class MissionPaused(id: String) : DomainException("Mission paused: $id")
    class UserInputRequired(detail: String) : DomainException("User input required: $detail")
    class InvalidAction(detail: String) : DomainException("Invalid action: $detail")
}
