package com.agentflow.domain.ai

import com.agentflow.domain.provider.ProviderType

data class AIResponse(
    val provider: ProviderType,
    val model: String,
    val content: String,
    val finishReason: String? = null,
    val usage: AIUsageStats = AIUsageStats(),
    val latencyMs: Long,
    val requestId: String? = null,
    val rawResponsePath: String? = null,
)
