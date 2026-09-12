package com.agentflow.domain.agent

object AgentLimits {
    const val MAX_NAME_LENGTH = 80
    const val MAX_ROLE_LENGTH = 200
    const val MAX_DESCRIPTION_LENGTH = 4_000
    const val MAX_RULE_NAME_LENGTH = 80
    const val MAX_RULE_INSTRUCTION_LENGTH = 4_000
    const val MIN_TEMPERATURE = 0.0
    const val MAX_TEMPERATURE = 2.0
    const val MIN_OUTPUT_TOKENS = 1
    const val MAX_OUTPUT_TOKENS = 128_000
}
