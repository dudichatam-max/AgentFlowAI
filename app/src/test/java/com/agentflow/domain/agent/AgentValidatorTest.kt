package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentStatus
import com.agentflow.domain.model.ProviderId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentValidatorTest {

    private fun valid() = Agent(
        id = "a1",
        projectId = "p1",
        name = "DSP Expert",
        role = "Digital Signal Processing specialist",
        description = "Analyzes audio algorithms.",
        providerId = ProviderId.GROQ,
        modelId = "llama-3.1-8b-instant",
        fallbackProviderId = ProviderId.GEMINI,
        fallbackModelId = "gemini-2.5-flash",
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun validAgent() {
        assertThat(AgentValidator.validate(valid(), projectExists = { it == "p1" })).isEmpty()
    }

    @Test
    fun blankName() {
        val errors = AgentValidator.validate(valid().copy(name = "  "), projectExists = { true })
        assertThat(errors.any { it is AgentValidationError.BlankName }).isTrue()
    }

    @Test
    fun blankRole() {
        val errors = AgentValidator.validate(valid().copy(role = ""), projectExists = { true })
        assertThat(errors.any { it is AgentValidationError.BlankRole }).isTrue()
    }

    @Test
    fun invalidTemperature() {
        val errors = AgentValidator.validate(valid().copy(temperature = 9.0), projectExists = { true })
        assertThat(errors.any { it is AgentValidationError.InvalidTemperature }).isTrue()
    }

    @Test
    fun invalidTokens() {
        val errors = AgentValidator.validate(valid().copy(maxOutputTokens = 0), projectExists = { true })
        assertThat(errors.any { it is AgentValidationError.InvalidMaxTokens }).isTrue()
    }

    @Test
    fun fallbackEqualsPrimary() {
        val errors = AgentValidator.validate(
            valid().copy(fallbackProviderId = ProviderId.GROQ, fallbackModelId = "llama-3.1-8b-instant"),
            projectExists = { true },
        )
        assertThat(errors.any { it is AgentValidationError.FallbackEqualsPrimary }).isTrue()
    }

    @Test
    fun unknownProject() {
        val errors = AgentValidator.validate(valid(), projectExists = { false })
        assertThat(errors.any { it is AgentValidationError.UnknownProject }).isTrue()
    }

    @Test
    fun disabledIsNotReadyButStillValid() {
        assertThat(AgentValidator.validate(valid().copy(status = AgentStatus.DISABLED), projectExists = { true })).isEmpty()
    }
}
