package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule

/**
 * Structured brain. Not a single editable mega-prompt.
 * Rules are behavioral instructions, not security permissions.
 */
data class AgentBrain(
    val agentId: String,
    val name: String,
    val role: String,
    val description: String,
    val rules: List<AgentRule>,
    val config: AgentBrainConfig,
    val model: AgentModelConfig,
    val capabilities: Set<AgentCapability>,
) {
    val enabledRules: List<AgentRule> get() = rules.enabledSorted()
}

fun List<AgentRule>.enabledSorted(): List<AgentRule> =
    filter { it.enabled }.sortedForBrain()

fun List<AgentRule>.sortedForBrain(): List<AgentRule> =
    sortedWith(
        compareByDescending<AgentRule> { it.priority.rank() }
            .thenBy { it.createdAt }
            .thenBy { it.id },
    )

fun Agent.toModelConfig(): AgentModelConfig = AgentModelConfig(
    provider = providerId,
    modelId = modelId,
    fallbackProvider = fallbackProviderId,
    fallbackModelId = fallbackModelId,
    temperature = temperature,
    maxOutputTokens = maxOutputTokens,
    reasoningLevel = reasoningLevelEnum,
    freeOnly = freeOnly,
)

fun Agent.toBrainConfig(): AgentBrainConfig = AgentBrainConfig(
    role = role,
    outputStyle = outputStyle,
    reasoningLevel = reasoningLevelEnum,
    verbosity = verbosity,
    exposeUncertainty = exposeUncertainty,
    includeAssumptions = includeAssumptions,
    includeAlternatives = includeAlternatives,
)

fun Agent.toBrain(rules: List<AgentRule>): AgentBrain = AgentBrain(
    agentId = id,
    name = name,
    role = role,
    description = description,
    rules = rules.sortedForBrain(),
    config = toBrainConfig(),
    model = toModelConfig(),
    capabilities = capabilities,
)
