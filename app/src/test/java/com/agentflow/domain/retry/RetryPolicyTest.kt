package com.agentflow.domain.retry

import com.agentflow.domain.provider.ProviderError
import com.agentflow.domain.provider.ProviderErrorType
import com.agentflow.domain.provider.ProviderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class RetryPolicyTest {
    private val policy = RetryPolicy(maxAttempts = 3, initialDelayMs = 500, maxDelayMs = 4000, multiplier = 2.0, jitterFraction = 0.0)

    private fun err(type: ProviderErrorType, retryAfter: Long? = null) = ProviderError.of(
        ProviderType.GROQ, type, "x", retryAfterMs = retryAfter,
    )

    @Test
    fun retriesRetryableUntilMax() {
        val error = err(ProviderErrorType.RATE_LIMITED)
        assertThat(policy.shouldRetry(1, error)).isTrue()
        assertThat(policy.shouldRetry(2, error)).isTrue()
        assertThat(policy.shouldRetry(3, error)).isFalse()
    }

    @Test
    fun doesNotRetryAuth() {
        assertThat(policy.shouldRetry(1, err(ProviderErrorType.AUTHENTICATION_ERROR))).isFalse()
        assertThat(policy.shouldRetry(1, err(ProviderErrorType.INVALID_REQUEST))).isFalse()
        assertThat(policy.shouldRetry(1, err(ProviderErrorType.FREE_MODEL_REQUIRED))).isFalse()
        assertThat(policy.shouldRetry(1, err(ProviderErrorType.MODEL_NOT_FOUND))).isFalse()
    }

    @Test
    fun backoffBounded() {
        val error = err(ProviderErrorType.SERVER_ERROR)
        val d1 = policy.delayMs(1, error, Random(0))
        val d2 = policy.delayMs(2, error, Random(0))
        val d3 = policy.delayMs(3, error, Random(0))
        assertThat(d1).isEqualTo(500)
        assertThat(d2).isEqualTo(1000)
        assertThat(d3).isEqualTo(2000)
        val huge = policy.delayMs(20, error, Random(0))
        assertThat(huge).isAtMost(4000)
    }

    @Test
    fun respectsRetryAfter() {
        val error = err(ProviderErrorType.RATE_LIMITED, retryAfter = 1500)
        assertThat(policy.delayMs(1, error)).isEqualTo(1500)
    }
}
