package com.agentflow.domain.agent

data class AgentBrainConfig(
    val role: String,
    val outputStyle: OutputStyle = OutputStyle.BALANCED,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.MEDIUM,
    val verbosity: Verbosity = Verbosity.NORMAL,
    val exposeUncertainty: Boolean = true,
    val includeAssumptions: Boolean = true,
    val includeAlternatives: Boolean = true,
)
