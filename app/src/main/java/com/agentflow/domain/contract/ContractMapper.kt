package com.agentflow.domain.contract

import com.agentflow.domain.mission.MissionAction
import com.agentflow.domain.model.ArtifactType
import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.Priority

object ContractLimits {
    const val MAX_TITLE = 160
    const val MAX_DESCRIPTION = 8_000
    const val MAX_QUESTION = 1_000
    const val MAX_REASON = 2_000
    const val MAX_CONTENT = 20_000
    const val MAX_ACTIONS = 12
}

object ContractMapper {
    fun toActions(envelope: AgentDecisionEnvelope): List<MissionAction> {
        if (envelope.actions.size > ContractLimits.MAX_ACTIONS) {
            throw ContractException("Too many actions")
        }
        val mapped = envelope.actions.map { mapAction(it) }.toMutableList()
        if (envelope.requiresUserInput && mapped.none { it is MissionAction.RequestUserInput }) {
            val q = envelope.userInputQuestion ?: envelope.message
            mapped += MissionAction.RequestUserInput(q, envelope.reason ?: "user input required")
        }
        envelope.artifacts.forEach { art ->
            mapped += MissionAction.CreateArtifact(
                type = parseArtifact(art.artifactType),
                name = art.name,
                content = art.content,
            )
        }
        when (envelope.status) {
            DecisionStatus.REQUEST_REVIEW ->
                if (mapped.none { it is MissionAction.RequestReview }) mapped += MissionAction.RequestReview
            DecisionStatus.WAITING_FOR_USER -> Unit
            DecisionStatus.FAIL -> Unit
            DecisionStatus.COMPLETE, DecisionStatus.CONTINUE -> Unit
        }
        return mapped
    }

    fun mapAction(dto: ProposedActionDto): MissionAction {
        val type = try {
            ProposedActionType.valueOf(dto.type.trim().uppercase())
        } catch (_: Exception) {
            throw ContractException("Unknown action type ${dto.type}")
        }
        return when (type) {
            ProposedActionType.CREATE_TASK -> MissionAction.CreateTask(
                title = requireBound(dto.title, "title", ContractLimits.MAX_TITLE),
                description = requireBound(dto.description, "description", ContractLimits.MAX_DESCRIPTION),
                assignedAgentId = dto.assignedAgentId?.takeIf { it.isNotBlank() }
                    ?: throw ContractException("assignedAgentId required"),
                priority = parsePriority(dto.priority),
                parentTaskId = dto.parentTaskId,
            )
            ProposedActionType.ASSIGN_TASK -> MissionAction.AssignTask(
                taskId = requireId(dto.taskId, "taskId"),
                agentId = requireId(dto.agentId ?: dto.assignedAgentId, "agentId"),
            )
            ProposedActionType.ADD_DEPENDENCY -> MissionAction.AddDependency(
                taskId = requireId(dto.taskId, "taskId"),
                dependsOnTaskId = requireId(dto.dependsOnTaskId ?: dto.dependsOn.firstOrNull(), "dependsOnTaskId"),
                type = parseDep(dto.dependencyType),
            )
            ProposedActionType.REMOVE_DEPENDENCY -> MissionAction.RemoveDependency(
                dependencyId = dto.dependencyId ?: "${dto.taskId}:${dto.dependsOnTaskId}",
            )
            ProposedActionType.REQUEST_USER_INPUT -> MissionAction.RequestUserInput(
                question = requireBound(dto.question, "question", ContractLimits.MAX_QUESTION),
                reason = requireBound(dto.reason, "reason", ContractLimits.MAX_REASON),
                taskId = dto.taskId,
            )
            ProposedActionType.COMPLETE_TASK -> MissionAction.CompleteTask(
                taskId = requireId(dto.taskId, "taskId"),
                content = requireBound(dto.summary ?: dto.content, "summary", ContractLimits.MAX_CONTENT),
                confidence = dto.confidence,
            )
            ProposedActionType.FAIL_TASK -> MissionAction.FailTask(
                taskId = requireId(dto.taskId, "taskId"),
                reason = requireBound(dto.reason, "reason", ContractLimits.MAX_REASON),
            )
            ProposedActionType.RETRY_TASK -> MissionAction.RetryTask(requireId(dto.taskId, "taskId"))
            ProposedActionType.REQUEST_MORE_RESEARCH -> MissionAction.RequestMoreResearch
            ProposedActionType.REQUEST_SYNTHESIS -> MissionAction.RequestSynthesis
            ProposedActionType.REQUEST_REVIEW -> MissionAction.RequestReview
            ProposedActionType.CREATE_ARTIFACT -> MissionAction.CreateArtifact(
                type = parseArtifact(dto.artifactType),
                name = requireBound(dto.name, "name", ContractLimits.MAX_TITLE),
                content = requireBound(dto.content, "content", ContractLimits.MAX_CONTENT),
            )
            ProposedActionType.CONTINUE -> MissionAction.Continue
        }
    }

    private fun requireId(value: String?, field: String): String =
        value?.takeIf { it.isNotBlank() } ?: throw ContractException("$field required")

    private fun requireBound(value: String?, field: String, max: Int): String {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) throw ContractException("$field required")
        if (text.length > max) throw ContractException("$field exceeds $max")
        return text
    }

    private fun parsePriority(raw: String?): Priority = try {
        Priority.valueOf((raw ?: "NORMAL").uppercase())
    } catch (_: Exception) {
        throw ContractException("Invalid priority")
    }

    private fun parseDep(raw: String?): DependencyType = try {
        DependencyType.valueOf((raw ?: "REQUIRED").uppercase())
    } catch (_: Exception) {
        throw ContractException("Invalid dependencyType")
    }

    private fun parseArtifact(raw: String?): ArtifactType = try {
        ArtifactType.valueOf((raw ?: "").uppercase())
    } catch (_: Exception) {
        throw ContractException("Invalid artifactType")
    }
}
