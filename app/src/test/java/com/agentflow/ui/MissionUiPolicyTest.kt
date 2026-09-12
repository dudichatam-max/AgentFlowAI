package com.agentflow.ui

import com.agentflow.domain.model.MissionStatus
import com.agentflow.ui.mission.MissionUiPolicy
import org.junit.Test
import com.google.common.truth.Truth.assertThat

class MissionUiPolicyTest {
    @Test fun startIsOnlyAvailableForCreatedMission() {
        assertThat(MissionUiPolicy.canStart(MissionStatus.CREATED)).isTrue()
        assertThat(MissionUiPolicy.canStart(MissionStatus.REVIEWING)).isFalse()
    }

    @Test fun pauseResumeAndCancelFollowTerminalStateRules() {
        assertThat(MissionUiPolicy.canPause(MissionStatus.EXECUTING)).isTrue()
        assertThat(MissionUiPolicy.canPause(MissionStatus.CREATED)).isFalse()
        assertThat(MissionUiPolicy.canResume(MissionStatus.PAUSED)).isTrue()
        assertThat(MissionUiPolicy.canResume(MissionStatus.APPROVED)).isFalse()
        assertThat(MissionUiPolicy.canCancel(MissionStatus.REVIEWING)).isTrue()
        assertThat(MissionUiPolicy.canCancel(MissionStatus.APPROVED)).isFalse()
    }

    @Test fun inspectorIsAvailableOnlyForReviewStates() {
        assertThat(MissionUiPolicy.canOpenInspector(MissionStatus.REVIEWING)).isTrue()
        assertThat(MissionUiPolicy.canOpenInspector(MissionStatus.REVISION_REQUIRED)).isTrue()
        assertThat(MissionUiPolicy.canOpenInspector(MissionStatus.ESCALATED)).isTrue()
        assertThat(MissionUiPolicy.canOpenInspector(MissionStatus.EXECUTING)).isFalse()
    }

    @Test fun failedStartMustNotBeScheduled() {
        assertThat(MissionUiPolicy.shouldEnqueueAfterStart(true)).isTrue()
        assertThat(MissionUiPolicy.shouldEnqueueAfterStart(false)).isFalse()
    }

    @Test fun userOverrideIsLimitedToReviewStates() {
        assertThat(MissionUiPolicy.canUserOverride(MissionStatus.REVIEWING)).isTrue()
        assertThat(MissionUiPolicy.canUserOverride(MissionStatus.REVISION_REQUIRED)).isTrue()
        assertThat(MissionUiPolicy.canUserOverride(MissionStatus.ESCALATED)).isTrue()
        assertThat(MissionUiPolicy.canUserOverride(MissionStatus.CREATED)).isFalse()
        assertThat(MissionUiPolicy.canUserOverride(MissionStatus.EXECUTING)).isFalse()
    }
}
