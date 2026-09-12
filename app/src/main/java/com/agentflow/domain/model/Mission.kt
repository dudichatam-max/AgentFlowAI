package com.agentflow.domain.model
data class Mission(
    val id: String, val projectId: String, val title: String, val description: String,
    val status: MissionStatus = MissionStatus.CREATED, val createdAt: Long, val updatedAt: Long,
    val startedAt: Long? = null, val completedAt: Long? = null, val createdBy: String = "user",
)
