package com.agentflow.domain
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.validation.DomainException
import com.agentflow.domain.validation.MissionStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionStateMachineTest {
    @Test fun createdCanStartResearch() {
        assertTrue(MissionStateMachine.canTransition(MissionStatus.CREATED, MissionStatus.RESEARCHING))
    }
    @Test fun approvedIsTerminal() {
        assertTrue(MissionStateMachine.isTerminal(MissionStatus.APPROVED))
        assertFalse(MissionStateMachine.canTransition(MissionStatus.APPROVED, MissionStatus.EXECUTING))
    }
    @Test fun reviewingCanApproveOrRevise() {
        assertTrue(MissionStateMachine.canTransition(MissionStatus.REVIEWING, MissionStatus.APPROVED))
        assertTrue(MissionStateMachine.canTransition(MissionStatus.REVIEWING, MissionStatus.REVISION_REQUIRED))
    }
    @Test(expected = DomainException.InvalidTransition::class)
    fun cannotSkipCreatedToApproved() {
        MissionStateMachine.transition(MissionStatus.CREATED, MissionStatus.APPROVED)
    }
    @Test fun pausedCanResumeToExecuting() {
        assertTrue(MissionStateMachine.canTransition(MissionStatus.PAUSED, MissionStatus.EXECUTING))
    }
}
