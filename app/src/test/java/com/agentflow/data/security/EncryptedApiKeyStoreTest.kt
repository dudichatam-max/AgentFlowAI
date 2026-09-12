package com.agentflow.data.security

import com.agentflow.domain.provider.ProviderType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Exercises the same AES-GCM payload used on device, with a software key.
 * This does not prove Android Keystore behavior.
 */
private class SoftwareEncryptedApiKeyStore(
    private val key: SecretKey,
) : SecureApiKeyStore {
    private val payloads = mutableMapOf<ProviderType, String>()

    override suspend fun saveApiKey(provider: ProviderType, apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) throw InvalidApiKeyException("API key is empty")
        payloads[provider] = AesGcmCipher.encrypt(trimmed.toByteArray(Charsets.UTF_8), key)
    }

    override suspend fun getApiKey(provider: ProviderType): String? {
        val payload = payloads[provider] ?: return null
        return try {
            AesGcmCipher.decrypt(payload, key).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            payloads.remove(provider)
            null
        }
    }

    override suspend fun deleteApiKey(provider: ProviderType) {
        payloads.remove(provider)
    }

    override suspend fun hasApiKey(provider: ProviderType): Boolean = payloads.containsKey(provider)

    override suspend fun clearAll() {
        payloads.clear()
    }

    fun rawPayload(provider: ProviderType): String? = payloads[provider]
}

class EncryptedApiKeyStoreTest {
    private fun store() = SoftwareEncryptedApiKeyStore(
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
    )

    @Test
    fun saveLoadReturnsOriginal() = runTest {
        val store = store()
        store.saveApiKey(ProviderType.GEMINI, "  test-gemini-key  ")
        assertThat(store.getApiKey(ProviderType.GEMINI)).isEqualTo("test-gemini-key")
    }

    @Test
    fun providersAreIsolated() = runTest {
        val store = store()
        store.saveApiKey(ProviderType.GEMINI, "test-gemini-key")
        store.saveApiKey(ProviderType.GROQ, "test-groq-key")
        store.saveApiKey(ProviderType.OPEN_ROUTER, "test-openrouter-key")
        assertThat(store.getApiKey(ProviderType.GEMINI)).isEqualTo("test-gemini-key")
        assertThat(store.getApiKey(ProviderType.GROQ)).isEqualTo("test-groq-key")
        assertThat(store.getApiKey(ProviderType.OPEN_ROUTER)).isEqualTo("test-openrouter-key")
        store.deleteApiKey(ProviderType.GROQ)
        assertThat(store.getApiKey(ProviderType.GROQ)).isNull()
        assertThat(store.getApiKey(ProviderType.GEMINI)).isEqualTo("test-gemini-key")
        assertThat(store.getApiKey(ProviderType.OPEN_ROUTER)).isEqualTo("test-openrouter-key")
    }

    @Test
    fun replacingProviderKeyWorks() = runTest {
        val store = store()
        store.saveApiKey(ProviderType.GEMINI, "first-key")
        store.saveApiKey(ProviderType.GEMINI, "second-key")
        assertThat(store.getApiKey(ProviderType.GEMINI)).isEqualTo("second-key")
    }

    @Test
    fun removingProviderKeyWorks() = runTest {
        val store = store()
        store.saveApiKey(ProviderType.GEMINI, "test-gemini-key")
        store.deleteApiKey(ProviderType.GEMINI)
        assertThat(store.hasApiKey(ProviderType.GEMINI)).isFalse()
        assertThat(store.getApiKey(ProviderType.GEMINI)).isNull()
    }

    @Test
    fun replaceWritesNewPayload() = runTest {
        val store = store()
        store.saveApiKey(ProviderType.GEMINI, "same-plaintext")
        val first = store.rawPayload(ProviderType.GEMINI)
        store.saveApiKey(ProviderType.GEMINI, "same-plaintext")
        val second = store.rawPayload(ProviderType.GEMINI)
        assertThat(first).isNotEqualTo(second)
        assertThat(store.getApiKey(ProviderType.GEMINI)).isEqualTo("same-plaintext")
    }

    @Test
    fun corruptedPayloadClearedOnRead() = runTest {
        val store = store()
        store.saveApiKey(ProviderType.GEMINI, "test-gemini-key")
        val field = SoftwareEncryptedApiKeyStore::class.java.getDeclaredField("payloads")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(store) as MutableMap<ProviderType, String>
        map[ProviderType.GEMINI] = "AFKS1not-valid-base64???"
        assertThat(store.getApiKey(ProviderType.GEMINI)).isNull()
    }
}
