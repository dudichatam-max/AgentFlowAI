package com.agentflow.domain.model

import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.agent.OutputStyle
import com.agentflow.domain.agent.ReasoningLevel
import com.agentflow.domain.agent.Verbosity

data class Agent(
    val id: String,
    val projectId: String,
    val name: String,
    val role: String,
    val description: String,
    val providerId: ProviderId,
    val modelId: String,
    val fallbackProviderId: ProviderId? = null,
    val fallbackModelId: String? = null,
    val temperature: Double = 0.4,
    val maxOutputTokens: Int = 4096,
    val reasoningLevelEnum: ReasoningLevel = ReasoningLevel.MEDIUM,
    val freeOnly: Boolean = true,
    val status: AgentStatus = AgentStatus.READY,
    val outputStyle: OutputStyle = OutputStyle.BALANCED,
    val verbosity: Verbosity = Verbosity.NORMAL,
    val exposeUncertainty: Boolean = true,
    val includeAssumptions: Boolean = true,
    val includeAlternatives: Boolean = true,
    val capabilities: Set<AgentCapability> = emptySet(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isEnabled: Boolean get() = status != AgentStatus.DISABLED

    /** Back-compat alias used by flattened Room column mapping. */
    val reasoningLevel: String get() = reasoningLevelEnum.name
}
