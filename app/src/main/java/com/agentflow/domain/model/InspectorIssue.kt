package com.agentflow.domain.model
data class InspectorIssue(
    val id: String, val reviewId: String, val severity: Severity, val description: String,
    val requiredAction: String, val resolved: Boolean = false, val createdAt: Long,
    val resolvedAt: Long? = null,
)
