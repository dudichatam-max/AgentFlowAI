package com.agentflow.domain.provider

import com.agentflow.domain.model.ProviderId

/**
 * Runtime provider identity. Kept separate from [ProviderId] so Room
 * storage stays a stable string enum while the provider layer can evolve.
 */
enum class ProviderType {
    GEMINI,
    GROQ,
    OPEN_ROUTER,
}

fun ProviderType.toProviderId(): ProviderId = when (this) {
    ProviderType.GEMINI -> ProviderId.GEMINI
    ProviderType.GROQ -> ProviderId.GROQ
    ProviderType.OPEN_ROUTER -> ProviderId.OPENROUTER
}

fun ProviderId.toProviderType(): ProviderType = when (this) {
    ProviderId.GEMINI -> ProviderType.GEMINI
    ProviderId.GROQ -> ProviderType.GROQ
    ProviderId.OPENROUTER -> ProviderType.OPEN_ROUTER
}
