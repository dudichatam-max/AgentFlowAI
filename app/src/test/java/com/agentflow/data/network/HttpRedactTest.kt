package com.agentflow.data.network

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HttpRedactTest {
    @Test
    fun redactsAuthorization() {
        val raw = "Authorization: Bearer gsk_live_secret\nx-goog-api-key: AIzaSomethingLongEnough12"
        val redacted = HttpClientFactory.redact(raw)
        assertThat(redacted).doesNotContain("gsk_live_secret")
        assertThat(redacted).doesNotContain("AIzaSomethingLongEnough12")
        assertThat(redacted).contains("[redacted]")
    }
}
