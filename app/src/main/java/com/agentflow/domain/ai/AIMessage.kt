package com.agentflow.domain.ai

data class AIMessage(
    val role: AIMessageRole,
    val content: String,
)
