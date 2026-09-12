package com.agentflow.domain.provider

data class ProviderHealth(
    val provider: ProviderType,
    val model: String?,
    val success: Boolean,
    val latencyMs: Long,
    val error: ProviderError?,
    val checkedAt: Long,
)
