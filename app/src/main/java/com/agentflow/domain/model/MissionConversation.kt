package com.agentflow.domain.model
data class MissionConversation(
    val id: String, val missionId: String, val agentId: String,
    val createdAt: Long, val updatedAt: Long,
)
