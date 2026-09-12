package com.agentflow.data.network.ai.openai

import com.agentflow.data.network.HttpClientFactory
import com.agentflow.data.network.ai.HttpErrorMapper
import com.agentflow.data.security.SecureApiKeyStore
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.provider.FreeModelCatalog
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
import kotlinx.serialization.json.Json

internal class OpenAiCompatClient(
    private val provider: ProviderType,
    private val keyStore: SecureApiKeyStore,
    private val http: HttpClient,
    private val json: Json = HttpClientFactory.json,
    private val chatUrl: String,
    private val modelsUrl: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
) {
    suspend fun generate(request: AIRequest): ProviderResult<AIResponse> {
        val key = keyStore.getApiKey(provider)
            ?: return ProviderResult.Failure(missingKey())
        val started = System.currentTimeMillis()
        return try {
            val response = http.post(chatUrl) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $key")
                extraHeaders.forEach { (k, v) -> header(k, v) }
                setBody(OpenAiCompatMapper.toRequest(provider, request))
            }
            val latency = System.currentTimeMillis() - started
            val text = response.bodyAsText()
            if (response.status.value >= 400) {
                return ProviderResult.Failure(
                    HttpErrorMapper.fromStatus(
                        provider,
                        response.status.value,
                        text,
                        HttpErrorMapper.parseRetryAfterMs(response.headers["Retry-After"]),
                    ),
                )
            }
            val parsed = runCatching { json.decodeFromString(OpenAiChatResponse.serializer(), text) }
                .getOrElse {
                    return ProviderResult.Failure(
                        ProviderError.of(provider, ProviderErrorType.INVALID_RESPONSE, "Malformed chat response"),
                    )
                }
            if (parsed.error != null) {
                return ProviderResult.Failure(
                    ProviderError.of(
                        provider,
                        ProviderErrorType.INVALID_REQUEST,
                        parsed.error.message ?: "Provider error",
                    ),
                )
            }
            ProviderResult.Success(OpenAiCompatMapper.toResponse(provider, request.model, parsed, latency))
        } catch (t: Throwable) {
            ProviderResult.Failure(HttpErrorMapper.fromThrowable(provider, t))
        }
    }

    suspend fun listModels(): ProviderResult<List<ProviderModel>> {
        val key = keyStore.getApiKey(provider) ?: return ProviderResult.Failure(missingKey())
        return try {
            val response = http.get(modelsUrl) {
                header(HttpHeaders.Authorization, "Bearer $key")
                extraHeaders.forEach { (k, v) -> header(k, v) }
            }
            val text = response.bodyAsText()
            if (response.status.value >= 400) {
                return ProviderResult.Failure(HttpErrorMapper.fromStatus(provider, response.status.value, text))
            }
            val parsed = json.decodeFromString(OpenAiModelList.serializer(), text)
            ProviderResult.Success(
                parsed.data.orEmpty().mapNotNull { card ->
                    val id = card.id ?: return@mapNotNull null
                    val pricedFree = card.pricing?.let { pricing ->
                        val prompt = pricing.prompt?.toDoubleOrNull() ?: 1.0
                        val completion = pricing.completion?.toDoubleOrNull() ?: 1.0
                        prompt == 0.0 && completion == 0.0
                    }
                    val catalog = FreeModelCatalog.isKnownFree(provider, id)
                    ProviderModel(
                        id = id,
                        displayName = card.name ?: id,
                        provider = provider,
                        isFree = pricedFree ?: catalog,
                        contextWindow = card.contextLength,
                    )
                },
            )
        } catch (t: Throwable) {
            ProviderResult.Failure(HttpErrorMapper.fromThrowable(provider, t))
        }
    }

    suspend fun health(model: String?): ProviderResult<ProviderHealth> {
        val started = System.currentTimeMillis()
        return when (val listed = listModels()) {
            is ProviderResult.Success -> ProviderResult.Success(
                ProviderHealth(provider, model, true, System.currentTimeMillis() - started, null, System.currentTimeMillis()),
            )
            is ProviderResult.Failure -> ProviderResult.Success(
                ProviderHealth(provider, model, false, System.currentTimeMillis() - started, listed.error, System.currentTimeMillis()),
            )
        }
    }

    private fun missingKey() = ProviderError.of(
        provider,
        ProviderErrorType.AUTHENTICATION_ERROR,
        "${provider.name} API key is not configured",
    )
}
