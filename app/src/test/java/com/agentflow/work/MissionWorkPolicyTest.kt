package com.agentflow.work

import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MissionWorkPolicyTest {
    private fun m(status: MissionStatus) = Mission("id", "p", "t", "d", status, 1, 1)

    @Test
    fun skipsTerminalAndPaused() {
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.APPROVED))).isEqualTo(WorkDecision.SKIP)
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.CANCELLED))).isEqualTo(WorkDecision.SKIP)
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.PAUSED))).isEqualTo(WorkDecision.SKIP)
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.WAITING_FOR_USER))).isEqualTo(WorkDecision.SKIP)
        assertThat(MissionWorkPolicy.decide(null)).isEqualTo(WorkDecision.SKIP)
    }

    @Test
    fun runsActive() {
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.EXECUTING))).isEqualTo(WorkDecision.RUN)
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.REVISION_REQUIRED))).isEqualTo(WorkDecision.RUN)
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.ESCALATED))).isEqualTo(WorkDecision.SKIP)
        assertThat(MissionWorkPolicy.decide(m(MissionStatus.REVIEWING))).isEqualTo(WorkDecision.SKIP)
        assertThat(MissionWorkPolicy.uniqueName("abc")).isEqualTo("mission-abc")
    }
}
