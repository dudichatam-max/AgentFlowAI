package com.agentflow.data.network.ai.gemini

import com.agentflow.data.network.HttpClientFactory
import com.agentflow.data.network.ai.HttpErrorMapper
import com.agentflow.data.security.SecureApiKeyStore
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.provider.AIProvider
import com.agentflow.domain.provider.ProviderError
import com.agentflow.domain.provider.ProviderErrorType
import com.agentflow.domain.provider.ProviderHealth
import com.agentflow.domain.provider.ProviderModel
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class GeminiProvider(
    private val keyStore: SecureApiKeyStore,
    private val http: HttpClient,
    private val json: Json = HttpClientFactory.json,
    private val baseUrl: String = DEFAULT_BASE,
) : AIProvider {

    override fun providerType(): ProviderType = ProviderType.GEMINI

    override suspend fun generate(request: AIRequest): ProviderResult<AIResponse> =
        withContext(Dispatchers.IO) {
            val key = keyStore.getApiKey(ProviderType.GEMINI)
                ?: return@withContext missingKey()
            val started = System.currentTimeMillis()
            val model = request.model.removePrefix("models/")
            try {
                val response = http.post("$baseUrl/models/$model:generateContent") {
                    contentType(ContentType.Application.Json)
                    header("x-goog-api-key", key)
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    setBody(GeminiMapper.toRequest(request))
                }
                val latency = System.currentTimeMillis() - started
                val text = response.bodyAsText()
                if (response.status.value >= 400) {
                    return@withContext ProviderResult.Failure(
                        HttpErrorMapper.fromStatus(
                            ProviderType.GEMINI,
                            response.status.value,
                            text,
                            HttpErrorMapper.parseRetryAfterMs(response.headers["Retry-After"]),
                        ),
                    )
                }
                val parsed = runCatching { json.decodeFromString(GeminiGenerateResponse.serializer(), text) }
                    .getOrElse {
                        return@withContext ProviderResult.Failure(
                            ProviderError.of(
                                ProviderType.GEMINI,
                                ProviderErrorType.INVALID_RESPONSE,
                                "Malformed Gemini response",
                            ),
                        )
                    }
                if (parsed.error != null) {
                    return@withContext ProviderResult.Failure(
                        HttpErrorMapper.fromStatus(
                            ProviderType.GEMINI,
                            parsed.error.code ?: 400,
                            parsed.error.message ?: "Gemini error",
                        ),
                    )
                }
                ProviderResult.Success(GeminiMapper.toResponse(model, parsed, latency))
            } catch (t: Throwable) {
                ProviderResult.Failure(HttpErrorMapper.fromThrowable(ProviderType.GEMINI, t))
            }
        }

    override suspend fun listModels(): ProviderResult<List<ProviderModel>> = withContext(Dispatchers.IO) {
        val key = keyStore.getApiKey(ProviderType.GEMINI) ?: return@withContext missingKey()
        try {
            val response = http.get("$baseUrl/models") {
                header("x-goog-api-key", key)
            }
            val text = response.bodyAsText()
            if (response.status.value >= 400) {
                return@withContext ProviderResult.Failure(
                    HttpErrorMapper.fromStatus(ProviderType.GEMINI, response.status.value, text),
                )
            }
            val parsed = json.decodeFromString(GeminiModelListResponse.serializer(), text)
            ProviderResult.Success(parsed.models.orEmpty().mapNotNull { GeminiMapper.toModel(it) })
        } catch (t: Throwable) {
            ProviderResult.Failure(HttpErrorMapper.fromThrowable(ProviderType.GEMINI, t))
        }
    }

    override suspend fun testConnection(model: String?): ProviderResult<ProviderHealth> =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            when (val listed = listModels()) {
                is ProviderResult.Success -> ProviderResult.Success(
                    ProviderHealth(
                        provider = ProviderType.GEMINI,
                        model = model,
                        success = true,
                        latencyMs = System.currentTimeMillis() - started,
                        error = null,
                        checkedAt = System.currentTimeMillis(),
                    ),
                )
                is ProviderResult.Failure -> ProviderResult.Success(
                    ProviderHealth(
                        provider = ProviderType.GEMINI,
                        model = model,
                        success = false,
                        latencyMs = System.currentTimeMillis() - started,
                        error = listed.error,
                        checkedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

    private fun missingKey(): ProviderResult.Failure = ProviderResult.Failure(
        ProviderError.of(
            ProviderType.GEMINI,
            ProviderErrorType.AUTHENTICATION_ERROR,
            "Gemini API key is not configured",
        ),
    )

    companion object {
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
