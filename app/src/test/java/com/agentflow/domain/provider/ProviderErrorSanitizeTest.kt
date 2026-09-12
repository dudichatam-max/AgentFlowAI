package com.agentflow.domain.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderErrorSanitizeTest {
    @Test
    fun stripsKeysFromMessages() {
        val error = ProviderError.of(
            ProviderType.GEMINI,
            ProviderErrorType.AUTHENTICATION_ERROR,
            "Authorization: Bearer gsk_abcdefghijk failed for AIzaSyDummyKeyValue1234567890",
        )
        assertThat(error.message).doesNotContain("gsk_")
        assertThat(error.message).doesNotContain("AIza")
        assertThat(error.message).contains("[redacted]")
    }
}
