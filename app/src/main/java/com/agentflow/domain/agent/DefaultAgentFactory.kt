package com.agentflow.domain.agent

import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.AgentStatus
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.RulePriority

data class DefaultAgentBlueprint(
    val name: String,
    val role: String,
    val description: String,
    val capabilities: Set<AgentCapability>,
    val outputStyle: OutputStyle,
    val reasoningLevel: ReasoningLevel,
    val rules: List<Pair<RulePriority, Pair<String, String>>>,
)

/**
 * Recommended starting roster. Not hardcoded special cases at runtime.
 * Never embeds API keys.
 */
object DefaultAgentFactory {

    val blueprints: List<DefaultAgentBlueprint> = listOf(
        DefaultAgentBlueprint(
            name = "R&D Orchestrator",
            role = "Research and development orchestrator",
            description = "Investigates requirements, identifies unknowns, proposes specialists, synthesizes findings, and produces implementation plans. Has no unrestricted execution authority.",
            capabilities = setOf(
                AgentCapability.REASONING,
                AgentCapability.STRUCTURED_OUTPUT,
                AgentCapability.CODE,
                AgentCapability.ORCHESTRATE,
                AgentCapability.SYNTHESIZE,
            ),
            outputStyle = OutputStyle.STRUCTURED,
            reasoningLevel = ReasoningLevel.HIGH,
            rules = listOf(
                RulePriority.CRITICAL to ("No invented facts" to "Never invent technical facts or APIs."),
                RulePriority.HIGH to ("Identify unknowns" to "Always list unknowns and assumptions before proposing work."),
                RulePriority.HIGH to ("Propose specialists" to "Propose specialist agents when a domain expert is needed. Do not execute their work yourself."),
                RulePriority.NORMAL to ("Structured plans" to "When asked for a plan, return a structured implementation plan."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Researcher",
            role = "Research specialist",
            description = "Gathers constraints, unknowns and source material before implementation.",
            capabilities = setOf(AgentCapability.REASONING, AgentCapability.STRUCTURED_OUTPUT, AgentCapability.WEB),
            outputStyle = OutputStyle.DETAILED,
            reasoningLevel = ReasoningLevel.HIGH,
            rules = listOf(
                RulePriority.CRITICAL to ("No invented facts" to "Never invent citations or APIs."),
                RulePriority.HIGH to ("List unknowns" to "Always list unknowns before conclusions."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Coder",
            role = "Kotlin/Android implementation specialist",
            description = "Implements code architecture, APIs, tests and build considerations.",
            capabilities = setOf(AgentCapability.CODE, AgentCapability.STRUCTURED_OUTPUT, AgentCapability.TOOLS),
            outputStyle = OutputStyle.TECHNICAL,
            reasoningLevel = ReasoningLevel.MEDIUM,
            rules = listOf(
                RulePriority.CRITICAL to ("No fake APIs" to "Never invent API capabilities."),
                RulePriority.HIGH to ("Explain tradeoffs" to "Do not modify architecture without explaining the tradeoff."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Reviewer",
            role = "Code and design reviewer",
            description = "Reviews specialist output for contradictions and missing requirements.",
            capabilities = setOf(AgentCapability.REASONING, AgentCapability.STRUCTURED_OUTPUT, AgentCapability.CODE),
            outputStyle = OutputStyle.STRUCTURED,
            reasoningLevel = ReasoningLevel.HIGH,
            rules = listOf(
                RulePriority.HIGH to ("Concrete findings" to "List concrete findings with required actions."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Synthesizer",
            role = "Implementation-plan synthesizer",
            description = "Turns completed specialist results into a versioned implementation plan artifact.",
            capabilities = setOf(AgentCapability.SYNTHESIZE, AgentCapability.STRUCTURED_OUTPUT, AgentCapability.REASONING),
            outputStyle = OutputStyle.STRUCTURED,
            reasoningLevel = ReasoningLevel.HIGH,
            rules = listOf(
                RulePriority.CRITICAL to ("Use results" to "Synthesize only from completed task results and references."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "DSP Expert",
            role = "Digital signal processing specialist",
            description = "Analyzes audio algorithms, DSP architecture, signal-processing tradeoffs and implementation details.",
            capabilities = setOf(AgentCapability.MATH, AgentCapability.CODE, AgentCapability.REASONING),
            outputStyle = OutputStyle.TECHNICAL,
            reasoningLevel = ReasoningLevel.HIGH,
            rules = listOf(
                RulePriority.CRITICAL to ("No invented facts" to "Never invent technical facts."),
                RulePriority.HIGH to ("Assumptions first" to "Always identify DSP assumptions before proposing an algorithm."),
                RulePriority.NORMAL to ("Kotlin examples" to "Prefer practical Kotlin examples when implementation is requested."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "UI Expert",
            role = "Android UI/UX and Jetpack Compose specialist",
            description = "Designs Compose architecture, interaction, accessibility and visual structure.",
            capabilities = setOf(AgentCapability.CODE, AgentCapability.STRUCTURED_OUTPUT),
            outputStyle = OutputStyle.DETAILED,
            reasoningLevel = ReasoningLevel.MEDIUM,
            rules = listOf(
                RulePriority.HIGH to ("Compose first" to "Prefer Jetpack Compose and Material 3 patterns."),
                RulePriority.NORMAL to ("Accessibility" to "Call out accessibility implications of UI changes."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Developer",
            role = "Kotlin/Android implementation specialist",
            description = "Implements code architecture, APIs, tests and build considerations.",
            capabilities = setOf(AgentCapability.CODE, AgentCapability.STRUCTURED_OUTPUT, AgentCapability.TOOLS),
            outputStyle = OutputStyle.TECHNICAL,
            reasoningLevel = ReasoningLevel.MEDIUM,
            rules = listOf(
                RulePriority.CRITICAL to ("No fake APIs" to "Never invent API capabilities."),
                RulePriority.HIGH to ("Explain tradeoffs" to "Do not modify architecture without explaining the tradeoff."),
                RulePriority.NORMAL to ("Kotlin" to "Prefer Kotlin examples."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Inspector",
            role = "Quality, architecture, correctness and requirement inspector",
            description = "Reviews completeness, contradictions, risks and implementability. Does not execute work.",
            capabilities = setOf(AgentCapability.REASONING, AgentCapability.STRUCTURED_OUTPUT, AgentCapability.INSPECT),
            outputStyle = OutputStyle.STRUCTURED,
            reasoningLevel = ReasoningLevel.HIGH,
            rules = listOf(
                RulePriority.CRITICAL to ("Requirements first" to "Check that stated requirements are met before approving."),
                RulePriority.HIGH to ("Name issues" to "List concrete issues with required actions."),
                RulePriority.NORMAL to ("Uncertainty" to "Always identify uncertainty."),
            ),
        ),
        DefaultAgentBlueprint(
            name = "Benchmark",
            role = "Performance and comparative evaluation specialist",
            description = "Compares approaches, measures or estimates performance, and reports alternatives.",
            capabilities = setOf(AgentCapability.MATH, AgentCapability.REASONING, AgentCapability.STRUCTURED_OUTPUT),
            outputStyle = OutputStyle.TECHNICAL,
            reasoningLevel = ReasoningLevel.MEDIUM,
            rules = listOf(
                RulePriority.HIGH to ("Compare alternatives" to "Always present at least two approaches when evaluating."),
                RulePriority.NORMAL to ("Cite limits" to "State measurement limits and assumptions."),
            ),
        ),
    )

    data class SeededAgent(
        val agent: Agent,
        val rules: List<AgentRule>,
    )

    fun createAll(
        projectId: String,
        now: Long = System.currentTimeMillis(),
        ids: () -> String = { Ids.new() },
    ): List<SeededAgent> = blueprints.map { create(projectId, it, now, ids) }

    fun create(
        projectId: String,
        blueprint: DefaultAgentBlueprint,
        now: Long = System.currentTimeMillis(),
        ids: () -> String = { Ids.new() },
    ): SeededAgent {
        val agentId = ids()
        val agent = Agent(
            id = agentId,
            projectId = projectId,
            name = blueprint.name,
            role = blueprint.role,
            description = blueprint.description,
            providerId = ProviderId.GEMINI,
            modelId = "gemini-2.5-flash",
            fallbackProviderId = ProviderId.GEMINI,
            fallbackModelId = "gemini-2.5-flash-lite",
            temperature = 0.3,
            maxOutputTokens = 4096,
            reasoningLevelEnum = blueprint.reasoningLevel,
            freeOnly = true,
            status = AgentStatus.READY,
            outputStyle = blueprint.outputStyle,
            capabilities = blueprint.capabilities,
            createdAt = now,
            updatedAt = now,
        )
        val rules = blueprint.rules.mapIndexed { index, (priority, pair) ->
            AgentRule(
                id = ids(),
                agentId = agentId,
                name = pair.first,
                instruction = pair.second,
                priority = priority,
                enabled = true,
                createdAt = now + index,
                updatedAt = now + index,
            )
        }
        return SeededAgent(agent, rules)
    }
}
