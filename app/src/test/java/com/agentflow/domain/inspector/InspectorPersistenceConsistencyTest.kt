package com.agentflow.domain.inspector

import com.agentflow.domain.mission.InMemoryMissionStore
import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.mission.event
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.ProviderId
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InspectorPersistenceConsistencyTest {
    @Test
    fun reviewAndMissionStatusRollbackTogetherOnFailure() = runTest {
        val base = InMemoryMissionStore()
        val mission = Mission("m1", "p", "t", "d", MissionStatus.REVIEWING, 1, 2)
        base.missions[mission.id] = mission
        val failing = object : MissionStore by base {
            override suspend fun persistMissionStatus(mission: Mission, completionEvent: com.agentflow.domain.model.MissionEvent) {
                throw IllegalStateException("simulated status failure")
            }
        }
        val reviews = InMemoryReviewCatalog()
        val review = InspectorReview("r1", "m1", null, "insp", com.agentflow.domain.model.ReviewStatus.APPROVED, "ok", "ok", com.agentflow.domain.model.Severity.NONE, 3)
        val event = event("m1", MissionEventType.INSPECTOR_APPROVED, "ok", now = 3)

        val result = runCatching {
            reviews.persistDecision(review, emptyList(), mission.copy(status = MissionStatus.APPROVED), event, failing)
        }

        assertThat(result.isFailure).isTrue()
        assertThat(reviews.list("m1")).isEmpty()
        assertThat(base.missions["m1"]).isEqualTo(mission)
    }
}
