package com.agentflow.domain.reference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecretDenylistTest {
    @Test
    fun blocksSecretsAllowsSource() {
        assertThat(SecretDenylist.isDenied(".env")).isTrue()
        assertThat(SecretDenylist.isDenied("id_rsa")).isTrue()
        assertThat(SecretDenylist.isDenied("release.jks")).isTrue()
        assertThat(SecretDenylist.isDenied("local.properties")).isTrue()
        assertThat(SecretDenylist.isDenied("service.credentials")).isTrue()
        assertThat(SecretDenylist.isDenied("README.md")).isFalse()
        assertThat(SecretDenylist.isDenied("AudioProcessor.kt")).isFalse()
        assertThat(SecretDenylist.looksLikeSecretContent("API_KEY=abcdefghijklmnop")).isTrue()
        assertThat(SecretDenylist.looksLikeSecretContent("line 1\nline 9000\nPASSWORD=abcdefghijklmnop")).isTrue()
    }
}
