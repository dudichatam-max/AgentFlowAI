package com.agentflow.domain.model
data class TaskDependency(
    val id: String, val missionId: String, val taskId: String, val dependsOnTaskId: String,
    val type: DependencyType = DependencyType.REQUIRED, val createdAt: Long,
)
