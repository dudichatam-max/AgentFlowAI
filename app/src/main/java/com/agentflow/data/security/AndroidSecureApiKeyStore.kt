package com.agentflow.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.agentflow.domain.provider.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Encrypts API keys with an AES key that lives in Android Keystore.
 * Ciphertext is stored in a private prefs file. The key material is never
 * written to disk. This is platform protection, not an unbreakable vault.
 */
class AndroidSecureApiKeyStore(
    context: Context,
) : SecureApiKeyStore {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun saveApiKey(provider: ProviderType, apiKey: String) = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) throw InvalidApiKeyException("API key is empty")
        mutex.withLock {
            val encrypted = AesGcmCipher.encrypt(trimmed.toByteArray(Charsets.UTF_8), secretKey())
            prefs.edit().putString(prefKey(provider), encrypted).apply()
        }
    }

    override suspend fun getApiKey(provider: ProviderType): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val payload = prefs.getString(prefKey(provider), null) ?: return@withLock null
            try {
                AesGcmCipher.decrypt(payload, secretKey()).toString(Charsets.UTF_8)
            } catch (_: Exception) {
                prefs.edit().remove(prefKey(provider)).apply()
                null
            }
        }
    }

    override suspend fun deleteApiKey(provider: ProviderType) = withContext(Dispatchers.IO) {
        mutex.withLock { prefs.edit().remove(prefKey(provider)).apply() }
    }

    override suspend fun hasApiKey(provider: ProviderType): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { prefs.contains(prefKey(provider)) }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock { prefs.edit().clear().apply() }
    }

    private fun prefKey(provider: ProviderType): String = "enc_key_${provider.name}"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "agentflow_provider_keys"
        private const val PREFS = "agentflow_secure_keys"
    }
}
