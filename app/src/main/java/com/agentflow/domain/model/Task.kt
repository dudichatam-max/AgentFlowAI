package com.agentflow.domain.model
data class Task(
    val id: String, val missionId: String, val parentTaskId: String? = null,
    val createdByType: CreatedByType, val createdById: String, val assignedAgentId: String,
    val title: String, val description: String, val status: TaskStatus = TaskStatus.PENDING,
    val priority: Priority = Priority.NORMAL, val inputMessageId: String? = null,
    val startedAt: Long? = null, val completedAt: Long? = null, val retryCount: Int = 0,
    val createdAt: Long, val updatedAt: Long,
)
