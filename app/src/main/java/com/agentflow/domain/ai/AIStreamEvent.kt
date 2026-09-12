package com.agentflow.domain.ai

import com.agentflow.domain.provider.ProviderError

sealed class AIStreamEvent {
    data object Started : AIStreamEvent()
    data class Chunk(val text: String) : AIStreamEvent()
    data class Completed(val response: AIResponse) : AIStreamEvent()
    data class Failed(val error: ProviderError) : AIStreamEvent()
}
