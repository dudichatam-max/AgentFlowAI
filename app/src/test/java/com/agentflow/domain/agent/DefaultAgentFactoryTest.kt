package com.agentflow.domain.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultAgentFactoryTest {
    @Test
    fun recommendedRoster() {
        val seeded = DefaultAgentFactory.createAll("proj")
        assertThat(seeded.map { it.agent.name }).containsAtLeast(
            "R&D Orchestrator", "Researcher", "Coder", "Reviewer", "Inspector", "Synthesizer",
        )
        val ids = seeded.flatMap { listOf(it.agent.id) + it.rules.map { r -> r.id } }
        assertThat(ids).containsNoDuplicates()
        seeded.forEach { item ->
            assertThat(item.agent.projectId).isEqualTo("proj")
            assertThat(item.agent.freeOnly).isTrue()
            assertThat(item.agent.id).isNotEmpty()
            assertThat(item.rules.all { it.agentId == item.agent.id }).isTrue()
        }
        val blob = seeded.joinToString { it.agent.toString() + it.rules.toString() }
        assertThat(blob).doesNotContain("AIza")
        assertThat(blob).doesNotContain("gsk_")
        assertThat(blob).doesNotContain("sk-or-")
        assertThat(blob.lowercase()).doesNotContain("apiKey")
    }
}
