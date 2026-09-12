package com.agentflow.domain.mission

import com.agentflow.domain.model.Mission

/**
 * Produces untrusted AgentDecisionEnvelope JSON. DecisionGateway decides.
 */
fun interface MissionPlanner {
    suspend fun plan(mission: Mission): String
}
