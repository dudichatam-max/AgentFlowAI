package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.AgentStatus
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.RulePriority
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentCloneResetTest {
    @Test
    fun cloneCopiesConfigNotSecretsOrHistory() {
        val catalog = InMemoryAgentCatalog().apply { addProject("p1") }
        val source = Agent(
            id = "src",
            projectId = "p1",
            name = "DSP Expert",
            role = "Digital Signal Processing specialist",
            description = "Analyzes audio algorithms and DSP architecture.",
            providerId = ProviderId.GROQ,
            modelId = "openai/gpt-oss-20b",
            fallbackProviderId = ProviderId.GEMINI,
            fallbackModelId = "gemini-2.5-flash",
            freeOnly = true,
            reasoningLevelEnum = ReasoningLevel.HIGH,
            outputStyle = OutputStyle.TECHNICAL,
            status = AgentStatus.THINKING,
            capabilities = setOf(AgentCapability.MATH, AgentCapability.CODE),
            createdAt = 10,
            updatedAt = 10,
        )
        catalog.createAgent(
            source,
            listOf(
                AgentRule("r1", "src", "Facts", "Never invent technical facts.", RulePriority.CRITICAL, true, 1, 1),
            ),
        )
        val clone = catalog.clone("src", "Advanced DSP Expert", now = 99)
        assertThat(clone.id).isNotEqualTo("src")
        assertThat(clone.name).isEqualTo("Advanced DSP Expert")
        assertThat(clone.role).isEqualTo(source.role)
        assertThat(clone.modelId).isEqualTo(source.modelId)
        assertThat(clone.status).isEqualTo(AgentStatus.READY)
        assertThat(clone.capabilities).isEqualTo(source.capabilities)
        val clonedRules = catalog.getRules(clone.id)
        assertThat(clonedRules).hasSize(1)
        assertThat(clonedRules.single().id).isNotEqualTo("r1")
        assertThat(clonedRules.single().instruction).contains("Never invent")
        assertThat(catalog.getAgent("src")).isNotNull()
        assertThat(catalog.getRules("src")).hasSize(1)
    }

    @Test
    fun resetClearsRulesKeepsIdentity() {
        val catalog = InMemoryAgentCatalog().apply { addProject("p1") }
        catalog.createAgent(
            Agent(
                id = "a",
                projectId = "p1",
                name = "Dev",
                role = "impl",
                description = "d",
                providerId = ProviderId.GROQ,
                modelId = "openai/gpt-oss-20b",
                outputStyle = OutputStyle.TECHNICAL,
                createdAt = 1,
                updatedAt = 1,
            ),
            listOf(AgentRule("r", "a", "n", "i", RulePriority.HIGH, true, 1, 1)),
        )
        catalog.reset("a")
        assertThat(catalog.getAgent("a")!!.name).isEqualTo("Dev")
        assertThat(catalog.getAgent("a")!!.outputStyle).isEqualTo(OutputStyle.BALANCED)
        assertThat(catalog.getRules("a")).isEmpty()
    }
}
