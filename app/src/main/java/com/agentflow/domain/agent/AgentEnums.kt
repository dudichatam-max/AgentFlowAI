package com.agentflow.domain.agent

import com.agentflow.domain.model.RulePriority

enum class OutputStyle { CONCISE, BALANCED, DETAILED, TECHNICAL, STRUCTURED }

enum class ReasoningLevel { LOW, MEDIUM, HIGH }

enum class Verbosity { LOW, NORMAL, HIGH }

/**
 * Intent only. A capability does not grant execution permission.
 * PolicyEngine (later phases) remains authoritative.
 */
enum class AgentCapability {
    CODE,
    MATH,
    WEB,
    FILES,
    VISION,
    STRUCTURED_OUTPUT,
    TOOLS,
    REASONING,
    ORCHESTRATE,
    INSPECT,
    SYNTHESIZE,
}

fun RulePriority.rank(): Int = when (this) {
    RulePriority.CRITICAL -> 40
    RulePriority.HIGH -> 30
    RulePriority.NORMAL -> 20
    RulePriority.LOW -> 10
}
