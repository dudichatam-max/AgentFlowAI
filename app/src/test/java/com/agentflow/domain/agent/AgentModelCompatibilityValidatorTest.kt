package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.provider.CatalogModel
import com.agentflow.domain.provider.ProviderModelCatalog
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.provider.toProviderType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentModelCompatibilityValidatorTest {

    private fun agent(vararg caps: AgentCapability) = Agent(
        id = "a",
        projectId = "p",
        name = "A",
        role = "r",
        description = "d",
        providerId = ProviderId.GROQ,
        modelId = "llama-3.1-8b-instant",
        capabilities = caps.toSet(),
        createdAt = 1,
        updatedAt = 1,
    )

    private val groqFree = CatalogModel(
        id = "llama-3.1-8b-instant",
        displayName = "Llama",
        provider = ProviderType.GROQ,
        isFree = true,
        structuredOutput = true,
        vision = false,
        tools = false,
        reasoning = true,
        code = true,
    )

    @Test
    fun compatibleModelAccepted() {
        val result = AgentModelCompatibilityValidator.evaluate(
            agent(AgentCapability.CODE, AgentCapability.REASONING, AgentCapability.STRUCTURED_OUTPUT),
            groqFree,
        )
        assertThat(result.accepted).isTrue()
    }

    @Test
    fun visionRejected() {
        val result = AgentModelCompatibilityValidator.evaluate(agent(AgentCapability.VISION), groqFree)
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("VISION")
    }

    @Test
    fun structuredOutputRejected() {
        val result = AgentModelCompatibilityValidator.evaluate(
            agent(AgentCapability.STRUCTURED_OUTPUT),
            groqFree.copy(structuredOutput = false),
        )
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("STRUCTURED_OUTPUT")
    }

    @Test
    fun codeRejected() {
        val result = AgentModelCompatibilityValidator.evaluate(
            agent(AgentCapability.CODE),
            groqFree.copy(code = false),
        )
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("CODE")
    }

    @Test
    fun freeOnlyRejectsPaid() {
        val result = AgentModelCompatibilityValidator.evaluate(
            agent(AgentCapability.REASONING),
            groqFree.copy(id = "paid-model", isFree = false),
            freeOnly = true,
        )
        assertThat(result.accepted).isFalse()
    }

    @Test
    fun defaultsPassValidator() {
        DefaultAgentFactory.createAll("p").forEach { seeded ->
            val provider = seeded.agent.providerId.toProviderType()
            val model = ProviderModelCatalog.find(provider, seeded.agent.modelId)
                ?: ProviderModelCatalog.defaults().first { it.provider == provider }
            val result = AgentModelCompatibilityValidator.evaluate(seeded.agent, model)
            assertThat(result.reason).isEqualTo("compatible")
            assertThat(result.accepted).isTrue()
        }
    }

    @Test
    fun invalidConfigDoesNotChangeOriginal() {
        val original = agent(AgentCapability.VISION)
        val previous = original.copy()
        val result = AgentModelCompatibilityValidator.evaluate(original, groqFree)
        assertThat(result.accepted).isFalse()
        assertThat(original.modelId).isEqualTo(previous.modelId)
        assertThat(original.providerId).isEqualTo(previous.providerId)
    }
}
