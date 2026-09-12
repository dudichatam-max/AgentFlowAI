package com.agentflow.di

import android.content.Context
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.network.HttpClientFactory
import com.agentflow.data.network.ai.gemini.GeminiProvider
import com.agentflow.data.network.ai.groq.GroqProvider
import com.agentflow.data.network.ai.openrouter.OpenRouterProvider
import com.agentflow.data.repository.ProviderConfigRepository
import com.agentflow.data.repository.ProviderRepository
import com.agentflow.data.repository.ProviderRepositoryImpl
import com.agentflow.data.security.AndroidSecureApiKeyStore
import com.agentflow.data.security.SecureApiKeyStore
import com.agentflow.domain.provider.AIProvider
import com.agentflow.domain.provider.InMemoryModelRegistry
import com.agentflow.domain.provider.ModelRegistry
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.retry.RetryPolicy
import io.ktor.client.HttpClient

/**
 * Manual graph. No Hilt in this phase so tests can swap fakes without a framework.
 */
class ProviderModule(
    context: Context,
    database: AgentFlowDatabase,
    httpClient: HttpClient = HttpClientFactory.create(),
    keyStore: SecureApiKeyStore = AndroidSecureApiKeyStore(context),
) {
    val keyStore: SecureApiKeyStore = keyStore
    val httpClient: HttpClient = httpClient

    val providerConfigRepository = ProviderConfigRepository(database.providerConfigDao())
    val providerRepository: ProviderRepository = ProviderRepositoryImpl(providerConfigRepository, keyStore)

    val gemini: AIProvider = GeminiProvider(keyStore, httpClient)
    val groq: AIProvider = GroqProvider(keyStore, httpClient)
    val openRouter: AIProvider = OpenRouterProvider(keyStore, httpClient)

    val manager: ProviderManager = ProviderManager(
        providers = mapOf(
            ProviderType.GEMINI to gemini,
            ProviderType.GROQ to groq,
            ProviderType.OPEN_ROUTER to openRouter,
        ),
        retryPolicy = RetryPolicy(),
        freeOnly = { true },
    )

    val modelRegistry: ModelRegistry = InMemoryModelRegistry(manager)
}
