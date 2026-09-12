package com.agentflow.domain.model
data class MissionEvent(
    val id: String, val missionId: String, val type: MissionEventType, val message: String,
    val relatedTaskId: String? = null, val relatedMessageId: String? = null, val createdAt: Long,
)
