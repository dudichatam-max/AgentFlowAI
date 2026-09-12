package com.agentflow.domain.inspector

import com.agentflow.domain.contract.ContractException
import com.agentflow.domain.model.ReviewStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InspectorReviewValidatorTest {
    @Test
    fun approvedOk() {
        val v = InspectorReviewValidator.validate(
            InspectorReviewEnvelope(1, "APPROVED", "ok", "covers architecture", 0.96, "NONE", emptyList()),
        )
        assertThat(v.decision).isEqualTo(ReviewStatus.APPROVED)
    }

    @Test(expected = ContractException::class)
    fun rejectedNeedsIssues() {
        InspectorReviewValidator.validate(
            InspectorReviewEnvelope(1, "REJECTED", "incomplete", "missing sync", 0.9, "HIGH", emptyList()),
        )
    }

    @Test(expected = ContractException::class)
    fun approvedCannotHaveCritical() {
        InspectorReviewValidator.validate(
            InspectorReviewEnvelope(
                1, "APPROVED", "ok", "ok", 0.9, "NONE",
                listOf(InspectorIssueDto("CRITICAL", "hole", "fix", "ARCHITECTURE")),
            ),
        )
    }

    @Test
    fun rejectedWithIssue() {
        val v = InspectorReviewValidator.validate(
            InspectorReviewEnvelope(
                1, "REJECTED", "incomplete", "sync underspecified", 0.93, "HIGH",
                listOf(InspectorIssueDto("HIGH", "No conflict resolution", "Define rules", "ARCHITECTURE")),
            ),
        )
        assertThat(v.issues).hasSize(1)
        assertThat(v.issues.single().category).isEqualTo(IssueCategory.ARCHITECTURE)
    }
}
