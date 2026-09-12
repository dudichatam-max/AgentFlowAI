package com.agentflow.data.security

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM helper used by the Keystore-backed store.
 *
 * Encrypt never supplies a caller-generated IV. Android Keystore keys are
 * created with randomizedEncryptionRequired=true and reject ENCRYPT_MODE
 * initializations that include a GCMParameterSpec.
 *
 * Payload formats:
 *   AFKS1 + Base64(ivLen:1 | iv | ciphertext)   current
 *   Base64(iv[12] | ciphertext)                 legacy, decrypt only
 */
object AesGcmCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val TAG_BITS = 128
    const val VERSION_PREFIX = "AFKS1"
    private const val LEGACY_IV_BYTES = 12

    data class Packed(
        val version: String,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    fun encrypt(plain: ByteArray, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv ?: throw IllegalStateException("Cipher did not provide an IV")
        val ciphertext = cipher.doFinal(plain)
        return encodeV1(iv, ciphertext)
    }

    fun decrypt(payload: String, key: SecretKey): ByteArray {
        val packed = parse(payload)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, packed.iv))
        return cipher.doFinal(packed.ciphertext)
    }

    fun parse(payload: String): Packed {
        if (payload.startsWith(VERSION_PREFIX)) {
            val raw = Base64.getDecoder().decode(payload.removePrefix(VERSION_PREFIX))
            if (raw.isEmpty()) throw IllegalStateException("Corrupted encrypted payload")
            val ivLen = raw[0].toInt() and 0xFF
            if (ivLen < 8 || ivLen > 16 || raw.size <= 1 + ivLen) {
                throw IllegalStateException("Corrupted encrypted payload")
            }
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val ciphertext = raw.copyOfRange(1 + ivLen, raw.size)
            return Packed(VERSION_PREFIX, iv, ciphertext)
        }
        val raw = Base64.getDecoder().decode(payload)
        if (raw.size <= LEGACY_IV_BYTES) throw IllegalStateException("Corrupted encrypted payload")
        return Packed(
            version = "legacy",
            iv = raw.copyOfRange(0, LEGACY_IV_BYTES),
            ciphertext = raw.copyOfRange(LEGACY_IV_BYTES, raw.size),
        )
    }

    internal fun encodeV1(iv: ByteArray, ciphertext: ByteArray): String {
        val packed = ByteArray(1 + iv.size + ciphertext.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(ciphertext, 0, packed, 1 + iv.size, ciphertext.size)
        return VERSION_PREFIX + Base64.getEncoder().encodeToString(packed)
    }

    internal fun encodeLegacy(iv: ByteArray, ciphertext: ByteArray): String {
        val packed = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(ciphertext, 0, packed, iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(packed)
    }
}
