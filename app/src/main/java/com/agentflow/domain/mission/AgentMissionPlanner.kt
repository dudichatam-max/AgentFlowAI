package com.agentflow.domain.mission

import com.agentflow.domain.agent.AgentDuties
import com.agentflow.domain.agent.AgentPromptBuilder
import com.agentflow.domain.agent.toBrain
import com.agentflow.domain.agent.toModelConfig
import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIRequestOptions
import com.agentflow.domain.ai.AIResponseFormat
import com.agentflow.domain.model.Mission
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.validation.DomainException

/**
 * R&D planner. Returns untrusted JSON only. Never mutates Room.
 */
class AgentMissionPlanner(
    private val store: MissionStore,
    private val manager: ProviderManager,
    private val contextFactory: MissionContextFactory? = null,
    private val loadRules: suspend (String) -> List<com.agentflow.domain.model.AgentRule> = { store.listRules(it) },
) : MissionPlanner {

    override suspend fun plan(mission: Mission): String {
        val rd = AgentDuties.orchestrator(store.listAgents(mission.projectId))
            ?: throw DomainException.AgentNotFound("orchestrator")
        val brain = rd.toBrain(loadRules(rd.id))
        val system = AgentPromptBuilder.build(brain) + PLAN_CONTRACT
        val refs = contextFactory?.let {
            val dummy = com.agentflow.domain.model.Task(
                id = "plan",
                missionId = mission.id,
                createdByType = com.agentflow.domain.model.CreatedByType.ENGINE,
                createdById = "engine",
                assignedAgentId = rd.id,
                title = "Plan mission",
                description = mission.description,
                createdAt = mission.createdAt,
                updatedAt = mission.updatedAt,
            )
            it.build(store, mission, dummy, rd, emptyList())
        }.orEmpty()
        val user = buildString {
            appendLine("MISSION TITLE: ${mission.title}")
            appendLine("MISSION DESCRIPTION: ${mission.description}")
            appendLine("AVAILABLE AGENTS:")
            store.listAgents(mission.projectId).forEach { agent ->
                appendLine("- id=${agent.id} role=${agent.role} caps=${agent.capabilities}")
            }
            if (refs.isNotBlank()) {
                appendLine()
                appendLine(refs)
            }
            appendLine()
            appendLine("Propose the initial dynamic task graph as contract JSON only.")
        }
        val model = rd.toModelConfig()
        val primary = model.providerType
        val fallback = model.fallbackProviderType
        val request = AIRequest(
            messages = listOf(AIMessage(AIMessageRole.USER, user)),
            model = model.modelId,
            options = AIRequestOptions(
                temperature = model.temperature,
                maxOutputTokens = model.maxOutputTokens,
            ),
            systemInstruction = system,
            responseFormat = AIResponseFormat.JsonObjectFormat,
            preferredProvider = primary,
        )
        return when (val result = manager.generate(request, primary, listOfNotNull(fallback))) {
            is ProviderResult.Success -> result.value.content
            is ProviderResult.Failure -> throw DomainException.InvalidAction("planner provider: ${result.error.message}")
        }
    }

    companion object {
        const val PLAN_CONTRACT = """

Return ONLY JSON matching AgentDecisionEnvelope version 1.
Allowed action types: CREATE_TASK, ASSIGN_TASK, ADD_DEPENDENCY, REQUEST_USER_INPUT, REQUEST_MORE_RESEARCH, CONTINUE.
Do not invent agent ids. Use only ids listed under AVAILABLE AGENTS.
Do not claim execution authority. You propose; the engine decides.
"""
    }
}
