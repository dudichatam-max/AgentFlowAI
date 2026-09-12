package com.agentflow.domain.model

data class InspectorReview(
    val id: String,
    val missionId: String,
    val taskId: String?,
    val inspectorAgentId: String,
    val status: ReviewStatus = ReviewStatus.PENDING,
    val summary: String,
    val reason: String,
    val severity: Severity = Severity.NONE,
    val createdAt: Long,
    val rejectedArtifactId: String? = null,
    val rejectedArtifactVersion: Int? = null,
)
