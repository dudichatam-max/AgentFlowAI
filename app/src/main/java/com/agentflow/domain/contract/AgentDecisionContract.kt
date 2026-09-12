package com.agentflow.domain.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CURRENT_CONTRACT_VERSION = 1

@Serializable
enum class DecisionStatus {
    CONTINUE,
    WAITING_FOR_USER,
    COMPLETE,
    FAIL,
    REQUEST_REVIEW,
}

@Serializable
data class AgentDecisionEnvelope(
    val version: Int,
    val status: DecisionStatus,
    val message: String,
    val confidence: Double? = null,
    val reason: String? = null,
    val requiresUserInput: Boolean = false,
    val userInputQuestion: String? = null,
    val actions: List<ProposedActionDto> = emptyList(),
    val artifacts: List<ProposedArtifactDto> = emptyList(),
)

@Serializable
data class ProposedActionDto(
    val type: String,
    val taskId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val assignedAgentId: String? = null,
    val agentId: String? = null,
    val priority: String? = null,
    val parentTaskId: String? = null,
    val dependsOn: List<String> = emptyList(),
    val dependsOnTaskId: String? = null,
    val dependencyType: String? = null,
    val dependencyId: String? = null,
    val question: String? = null,
    val reason: String? = null,
    val summary: String? = null,
    val content: String? = null,
    val resultType: String? = null,
    val confidence: Double? = null,
    val artifactType: String? = null,
    val name: String? = null,
)

@Serializable
data class ProposedArtifactDto(
    val artifactType: String,
    val name: String,
    val content: String,
    val version: Int = 1,
)

enum class ProposedActionType {
    CREATE_TASK,
    ASSIGN_TASK,
    ADD_DEPENDENCY,
    REMOVE_DEPENDENCY,
    REQUEST_USER_INPUT,
    COMPLETE_TASK,
    FAIL_TASK,
    RETRY_TASK,
    REQUEST_MORE_RESEARCH,
    REQUEST_SYNTHESIS,
    REQUEST_REVIEW,
    CREATE_ARTIFACT,
    CONTINUE,
}
