package com.agentflow.data.security

import com.agentflow.domain.provider.ProviderType

/**
 * Secret store for provider API keys. Implementations must use
 * platform-backed encryption in production. Keys never belong in Room.
 */
interface SecureApiKeyStore {
    suspend fun saveApiKey(provider: ProviderType, apiKey: String)
    suspend fun getApiKey(provider: ProviderType): String?
    suspend fun deleteApiKey(provider: ProviderType)
    suspend fun hasApiKey(provider: ProviderType): Boolean
    suspend fun clearAll()
}

class InvalidApiKeyException(message: String) : IllegalArgumentException(message)
