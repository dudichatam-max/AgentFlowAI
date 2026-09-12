package com.agentflow.domain.provider

import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.ai.AIStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AIProvider {
    fun providerType(): ProviderType
    suspend fun generate(request: AIRequest): ProviderResult<AIResponse>
    suspend fun listModels(): ProviderResult<List<ProviderModel>>
    suspend fun testConnection(model: String? = null): ProviderResult<ProviderHealth>

    /**
     * Default: one-shot generate wrapped as a stream.
     * Providers that support SSE override this.
     */
    fun stream(request: AIRequest): Flow<AIStreamEvent> = flow {
        emit(AIStreamEvent.Started)
        when (val result = generate(request)) {
            is ProviderResult.Success -> {
                if (result.value.content.isNotEmpty()) emit(AIStreamEvent.Chunk(result.value.content))
                emit(AIStreamEvent.Completed(result.value))
            }
            is ProviderResult.Failure -> emit(AIStreamEvent.Failed(result.error))
        }
    }
}
