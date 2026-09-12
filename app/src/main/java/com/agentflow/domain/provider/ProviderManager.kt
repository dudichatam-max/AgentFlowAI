package com.agentflow.domain.provider

import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.ai.AIStreamEvent
import com.agentflow.domain.retry.RetryPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AI infrastructure only. Does not know about Missions, Tasks, or Agents.
 */
class ProviderManager(
    private val providers: Map<ProviderType, AIProvider>,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val freeOnly: () -> Boolean = { true },
    private val modelLookup: (ProviderType, String) -> ProviderModel? = { provider, id ->
        ProviderModel(
            id = id,
            displayName = id,
            provider = provider,
            isFree = FreeModelCatalog.isKnownFree(provider, id),
        )
    },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    fun provider(type: ProviderType): AIProvider? = providers[type]

    fun registered(): Set<ProviderType> = providers.keys

    suspend fun listModels(type: ProviderType): ProviderResult<List<ProviderModel>> {
        val provider = providers[type] ?: return missing(type)
        return provider.listModels()
    }

    suspend fun testConnection(type: ProviderType, model: String? = null): ProviderResult<ProviderHealth> {
        val provider = providers[type] ?: return missing(type)
        return provider.testConnection(model)
    }

    suspend fun generate(
        request: AIRequest,
        primary: ProviderType,
        fallbacks: List<ProviderType> = emptyList(),
        fallbackModels: Map<ProviderType, String> = emptyMap(),
    ): ProviderResult<AIResponse> {
        val policyError = ProviderPolicy.validateModelId(
            provider = primary,
            modelId = request.model,
            freeOnly = freeOnly(),
            catalog = listOfNotNull(modelLookup(primary, request.model)),
        )
        if (policyError != null) return ProviderResult.Failure(policyError)

        val chain = (listOf(primary) + fallbacks).distinct()
        val attempted = linkedSetOf<ProviderType>()
        var lastFailure: ProviderError? = null

        for (type in chain) {
            if (!attempted.add(type)) continue
            val provider = providers[type]
            if (provider == null) {
                lastFailure = ProviderError.of(type, ProviderErrorType.UNKNOWN, "Provider not registered")
                continue
            }
            val providerRequest = request.copy(model = fallbackModels[type] ?: request.model)
            if (type != primary) {
                val fbPolicy = ProviderPolicy.validateModelId(
                    provider = type,
                    modelId = providerRequest.model,
                    freeOnly = freeOnly(),
                    catalog = listOfNotNull(modelLookup(type, providerRequest.model)),
                )
                if (fbPolicy != null) {
                    lastFailure = fbPolicy
                    continue
                }
            }

            when (val outcome = generateWithRetry(provider, providerRequest)) {
                is ProviderResult.Success -> return outcome
                is ProviderResult.Failure -> {
                    lastFailure = outcome.error
                    val canFallback = outcome.error.retryable && type != chain.last()
                    if (!canFallback) return outcome
                }
            }
        }
        return ProviderResult.Failure(
            lastFailure ?: ProviderError.of(primary, ProviderErrorType.UNKNOWN, "No provider available"),
        )
    }

    fun stream(
        request: AIRequest,
        primary: ProviderType,
        fallbacks: List<ProviderType> = emptyList(),
        fallbackModels: Map<ProviderType, String> = emptyMap(),
    ): Flow<AIStreamEvent> = flow {
        val policyError = ProviderPolicy.validateModelId(
            provider = primary,
            modelId = request.model,
            freeOnly = freeOnly(),
            catalog = listOfNotNull(modelLookup(primary, request.model)),
        )
        if (policyError != null) {
            emit(AIStreamEvent.Failed(policyError))
            return@flow
        }
        val chain = (listOf(primary) + fallbacks).distinct()
        val attempted = linkedSetOf<ProviderType>()
        var lastError: ProviderError? = null
        for (type in chain) {
            if (!attempted.add(type)) continue
            val provider = providers[type]
            if (provider == null) {
                lastError = ProviderError.of(type, ProviderErrorType.UNKNOWN, "Provider not registered")
                continue
            }
            val providerRequest = request.copy(model = fallbackModels[type] ?: request.model)
            if (type != primary) {
                val fbPolicy = ProviderPolicy.validateModelId(
                    provider = type,
                    modelId = providerRequest.model,
                    freeOnly = freeOnly(),
                    catalog = listOfNotNull(modelLookup(type, providerRequest.model)),
                )
                if (fbPolicy != null) {
                    lastError = fbPolicy
                    continue
                }
            }
            emit(AIStreamEvent.Started)
            var failed: ProviderError? = null
            var completed = false
            var emittedChunk = false
            try {
                provider.stream(providerRequest).collect { event ->
                    when (event) {
                        is AIStreamEvent.Started -> Unit
                        is AIStreamEvent.Chunk -> {
                            emittedChunk = true
                            emit(event)
                        }
                        is AIStreamEvent.Completed -> {
                            completed = true
                            emit(event)
                        }
                        is AIStreamEvent.Failed -> failed = event.error
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                failed = ProviderError.of(type, ProviderErrorType.UNKNOWN, t.message ?: "stream failed")
            }
            if (completed) return@flow
            val error = failed
            if (error != null) {
                lastError = error
                // Once any text has reached the caller, switching providers would
                // concatenate two independent generations into one response.
                // Fallback is therefore only safe before the first chunk.
                if (emittedChunk || !(error.retryable && type != chain.last())) {
                    emit(AIStreamEvent.Failed(error))
                    return@flow
                }
            }
        }
        emit(AIStreamEvent.Failed(lastError ?: ProviderError.of(primary, ProviderErrorType.UNKNOWN, "No provider available")))
    }

    private suspend fun generateWithRetry(
        provider: AIProvider,
        request: AIRequest,
    ): ProviderResult<AIResponse> {
        var attempt = 1
        var last: ProviderError? = null
        while (true) {
            when (val result = provider.generate(request)) {
                is ProviderResult.Success -> return result
                is ProviderResult.Failure -> {
                    last = result.error
                    if (!retryPolicy.shouldRetry(attempt, result.error)) return result
                    sleeper(retryPolicy.delayMs(attempt, result.error))
                    attempt += 1
                }
            }
        }
    }

    private fun missing(type: ProviderType): ProviderResult.Failure = ProviderResult.Failure(
        ProviderError.of(type, ProviderErrorType.UNKNOWN, "Provider ${type.name} is not registered"),
    )
}
