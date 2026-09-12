package com.agentflow.domain.provider

import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.ai.AIUsageStats

class FakeAIProvider(
    private val type: ProviderType,
    var results: MutableList<ProviderResult<AIResponse>> = mutableListOf(),
) : AIProvider {
    val requests = mutableListOf<AIRequest>()

    override fun providerType(): ProviderType = type

    override suspend fun generate(request: AIRequest): ProviderResult<AIResponse> {
        requests += request
        if (results.isEmpty()) {
            return ProviderResult.Failure(
                ProviderError.of(type, ProviderErrorType.UNKNOWN, "no scripted result"),
            )
        }
        return results.removeAt(0)
    }

    override suspend fun listModels(): ProviderResult<List<ProviderModel>> =
        ProviderResult.Success(emptyList())

    override suspend fun testConnection(model: String?): ProviderResult<ProviderHealth> =
        ProviderResult.Success(ProviderHealth(type, model, true, 1, null, 0))

    companion object {
        fun ok(type: ProviderType, content: String = "ok") = ProviderResult.Success(
            AIResponse(type, "m", content, "stop", AIUsageStats(1, 1, 2, 10), 10, "req"),
        )

        fun fail(type: ProviderType, errorType: ProviderErrorType, status: Int? = null) =
            ProviderResult.Failure(ProviderError.of(type, errorType, errorType.name, httpStatus = status))
    }
}
