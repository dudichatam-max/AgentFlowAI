package com.agentflow.domain.provider

import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIStreamEvent
import com.agentflow.domain.retry.RetryPolicy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProviderManagerTest {

    private val request = AIRequest(
        messages = listOf(AIMessage(AIMessageRole.USER, "hi")),
        model = "llama-3.1-8b-instant",
    )

    @Test
    fun selectsPrimaryProvider() = runTest {
        val gemini = FakeAIProvider(ProviderType.GEMINI, mutableListOf(FakeAIProvider.ok(ProviderType.GEMINI, "g")))
        val manager = manager(gemini)
        val result = manager.generate(request.copy(model = "gemini-2.5-flash"), ProviderType.GEMINI)
        assertThat(result.getOrNull()?.content).isEqualTo("g")
        assertThat(gemini.requests).hasSize(1)
    }

    @Test
    fun fallbackOnRateLimitAfterRetries() = runTest {
        val gemini = FakeAIProvider(
            ProviderType.GEMINI,
            mutableListOf(
                FakeAIProvider.fail(ProviderType.GEMINI, ProviderErrorType.RATE_LIMITED, 429),
                FakeAIProvider.fail(ProviderType.GEMINI, ProviderErrorType.RATE_LIMITED, 429),
                FakeAIProvider.fail(ProviderType.GEMINI, ProviderErrorType.RATE_LIMITED, 429),
            ),
        )
        val groq = FakeAIProvider(ProviderType.GROQ, mutableListOf(FakeAIProvider.ok(ProviderType.GROQ, "fallback")))
        val manager = manager(gemini, groq, attempts = 2)
        val result = manager.generate(
            request.copy(model = "llama-3.1-8b-instant"),
            primary = ProviderType.GEMINI,
            fallbacks = listOf(ProviderType.GROQ),
        )
        assertThat(result.getOrNull()?.content).isEqualTo("fallback")
        assertThat(gemini.requests.size).isEqualTo(2)
        assertThat(groq.requests).hasSize(1)
    }

    @Test
    fun noFallbackOnAuthentication() = runTest {
        val gemini = FakeAIProvider(
            ProviderType.GEMINI,
            mutableListOf(FakeAIProvider.fail(ProviderType.GEMINI, ProviderErrorType.AUTHENTICATION_ERROR, 401)),
        )
        val groq = FakeAIProvider(ProviderType.GROQ, mutableListOf(FakeAIProvider.ok(ProviderType.GROQ)))
        val manager = manager(gemini, groq)
        val result = manager.generate(
            request.copy(model = "gemini-2.5-flash"),
            ProviderType.GEMINI,
            listOf(ProviderType.GROQ),
        )
        assertThat(result.isSuccess).isFalse()
        assertThat((result as ProviderResult.Failure).error.type).isEqualTo(ProviderErrorType.AUTHENTICATION_ERROR)
        assertThat(groq.requests).isEmpty()
    }

    @Test
    fun policyBlocksPaidBeforeCall() = runTest {
        val gemini = FakeAIProvider(ProviderType.GEMINI)
        val manager = ProviderManager(
            providers = mapOf(ProviderType.GEMINI to gemini),
            retryPolicy = RetryPolicy(maxAttempts = 1),
            freeOnly = { true },
            modelLookup = { _, id ->
                ProviderModel(id, id, ProviderType.GEMINI, isFree = false)
            },
            sleeper = {},
        )
        val result = manager.generate(request.copy(model = "gemini-pro-paid"), ProviderType.GEMINI)
        assertThat((result as ProviderResult.Failure).error.type).isEqualTo(ProviderErrorType.FREE_MODEL_REQUIRED)
        assertThat(gemini.requests).isEmpty()
    }

    @Test
    fun streamingDoesNotFallbackAfterPartialOutput() = runTest {
        val primary = object : AIProvider {
            override fun providerType() = ProviderType.GEMINI
            override suspend fun generate(request: AIRequest) = FakeAIProvider.ok(ProviderType.GEMINI)
            override suspend fun listModels() = ProviderResult.Success(emptyList<ProviderModel>())
            override suspend fun testConnection(model: String?) = ProviderResult.Success(ProviderHealth(ProviderType.GEMINI, model, true, 1, null, 0))
            override fun stream(request: AIRequest) = flow {
                emit(AIStreamEvent.Started)
                emit(AIStreamEvent.Chunk("partial"))
                emit(AIStreamEvent.Failed(ProviderError.of(ProviderType.GEMINI, ProviderErrorType.NETWORK_ERROR, "dropped")))
            }
        }
        val fallback = FakeAIProvider(ProviderType.GROQ, mutableListOf(FakeAIProvider.ok(ProviderType.GROQ, "fallback")))
        val manager = manager(primaryFake = primary, fallback = fallback)

        val events = mutableListOf<AIStreamEvent>()
        manager.stream(request.copy(model = "gemini-2.5-flash"), ProviderType.GEMINI, listOf(ProviderType.GROQ)).collect { events += it }

        assertThat(events.filterIsInstance<AIStreamEvent.Chunk>().map { it.text }).containsExactly("partial")
        assertThat(fallback.requests).isEmpty()
        assertThat(events.last()).isInstanceOf(AIStreamEvent.Failed::class.java)
    }

    @Test
    fun streamingPropagatesCancellationInsteadOfConvertingToProviderFailure() = runTest {
        val primary = object : AIProvider {
            override fun providerType() = ProviderType.GEMINI
            override suspend fun generate(request: AIRequest) = FakeAIProvider.ok(ProviderType.GEMINI)
            override suspend fun listModels() = ProviderResult.Success(emptyList<ProviderModel>())
            override suspend fun testConnection(model: String?) = ProviderResult.Success(ProviderHealth(ProviderType.GEMINI, model, true, 1, null, 0))
            override fun stream(request: AIRequest) = flow {
                throw CancellationException("cancelled")
            }
        }
        val fallback = FakeAIProvider(ProviderType.GROQ, mutableListOf(FakeAIProvider.ok(ProviderType.GROQ, "fallback")))
        val manager = manager(primaryFake = primary, fallback = fallback)

        try {
            manager.stream(request.copy(model = "gemini-2.5-flash"), ProviderType.GEMINI, listOf(ProviderType.GROQ)).collect { }
            throw AssertionError("expected cancellation")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("cancelled")
        }
        assertThat(fallback.requests).isEmpty()
    }

    @Test
    fun doesNotLoop() = runTest {
        val gemini = FakeAIProvider(
            ProviderType.GEMINI,
            MutableList(10) { FakeAIProvider.fail(ProviderType.GEMINI, ProviderErrorType.SERVER_ERROR, 503) },
        )
        val manager = manager(gemini, attempts = 3)
        manager.generate(request.copy(model = "gemini-2.5-flash"), ProviderType.GEMINI)
        assertThat(gemini.requests).hasSize(3)
    }

    private fun manager(
        vararg providers: FakeAIProvider,
        attempts: Int = 3,
    ) = ProviderManager(
        providers = providers.associateBy { it.providerType() },
        retryPolicy = RetryPolicy(maxAttempts = attempts, initialDelayMs = 0, maxDelayMs = 0, jitterFraction = 0.0),
        freeOnly = { true },
        modelLookup = { provider, id -> ProviderModel(id, id, provider, isFree = true) },
        sleeper = {},
    )

    private fun manager(primaryFake: AIProvider, fallback: FakeAIProvider) = ProviderManager(
        providers = mapOf(ProviderType.GEMINI to primaryFake, ProviderType.GROQ to fallback),
        retryPolicy = RetryPolicy(maxAttempts = 1, initialDelayMs = 0, maxDelayMs = 0, jitterFraction = 0.0),
        freeOnly = { true },
        modelLookup = { provider, id -> ProviderModel(id, id, provider, isFree = true) },
        sleeper = {},
    )
}
