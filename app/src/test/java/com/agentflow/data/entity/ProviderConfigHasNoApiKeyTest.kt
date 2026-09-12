package com.agentflow.data.entity

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderConfigHasNoApiKeyTest {
    @Test
    fun roomEntityHasNoApiKeyField() {
        val fields = ProviderConfigEntity::class.java.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("apiKey")
        assertThat(fields).contains("providerId")
        assertThat(fields).contains("freeOnly")
    }
}
