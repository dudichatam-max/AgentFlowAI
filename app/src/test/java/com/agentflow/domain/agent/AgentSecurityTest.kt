package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.ProviderId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentSecurityTest {
    @Test
    fun domainHasNoKeyStoreOrApiKeyField() {
        val fields = Agent::class.java.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("apiKey")
        assertThat(fields).doesNotContain("keyStore")
        val names = Agent::class.java.methods.map { it.name }
        assertThat(names.none { it.contains("ApiKey", ignoreCase = true) }).isTrue()
    }

    @Test
    fun promptNeverInjectsCredentials() {
        val agent = Agent(
            id = "a",
            projectId = "p",
            name = "R&D",
            role = "orchestrator",
            description = "plans work",
            providerId = ProviderId.GROQ,
            modelId = "llama-3.1-8b-instant",
            createdAt = 1,
            updatedAt = 1,
        )
        val prompt = AgentPromptBuilder.build(agent.toBrain(emptyList()))
        assertThat(prompt).doesNotContain("SecureApiKeyStore")
        assertThat(prompt).doesNotContain("Authorization")
        assertThat(prompt).doesNotContain("x-goog-api-key")
    }
}
