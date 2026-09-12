package com.agentflow.domain.mission

import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.Priority

sealed interface MissionAction {
    data class CreateTask(
        val title: String,
        val description: String,
        val assignedAgentId: String,
        val priority: Priority = Priority.NORMAL,
        val parentTaskId: String? = null,
        val isSynthesis: Boolean = false,
    ) : MissionAction

    data class AssignTask(val taskId: String, val agentId: String) : MissionAction

    data class AddDependency(
        val taskId: String,
        val dependsOnTaskId: String,
        val type: DependencyType = DependencyType.REQUIRED,
    ) : MissionAction

    data class RemoveDependency(val dependencyId: String) : MissionAction

    data class RequestUserInput(val question: String, val reason: String, val taskId: String? = null) : MissionAction

    data class CompleteTask(val taskId: String, val content: String, val confidence: Double? = 0.8) : MissionAction

    data class FailTask(val taskId: String, val reason: String) : MissionAction

    data class RetryTask(val taskId: String) : MissionAction

    data object RequestMoreResearch : MissionAction

    data object RequestSynthesis : MissionAction

    data object RequestReview : MissionAction

    data object PauseMission : MissionAction

    data object ResumeMission : MissionAction

    data object CancelMission : MissionAction

    data object Continue : MissionAction

    data class CreateArtifact(
        val type: com.agentflow.domain.model.ArtifactType,
        val name: String,
        val content: String,
    ) : MissionAction
}

data class MissionActionResult(
    val accepted: Boolean,
    val message: String,
    val createdTaskId: String? = null,
)
