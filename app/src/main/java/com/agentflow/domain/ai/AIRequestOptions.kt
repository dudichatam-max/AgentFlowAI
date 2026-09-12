package com.agentflow.domain.ai

data class AIRequestOptions(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val topP: Double? = null,
    val timeoutMs: Long? = null,
    val reasoningLevel: String? = null,
)
