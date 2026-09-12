package com.agentflow.domain.model
data class AgentRule(
    val id: String, val agentId: String, val name: String, val instruction: String,
    val priority: RulePriority = RulePriority.NORMAL, val enabled: Boolean = true,
    val createdAt: Long, val updatedAt: Long,
)
