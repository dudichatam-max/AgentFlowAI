package com.agentflow.domain.provider

data class ProviderCapabilities(
    val supportsVision: Boolean? = null,
    val supportsTools: Boolean? = null,
    val supportsStructuredOutput: Boolean? = null,
    val supportsReasoning: Boolean? = null,
)

/**
 * [isFree] is tri-state on purpose: `null` means cost is unknown.
 * Free-only policy rejects unknown rather than guessing.
 */
data class ProviderModel(
    val id: String,
    val displayName: String,
    val provider: ProviderType,
    val isFree: Boolean?,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsVision: Boolean? = null,
    val supportsTools: Boolean? = null,
    val supportsStructuredOutput: Boolean? = null,
    val supportsReasoning: Boolean? = null,
)
