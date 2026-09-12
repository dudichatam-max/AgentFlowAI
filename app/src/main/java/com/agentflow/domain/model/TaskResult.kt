package com.agentflow.domain.model
data class TaskResult(
    val id: String, val taskId: String, val missionId: String, val content: String,
    val contractVersion: Int = 1, val confidence: Double? = null, val createdAt: Long,
)
