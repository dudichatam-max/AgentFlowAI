package com.agentflow.domain.inspector

import com.agentflow.domain.contract.ContractException
import com.agentflow.domain.contract.ContractJson
import com.agentflow.domain.contract.JsonExtractor
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.model.Severity

object InspectorReviewParser {
    fun parse(raw: String): InspectorReviewEnvelope {
        val json = JsonExtractor.extract(raw)
        return try {
            ContractJson.strict.decodeFromString(InspectorReviewEnvelope.serializer(), json)
        } catch (e: Exception) {
            throw ContractException("Malformed inspector JSON: ${e.message}")
        }
    }
}

object InspectorReviewValidator {
    fun validate(envelope: InspectorReviewEnvelope): ValidatedInspectorReview {
        if (envelope.version != INSPECTOR_CONTRACT_VERSION) {
            throw ContractException("Unsupported inspector version ${envelope.version}")
        }
        val decision = parseDecision(envelope.decision)
        if (envelope.summary.isBlank()) throw ContractException("summary required")
        if (envelope.reason.isBlank()) throw ContractException("reason required")
        if (envelope.summary.length > InspectorReviewLimits.MAX_SUMMARY) throw ContractException("summary too long")
        if (envelope.reason.length > InspectorReviewLimits.MAX_REASON) throw ContractException("reason too long")
        val confidence = envelope.confidence ?: 0.5
        if (confidence !in 0.0..1.0) throw ContractException("confidence out of range")
        val severity = parseSeverity(envelope.severity)
        if (envelope.issues.size > InspectorReviewLimits.MAX_ISSUE_COUNT) throw ContractException("too many issues")
        val issues = envelope.issues.map { issue ->
            if (issue.description.isBlank() || issue.requiredAction.isBlank()) {
                throw ContractException("issue fields required")
            }
            if (issue.description.length > InspectorReviewLimits.MAX_ISSUE_DESCRIPTION) {
                throw ContractException("issue description too long")
            }
            if (issue.requiredAction.length > InspectorReviewLimits.MAX_REQUIRED_ACTION) {
                throw ContractException("requiredAction too long")
            }
            ValidatedIssue(
                severity = parseSeverity(issue.severity),
                description = issue.description.trim(),
                requiredAction = issue.requiredAction.trim(),
                category = parseCategory(issue.category),
            )
        }
        when (decision) {
            ReviewStatus.APPROVED -> {
                if (issues.any { it.severity == Severity.CRITICAL || it.severity == Severity.HIGH }) {
                    throw ContractException("APPROVED cannot include HIGH/CRITICAL issues")
                }
            }
            ReviewStatus.REJECTED -> {
                if (issues.isEmpty()) throw ContractException("REJECTED requires at least one issue")
            }
            ReviewStatus.ESCALATED, ReviewStatus.PENDING -> Unit
        }
        return ValidatedInspectorReview(decision, envelope.summary.trim(), envelope.reason.trim(), confidence, severity, issues)
    }

    private fun parseDecision(raw: String): ReviewStatus = when (raw.trim().uppercase()) {
        "APPROVED" -> ReviewStatus.APPROVED
        "REJECTED" -> ReviewStatus.REJECTED
        "ESCALATED" -> ReviewStatus.ESCALATED
        else -> throw ContractException("invalid decision")
    }

    private fun parseSeverity(raw: String): Severity = try {
        Severity.valueOf(raw.trim().uppercase())
    } catch (_: Exception) {
        throw ContractException("invalid severity")
    }

    private fun parseCategory(raw: String): IssueCategory = try {
        IssueCategory.valueOf(raw.trim().uppercase())
    } catch (_: Exception) {
        throw ContractException("unknown category")
    }
}
