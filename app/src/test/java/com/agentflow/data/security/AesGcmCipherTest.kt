package com.agentflow.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class AesGcmCipherTest {
    private fun aesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun saveLoadReturnsOriginal() {
        val key = aesKey()
        val secret = "test-provider-key-not-real"
        val packed = AesGcmCipher.encrypt(secret.toByteArray(), key)
        assertThat(packed).startsWith(AesGcmCipher.VERSION_PREFIX)
        assertThat(packed).doesNotContain(secret)
        assertThat(AesGcmCipher.decrypt(packed, key).toString(Charsets.UTF_8)).isEqualTo(secret)
    }

    @Test
    fun eachEncryptionUsesFreshIv() {
        val key = aesKey()
        val plain = "same-plaintext".toByteArray()
        val first = AesGcmCipher.parse(AesGcmCipher.encrypt(plain, key))
        val second = AesGcmCipher.parse(AesGcmCipher.encrypt(plain, key))
        assertThat(first.iv.toList()).isNotEqualTo(second.iv.toList())
    }

    @Test
    fun samePlaintextDoesNotProduceIdenticalPayloads() {
        val key = aesKey()
        val plain = "same-plaintext".toByteArray()
        val a = AesGcmCipher.encrypt(plain, key)
        val b = AesGcmCipher.encrypt(plain, key)
        assertThat(a).isNotEqualTo(b)
        assertThat(AesGcmCipher.decrypt(a, key)).isEqualTo(plain)
        assertThat(AesGcmCipher.decrypt(b, key)).isEqualTo(plain)
    }

    @Test
    fun decryptWithStoredIvSucceeds() {
        val key = aesKey()
        val packed = AesGcmCipher.encrypt("hello".toByteArray(), key)
        val parsed = AesGcmCipher.parse(packed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AesGcmCipher.TAG_BITS, parsed.iv))
        assertThat(cipher.doFinal(parsed.ciphertext).toString(Charsets.UTF_8)).isEqualTo("hello")
    }

    @Test
    fun corruptedCiphertextFailsSafely() {
        val key = aesKey()
        val parsed = AesGcmCipher.parse(AesGcmCipher.encrypt("hello".toByteArray(), key))
        parsed.ciphertext[0] = (parsed.ciphertext[0].toInt() xor 0xFF).toByte()
        val broken = AesGcmCipher.encodeV1(parsed.iv, parsed.ciphertext)
        assertThrows(Exception::class.java) { AesGcmCipher.decrypt(broken, key) }
    }

    @Test
    fun corruptedIvFailsSafely() {
        val key = aesKey()
        val parsed = AesGcmCipher.parse(AesGcmCipher.encrypt("hello".toByteArray(), key))
        parsed.iv[0] = (parsed.iv[0].toInt() xor 0xFF).toByte()
        val broken = AesGcmCipher.encodeV1(parsed.iv, parsed.ciphertext)
        assertThrows(Exception::class.java) { AesGcmCipher.decrypt(broken, key) }
    }

    @Test
    fun truncatedPayloadFailsSafely() {
        val key = aesKey()
        assertThrows(Exception::class.java) { AesGcmCipher.decrypt("AFKS1AAAA", key) }
        assertThrows(Exception::class.java) { AesGcmCipher.decrypt("AAAA", key) }
    }

    @Test
    fun legacyPayloadStillDecrypts() {
        val key = aesKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal("legacy-secret".toByteArray())
        val legacy = AesGcmCipher.encodeLegacy(iv, ciphertext)
        assertThat(legacy.startsWith(AesGcmCipher.VERSION_PREFIX)).isFalse()
        assertThat(AesGcmCipher.decrypt(legacy, key).toString(Charsets.UTF_8)).isEqualTo("legacy-secret")
    }

    @Test
    fun encryptDoesNotAcceptCallerIvPath() {
        // Production encrypt() must call cipher.init(ENCRYPT_MODE, key) with no spec.
        val key = aesKey()
        val packed = AesGcmCipher.encrypt("x".toByteArray(), key)
        val parsed = AesGcmCipher.parse(packed)
        assertThat(parsed.version).isEqualTo(AesGcmCipher.VERSION_PREFIX)
        assertThat(parsed.iv.size).isAtLeast(8)
    }
}
