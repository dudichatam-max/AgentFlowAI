package com.agentflow.data.security

import com.agentflow.domain.provider.ProviderType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InMemorySecureApiKeyStoreTest {
    private val store = InMemorySecureApiKeyStore()

    @Test
    fun saveGetDelete() = runTest {
        store.saveApiKey(ProviderType.GEMINI, "  gsk-test  ")
        assertThat(store.hasApiKey(ProviderType.GEMINI)).isTrue()
        assertThat(store.getApiKey(ProviderType.GEMINI)).isEqualTo("gsk-test")
        store.deleteApiKey(ProviderType.GEMINI)
        assertThat(store.hasApiKey(ProviderType.GEMINI)).isFalse()
        assertThat(store.getApiKey(ProviderType.GEMINI)).isNull()
    }

    @Test
    fun missingKey() = runTest {
        assertThat(store.getApiKey(ProviderType.GROQ)).isNull()
        assertThat(store.hasApiKey(ProviderType.GROQ)).isFalse()
    }

    @Test
    fun clearAll() = runTest {
        store.saveApiKey(ProviderType.GEMINI, "a")
        store.saveApiKey(ProviderType.GROQ, "b")
        store.clearAll()
        assertThat(store.hasApiKey(ProviderType.GEMINI)).isFalse()
        assertThat(store.hasApiKey(ProviderType.GROQ)).isFalse()
    }

    @Test
    fun emptyKeyRejected() = runTest {
        var thrown = false
        try {
            store.saveApiKey(ProviderType.OPEN_ROUTER, "   ")
        } catch (_: InvalidApiKeyException) {
            thrown = true
        }
        assertThat(thrown).isTrue()
    }
}
