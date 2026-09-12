package com.agentflow.domain.mission

data class ContinueOutcome(
    val exhaustedHops: Boolean = false,
    val workRemaining: Boolean = false,
)
