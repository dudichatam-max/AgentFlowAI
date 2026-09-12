package com.agentflow.domain.agent

import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.RulePriority
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleOrderingTest {
    private fun rule(id: String, priority: RulePriority, created: Long) = AgentRule(
        id = id, agentId = "a", name = id, instruction = id,
        priority = priority, createdAt = created, updatedAt = created,
    )

    @Test
    fun priorityThenCreatedThenId() {
        val rules = listOf(
            rule("d", RulePriority.LOW, 1),
            rule("c", RulePriority.NORMAL, 1),
            rule("b", RulePriority.HIGH, 1),
            rule("a", RulePriority.CRITICAL, 1),
            rule("a2", RulePriority.CRITICAL, 2),
            rule("a0", RulePriority.CRITICAL, 1),
        )
        val ordered = rules.sortedForBrain().map { it.id }
        assertThat(ordered).containsExactly("a", "a0", "a2", "b", "c", "d").inOrder()
    }
}
