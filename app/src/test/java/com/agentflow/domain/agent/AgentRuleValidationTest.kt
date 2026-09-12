package com.agentflow.domain.agent

import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.RulePriority
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgentRuleValidationTest {
    private fun rule(name: String = "n", instruction: String = "do x") = AgentRule(
        id = "r1", agentId = "a1", name = name, instruction = instruction,
        priority = RulePriority.NORMAL, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun valid() {
        assertThat(AgentValidator.validateRule(rule())).isEmpty()
    }

    @Test
    fun blankName() {
        assertThat(AgentValidator.validateRule(rule(name = " "))).isNotEmpty()
    }

    @Test
    fun blankInstruction() {
        assertThat(AgentValidator.validateRule(rule(instruction = ""))).isNotEmpty()
    }

    @Test
    fun tooLongInstruction() {
        val huge = "x".repeat(AgentLimits.MAX_RULE_INSTRUCTION_LENGTH + 1)
        assertThat(AgentValidator.validateRule(rule(instruction = huge))).isNotEmpty()
    }
}
