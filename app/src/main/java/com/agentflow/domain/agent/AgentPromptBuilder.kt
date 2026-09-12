package com.agentflow.domain.agent

/**
 * Builds the Agent behavioral system instruction.
 * Does not include API keys, mission files, or conversation history.
 * Application PolicyEngine always outranks Agent rules.
 */
object AgentPromptBuilder {

    fun build(brain: AgentBrain): String = buildString {
        appendLine("IDENTITY")
        appendLine("Name: ${brain.name}")
        appendLine("Role: ${brain.role}")
        if (brain.description.isNotBlank()) {
            appendLine("Description: ${brain.description}")
        }
        appendLine()
        appendLine("BEHAVIOR")
        val rules = brain.enabledRules
        if (rules.isEmpty()) {
            appendLine("- (no enabled rules)")
        } else {
            rules.forEach { rule ->
                appendLine("- [${rule.priority.name}] ${rule.name}: ${rule.instruction}")
            }
        }
        appendLine()
        appendLine("OUTPUT")
        appendLine("Style: ${brain.config.outputStyle.name}")
        appendLine("Verbosity: ${brain.config.verbosity.name}")
        appendLine("Expose uncertainty: ${brain.config.exposeUncertainty}")
        appendLine("Include assumptions: ${brain.config.includeAssumptions}")
        appendLine("Include alternatives: ${brain.config.includeAlternatives}")
        appendLine()
        appendLine("REASONING")
        appendLine("Requested level: ${brain.config.reasoningLevel.name}")
        appendLine()
        appendLine("CONSTRAINTS")
        appendLine("Free-only: ${brain.model.freeOnly}")
        appendLine("Capabilities (intent only, not permissions): ${formatCaps(brain.capabilities)}")
        appendLine("Agent rules are behavioral. Application policy always takes precedence.")
        appendLine("Do not claim hidden system access. Do not invent credentials or API keys.")
    }.trimEnd()

    private fun formatCaps(caps: Set<AgentCapability>): String =
        if (caps.isEmpty()) "none" else caps.sortedBy { it.name }.joinToString(",") { it.name }
}
