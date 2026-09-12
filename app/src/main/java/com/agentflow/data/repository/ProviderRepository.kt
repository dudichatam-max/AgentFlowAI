package com.agentflow.data.repository

import com.agentflow.data.security.SecureApiKeyStore
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.ProviderConfig
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.provider.toProviderId
import kotlinx.coroutines.flow.Flow

interface ProviderRepository {
    fun observeConfigs(): Flow<List<ProviderConfig>>
    suspend fun getConfig(type: ProviderType): ProviderConfig?
    suspend fun upsertConfig(config: ProviderConfig)
    suspend fun setEnabled(type: ProviderType, enabled: Boolean)
    suspend fun setFreeOnly(type: ProviderType, freeOnly: Boolean)
    suspend fun saveApiKey(type: ProviderType, apiKey: String)
    suspend fun getApiKey(type: ProviderType): String?
    suspend fun deleteApiKey(type: ProviderType)
    suspend fun hasApiKey(type: ProviderType): Boolean
    suspend fun isFreeOnlyDefault(): Boolean
}

class ProviderRepositoryImpl(
    private val configRepository: ProviderConfigRepository,
    private val keyStore: SecureApiKeyStore,
    private val defaultModels: Map<ProviderType, String> = mapOf(
        ProviderType.GEMINI to "gemini-2.5-flash",
        ProviderType.GROQ to "openai/gpt-oss-20b",
        ProviderType.OPEN_ROUTER to "meta-llama/llama-3.1-8b-instruct:free",
    ),
) : ProviderRepository {

    override fun observeConfigs(): Flow<List<ProviderConfig>> = configRepository.observeAll()

    override suspend fun getConfig(type: ProviderType): ProviderConfig? =
        configRepository.getByProvider(type.toProviderId().name)

    override suspend fun upsertConfig(config: ProviderConfig) = configRepository.upsert(config)

    override suspend fun setEnabled(type: ProviderType, enabled: Boolean) {
        val now = System.currentTimeMillis()
        val current = getConfig(type) ?: defaultConfig(type, now)
        upsertConfig(current.copy(enabled = enabled, updatedAt = now))
    }

    override suspend fun setFreeOnly(type: ProviderType, freeOnly: Boolean) {
        val now = System.currentTimeMillis()
        val current = getConfig(type) ?: defaultConfig(type, now)
        upsertConfig(current.copy(freeOnly = freeOnly, updatedAt = now))
    }

    override suspend fun saveApiKey(type: ProviderType, apiKey: String) = keyStore.saveApiKey(type, apiKey)

    override suspend fun getApiKey(type: ProviderType): String? = keyStore.getApiKey(type)

    override suspend fun deleteApiKey(type: ProviderType) = keyStore.deleteApiKey(type)

    override suspend fun hasApiKey(type: ProviderType): Boolean = keyStore.hasApiKey(type)

    override suspend fun isFreeOnlyDefault(): Boolean = true

    private fun defaultConfig(type: ProviderType, now: Long) = ProviderConfig(
        id = Ids.new(),
        providerId = type.toProviderId(),
        enabled = true,
        freeOnly = true,
        defaultModelId = defaultModels.getValue(type),
        createdAt = now,
        updatedAt = now,
    )
}


