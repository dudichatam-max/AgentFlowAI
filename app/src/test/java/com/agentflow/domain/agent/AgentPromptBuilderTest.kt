package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.RulePriority
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentPromptBuilderTest {

    @Test
    fun verificationScenario() {
        val agent = Agent(
            id = "dsp",
            projectId = "p1",
            name = "DSP Expert",
            role = "Digital Signal Processing specialist",
            description = "Analyzes audio algorithms and DSP architecture.",
            providerId = ProviderId.GROQ,
            modelId = "llama-3.1-8b-instant",
            fallbackProviderId = ProviderId.GEMINI,
            fallbackModelId = "gemini-2.5-flash",
            freeOnly = true,
            reasoningLevelEnum = ReasoningLevel.HIGH,
            outputStyle = OutputStyle.TECHNICAL,
            createdAt = 1,
            updatedAt = 1,
        )
        val rules = listOf(
            AgentRule("r3", "dsp", "Kotlin", "Prefer practical Kotlin examples when implementation examples are requested.", RulePriority.NORMAL, true, 3, 3),
            AgentRule("r2", "dsp", "Assumptions", "Always identify assumptions.", RulePriority.HIGH, true, 2, 2),
            AgentRule("r1", "dsp", "Facts", "Never invent technical facts.", RulePriority.CRITICAL, true, 1, 1),
            AgentRule("r0", "dsp", "Hidden", "This must not appear.", RulePriority.CRITICAL, enabled = false, 0, 0),
        )
        val prompt = AgentPromptBuilder.build(agent.toBrain(rules))
        assertThat(prompt).contains("Role: Digital Signal Processing specialist")
        assertThat(prompt).contains("Analyzes audio algorithms")
        assertThat(prompt).contains("[CRITICAL] Facts")
        assertThat(prompt).contains("[HIGH] Assumptions")
        assertThat(prompt).contains("[NORMAL] Kotlin")
        assertThat(prompt.indexOf("[CRITICAL]")).isLessThan(prompt.indexOf("[HIGH]"))
        assertThat(prompt.indexOf("[HIGH]")).isLessThan(prompt.indexOf("[NORMAL]"))
        assertThat(prompt).doesNotContain("This must not appear")
        assertThat(prompt).contains("Style: TECHNICAL")
        assertThat(prompt).contains("Requested level: HIGH")
        assertThat(prompt).doesNotContain("api")
        assertThat(prompt).doesNotContain("gsk_")
        assertThat(prompt).doesNotContain("AIza")
        assertThat(prompt).doesNotContain("GeminiProvider")
        val again = AgentPromptBuilder.build(agent.toBrain(rules))
        assertThat(again).isEqualTo(prompt)
    }
}
