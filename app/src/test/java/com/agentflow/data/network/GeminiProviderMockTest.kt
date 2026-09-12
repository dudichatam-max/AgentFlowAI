package com.agentflow.data.network

import com.agentflow.data.network.ai.gemini.GeminiProvider
import com.agentflow.data.security.InMemorySecureApiKeyStore
import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.provider.ProviderErrorType
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GeminiProviderMockTest {

    @Test
    fun successResponse() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.headers["x-goog-api-key"]).isEqualTo("secret-key")
            respond(
                content = """{"candidates":[{"content":{"parts":[{"text":"pong"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":2,"candidatesTokenCount":1,"totalTokenCount":3}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val provider = provider(engine, key = "secret-key")
        val result = provider.generate(
            AIRequest(listOf(AIMessage(AIMessageRole.USER, "ping")), "gemini-2.5-flash"),
        )
        assertThat(result.getOrNull()?.content).isEqualTo("pong")
        assertThat(result.getOrNull()?.usage?.totalTokens).isEqualTo(3)
    }

    @Test
    fun malformedJson() = runTest {
        val engine = MockEngine {
            respond("not-json", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = provider(engine).generate(
            AIRequest(listOf(AIMessage(AIMessageRole.USER, "x")), "gemini-2.5-flash"),
        )
        assertThat((result as ProviderResult.Failure).error.type).isEqualTo(ProviderErrorType.INVALID_RESPONSE)
    }

    @Test
    fun httpErrorNormalized() = runTest {
        val engine = MockEngine {
            respond("""{"error":{"message":"nope"}}""", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = provider(engine).generate(
            AIRequest(listOf(AIMessage(AIMessageRole.USER, "x")), "gemini-2.5-flash"),
        )
        val error = (result as ProviderResult.Failure).error
        assertThat(error.type).isEqualTo(ProviderErrorType.RATE_LIMITED)
        assertThat(error.retryable).isTrue()
    }

    @Test
    fun missingKey() = runTest {
        val engine = MockEngine { error("should not call network") }
        val store = InMemorySecureApiKeyStore()
        val result = GeminiProvider(store, client(engine)).generate(
            AIRequest(listOf(AIMessage(AIMessageRole.USER, "x")), "gemini-2.5-flash"),
        )
        assertThat((result as ProviderResult.Failure).error.type).isEqualTo(ProviderErrorType.AUTHENTICATION_ERROR)
    }

    private suspend fun provider(engine: MockEngine, key: String = "k"): GeminiProvider {
        val store = InMemorySecureApiKeyStore()
        store.saveApiKey(ProviderType.GEMINI, key)
        return GeminiProvider(store, client(engine))
    }

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(HttpClientFactory.json) }
    }
}
