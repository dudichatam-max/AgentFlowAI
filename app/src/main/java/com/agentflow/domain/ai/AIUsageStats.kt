package com.agentflow.domain.ai

data class AIUsageStats(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val totalTokens: Int? = null,
    val latencyMs: Long? = null,
)
