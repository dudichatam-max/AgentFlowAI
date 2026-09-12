package com.agentflow.domain.inspector

import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.model.Severity

data class InspectorApprovalPolicy(
    val allowLowSeverityOpenIssues: Boolean = true,
    val allowMediumSeverityOpenIssues: Boolean = false,
    val requireAllHighResolved: Boolean = true,
    val requireAllCriticalResolved: Boolean = true,
) {
    fun canApprove(decision: ReviewStatus, issues: List<ValidatedIssue>): Boolean {
        if (decision != ReviewStatus.APPROVED) return false
        val open = issues
        if (requireAllCriticalResolved && open.any { it.severity == Severity.CRITICAL }) return false
        if (requireAllHighResolved && open.any { it.severity == Severity.HIGH }) return false
        if (!allowMediumSeverityOpenIssues && open.any { it.severity == Severity.MEDIUM }) return false
        if (!allowLowSeverityOpenIssues && open.any { it.severity == Severity.LOW }) return false
        return true
    }

    fun blocking(issues: List<ValidatedIssue>): List<ValidatedIssue> = issues.filter {
        it.severity == Severity.CRITICAL ||
            it.severity == Severity.HIGH ||
            (it.severity == Severity.MEDIUM && !allowMediumSeverityOpenIssues)
    }
}
