package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent

/**
 * Architectural identity is capability-based, never display name.
 */
object AgentDuties {
    fun orchestrator(agents: List<Agent>): Agent? =
        agents.firstOrNull { AgentCapability.ORCHESTRATE in it.capabilities && it.isEnabled }

    fun inspector(agents: List<Agent>): Agent? =
        agents.firstOrNull { AgentCapability.INSPECT in it.capabilities && it.isEnabled }

    fun synthesizer(agents: List<Agent>): Agent? =
        agents.firstOrNull { AgentCapability.SYNTHESIZE in it.capabilities && it.isEnabled }
            ?: orchestrator(agents)
}
