package com.agentflow.domain.model
/** Metadata only. API keys never stored here. */
data class ProviderConfig(
    val id: String, val providerId: ProviderId, val enabled: Boolean = true,
    val freeOnly: Boolean = true, val defaultModelId: String, val createdAt: Long, val updatedAt: Long,
)
