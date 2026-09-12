package com.agentflow.domain.agent

import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.provider.toProviderType

data class AgentModelConfig(
    val provider: ProviderId,
    val modelId: String,
    val fallbackProvider: ProviderId? = null,
    val fallbackModelId: String? = null,
    val temperature: Double = 0.4,
    val maxOutputTokens: Int = 4096,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val freeOnly: Boolean = true,
) {
    val providerType: ProviderType get() = provider.toProviderType()
    val fallbackProviderType: ProviderType? get() = fallbackProvider?.toProviderType()
}
