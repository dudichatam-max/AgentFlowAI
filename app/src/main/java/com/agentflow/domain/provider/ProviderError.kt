package com.agentflow.domain.provider

enum class ProviderErrorType {
    AUTHENTICATION_ERROR,
    AUTHORIZATION_ERROR,
    RATE_LIMITED,
    INVALID_REQUEST,
    MODEL_NOT_FOUND,
    TIMEOUT,
    NETWORK_ERROR,
    SERVER_ERROR,
    INVALID_RESPONSE,
    QUOTA_EXCEEDED,
    FREE_MODEL_REQUIRED,
    UNKNOWN,
}

/**
 * Normalized provider failure. [message] is safe to show; it must never
 * contain an API key or raw Authorization header.
 */
data class ProviderError(
    val provider: ProviderType,
    val type: ProviderErrorType,
    val message: String,
    val retryable: Boolean,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
) {
    companion object {
        fun of(
            provider: ProviderType,
            type: ProviderErrorType,
            message: String,
            httpStatus: Int? = null,
            retryAfterMs: Long? = null,
        ): ProviderError = ProviderError(
            provider = provider,
            type = type,
            message = sanitize(message),
            retryable = type.isRetryable,
            httpStatus = httpStatus,
            retryAfterMs = retryAfterMs,
        )

        private val secretPatterns = listOf(
            Regex("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*\\S+"),
            Regex("(?i)AIza[0-9A-Za-z\\-_]{20,}"),
            Regex("(?i)sk-[0-9A-Za-z]{8,}"),
            Regex("(?i)gsk_[0-9A-Za-z]{8,}"),
        )

        fun sanitize(raw: String): String {
            var out = raw
            secretPatterns.forEach { out = it.replace(out, "[redacted]") }
            return out.take(400)
        }
    }
}

val ProviderErrorType.isRetryable: Boolean
    get() = when (this) {
        ProviderErrorType.TIMEOUT,
        ProviderErrorType.NETWORK_ERROR,
        ProviderErrorType.SERVER_ERROR,
        ProviderErrorType.RATE_LIMITED,
        -> true
        else -> false
    }
