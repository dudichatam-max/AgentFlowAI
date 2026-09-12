package com.agentflow.data.network.ai.groq

import com.agentflow.data.network.HttpClientFactory
import com.agentflow.data.network.ai.openai.OpenAiCompatClient
import com.agentflow.data.security.SecureApiKeyStore
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.provider.AIProvider
import com.agentflow.domain.provider.ProviderHealth
import com.agentflow.domain.provider.ProviderModel
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GroqProvider(
    keyStore: SecureApiKeyStore,
    http: HttpClient,
    baseUrl: String = DEFAULT_BASE,
) : AIProvider {

    private val client = OpenAiCompatClient(
        provider = ProviderType.GROQ,
        keyStore = keyStore,
        http = http,
        json = HttpClientFactory.json,
        chatUrl = "$baseUrl/chat/completions",
        modelsUrl = "$baseUrl/models",
    )

    override fun providerType(): ProviderType = ProviderType.GROQ

    override suspend fun generate(request: AIRequest): ProviderResult<AIResponse> =
        withContext(Dispatchers.IO) { client.generate(request) }

    override suspend fun listModels(): ProviderResult<List<ProviderModel>> =
        withContext(Dispatchers.IO) { client.listModels() }

    override suspend fun testConnection(model: String?): ProviderResult<ProviderHealth> =
        withContext(Dispatchers.IO) { client.health(model) }

    companion object {
        const val DEFAULT_BASE = "https://api.groq.com/openai/v1"
    }
}
