package com.agentflow.domain.inspector

import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.model.Severity
import kotlinx.serialization.Serializable

const val INSPECTOR_CONTRACT_VERSION = 1

enum class ReviewTarget { MISSION, ARTIFACT, TASK_RESULT, IMPLEMENTATION_PLAN }

enum class IssueCategory {
    REQUIREMENT, ARCHITECTURE, CODE, UI, DATA, SECURITY, PERFORMANCE, TESTING, DOCUMENTATION, EVIDENCE, OTHER,
}

@Serializable
data class InspectorReviewEnvelope(
    val version: Int,
    val decision: String,
    val summary: String,
    val reason: String,
    val confidence: Double? = null,
    val severity: String = "NONE",
    val issues: List<InspectorIssueDto> = emptyList(),
)

@Serializable
data class InspectorIssueDto(
    val severity: String,
    val description: String,
    val requiredAction: String,
    val category: String = "OTHER",
)

data class ValidatedInspectorReview(
    val decision: ReviewStatus,
    val summary: String,
    val reason: String,
    val confidence: Double,
    val severity: Severity,
    val issues: List<ValidatedIssue>,
)

data class ValidatedIssue(
    val severity: Severity,
    val description: String,
    val requiredAction: String,
    val category: IssueCategory,
)
