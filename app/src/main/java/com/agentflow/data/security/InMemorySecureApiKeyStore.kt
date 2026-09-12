package com.agentflow.data.security

import com.agentflow.domain.provider.ProviderType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-local store for tests and fallback when Keystore is unavailable.
 * Not a production secret store.
 */
class InMemorySecureApiKeyStore : SecureApiKeyStore {
    private val mutex = Mutex()
    private val keys = mutableMapOf<ProviderType, String>()

    override suspend fun saveApiKey(provider: ProviderType, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) throw InvalidApiKeyException("API key is empty")
        mutex.withLock { keys[provider] = trimmed }
    }

    override suspend fun getApiKey(provider: ProviderType): String? = mutex.withLock { keys[provider] }

    override suspend fun deleteApiKey(provider: ProviderType) {
        mutex.withLock { keys.remove(provider) }
    }

    override suspend fun hasApiKey(provider: ProviderType): Boolean = mutex.withLock { keys.containsKey(provider) }

    override suspend fun clearAll() {
        mutex.withLock { keys.clear() }
    }
}
