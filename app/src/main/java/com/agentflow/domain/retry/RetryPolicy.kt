package com.agentflow.domain.retry

import com.agentflow.domain.provider.ProviderError
import com.agentflow.domain.provider.ProviderErrorType
import kotlin.math.min
import kotlin.random.Random

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 4_000,
    val multiplier: Double = 2.0,
    val jitterFraction: Double = 0.2,
) {
    init {
        require(maxAttempts >= 1)
        require(initialDelayMs >= 0)
        require(maxDelayMs >= initialDelayMs)
        require(multiplier >= 1.0)
        require(jitterFraction in 0.0..1.0)
    }

    fun shouldRetry(attempt: Int, error: ProviderError): Boolean {
        if (attempt >= maxAttempts) return false
        return error.retryable && error.type.isRetryableForPolicy()
    }

    fun delayMs(attempt: Int, error: ProviderError, random: Random = Random.Default): Long {
        error.retryAfterMs?.let { return min(it, maxDelayMs) }
        val exp = initialDelayMs * Math.pow(multiplier, (attempt - 1).coerceAtLeast(0).toDouble())
        val capped = min(exp.toLong(), maxDelayMs)
        val jitter = (capped * jitterFraction * random.nextDouble()).toLong()
        return capped + jitter
    }
}

private fun ProviderErrorType.isRetryableForPolicy(): Boolean = when (this) {
    ProviderErrorType.TIMEOUT,
    ProviderErrorType.NETWORK_ERROR,
    ProviderErrorType.SERVER_ERROR,
    ProviderErrorType.RATE_LIMITED,
    -> true
    else -> false
}
