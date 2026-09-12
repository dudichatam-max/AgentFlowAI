package com.agentflow.domain.chat

import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.ai.AIStreamEvent
import com.agentflow.domain.ai.AIUsageStats
import com.agentflow.domain.provider.AIProvider
import com.agentflow.domain.provider.ProviderError
import com.agentflow.domain.provider.ProviderErrorType
import com.agentflow.domain.provider.ProviderHealth
import com.agentflow.domain.provider.ProviderModel
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeStreamingProvider(
    private val type: ProviderType = ProviderType.GEMINI,
    private val chunks: List<String> = listOf("Hello", " world", "!"),
    private val terminal: Terminal = Terminal.Complete,
) : AIProvider {
    enum class Terminal { Complete, Fail, HangForCancel }

    override fun providerType(): ProviderType = type

    override suspend fun generate(request: AIRequest): ProviderResult<AIResponse> =
        ProviderResult.Success(AIResponse(type, request.model, chunks.joinToString(""), "stop", AIUsageStats(), 1))

    override suspend fun listModels() = ProviderResult.Success(emptyList<ProviderModel>())

    override suspend fun testConnection(model: String?) =
        ProviderResult.Success(ProviderHealth(type, model, true, 1, null, 0))

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = flow {
        emit(AIStreamEvent.Started)
        chunks.forEach { emit(AIStreamEvent.Chunk(it)) }
        when (terminal) {
            Terminal.Complete -> emit(
                AIStreamEvent.Completed(
                    AIResponse(type, request.model, chunks.joinToString(""), "stop", AIUsageStats(1, 1, 2, 5), 5),
                ),
            )
            Terminal.Fail -> emit(
                AIStreamEvent.Failed(ProviderError.of(type, ProviderErrorType.SERVER_ERROR, "boom", 500)),
            )
            Terminal.HangForCancel -> kotlinx.coroutines.delay(60_000)
        }
    }
}
