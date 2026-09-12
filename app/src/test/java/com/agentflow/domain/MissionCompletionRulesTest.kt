package com.agentflow.domain
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.validation.CompletionSnapshot
import com.agentflow.domain.validation.MissionCompletionRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionCompletionRulesTest {
    private fun snap(
        tasks: Boolean = true, artifact: Boolean = true, inspector: Boolean = true,
        critical: Boolean = false, user: Boolean = false, blocked: Boolean = false,
        revision: Boolean = false, review: ReviewStatus = ReviewStatus.APPROVED,
        status: MissionStatus = MissionStatus.REVIEWING,
    ) = CompletionSnapshot(status, tasks, artifact, inspector, critical, user, blocked, revision, review)

    @Test fun approveWhenAllGatesPass() {
        assertTrue(MissionCompletionRules.canApprove(snap()))
        assertTrue(MissionCompletionRules.reasonsBlocked(snap()).isEmpty())
    }
    @Test fun blockWhenInspectorMissing() {
        assertFalse(MissionCompletionRules.canApprove(snap(inspector = false, review = ReviewStatus.PENDING)))
    }
    @Test fun blockOpenCritical() {
        assertFalse(MissionCompletionRules.canApprove(snap(critical = true)))
        assertTrue(MissionCompletionRules.reasonsBlocked(snap(critical = true)).contains("open critical issues"))
    }
    @Test fun agentSayingDoneIsNotEnough() {
        assertFalse(MissionCompletionRules.canApprove(snap(artifact = false, inspector = false, review = ReviewStatus.PENDING)))
    }
}
