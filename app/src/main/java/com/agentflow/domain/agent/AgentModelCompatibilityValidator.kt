package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.provider.CatalogModel
import com.agentflow.domain.provider.ProviderModel
import com.agentflow.domain.provider.ProviderPolicy
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.provider.toProviderType

data class CompatibilityResult(
    val accepted: Boolean,
    val reason: String,
)

object AgentModelCompatibilityValidator {

    fun evaluate(
        agent: Agent,
        model: CatalogModel,
        freeOnly: Boolean = agent.freeOnly,
    ): CompatibilityResult {
        if (model.provider != agent.providerId.toProviderType() && model.provider != ProviderType.valueOf(agent.providerId.name.let {
            if (it == "OPENROUTER") "OPEN_ROUTER" else it
        })) {
            // still allow if caller is switching provider together with model
        }
        val policyError = ProviderPolicy.validateModel(
            ProviderModel(
                id = model.id,
                displayName = model.displayName,
                provider = model.provider,
                isFree = model.isFree,
            ),
            freeOnly = freeOnly,
        )
        if (policyError != null) {
            return CompatibilityResult(false, policyError.message)
        }
        if (AgentCapability.VISION in agent.capabilities && !model.vision) {
            return CompatibilityResult(false, "Agent requires VISION; ${model.id} does not support vision")
        }
        if (AgentCapability.STRUCTURED_OUTPUT in agent.capabilities && !model.structuredOutput) {
            return CompatibilityResult(false, "Agent requires STRUCTURED_OUTPUT; ${model.id} does not advertise structured output")
        }
        if (AgentCapability.REASONING in agent.capabilities && !model.reasoning) {
            return CompatibilityResult(false, "Agent requires REASONING; ${model.id} does not advertise reasoning")
        }
        if (AgentCapability.CODE in agent.capabilities && !model.code) {
            return CompatibilityResult(false, "Agent requires CODE; ${model.id} is not marked code-capable")
        }
        if (AgentCapability.TOOLS in agent.capabilities && !model.tools) {
            return CompatibilityResult(false, "Agent requires TOOLS; ${model.id} does not advertise tools")
        }
        return CompatibilityResult(true, "compatible")
    }

    fun allowedModels(
        agent: Agent,
        catalog: List<CatalogModel>,
        provider: ProviderType,
        freeOnly: Boolean = agent.freeOnly,
        includeIncompatible: Boolean = false,
    ): List<Pair<CatalogModel, CompatibilityResult>> {
        return catalog.filter { it.provider == provider }.map { model ->
            model to evaluate(agent.copy(providerId = when (provider) {
                ProviderType.GEMINI -> com.agentflow.domain.model.ProviderId.GEMINI
                ProviderType.GROQ -> com.agentflow.domain.model.ProviderId.GROQ
                ProviderType.OPEN_ROUTER -> com.agentflow.domain.model.ProviderId.OPENROUTER
            }, freeOnly = freeOnly), model, freeOnly)
        }.filter { includeIncompatible || it.second.accepted }
    }
}
