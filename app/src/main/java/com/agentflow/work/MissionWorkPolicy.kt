package com.agentflow.work

import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionStatus

enum class WorkDecision { RUN, SKIP, RETRY }

object MissionWorkPolicy {
    fun decide(mission: Mission?): WorkDecision = when (mission?.status) {
        null -> WorkDecision.SKIP
        MissionStatus.APPROVED, MissionStatus.FAILED, MissionStatus.CANCELLED -> WorkDecision.SKIP
        MissionStatus.PAUSED, MissionStatus.WAITING_FOR_USER -> WorkDecision.SKIP
        MissionStatus.CREATED -> WorkDecision.SKIP
        MissionStatus.REVIEWING -> WorkDecision.SKIP
        MissionStatus.RESEARCHING, MissionStatus.PLANNING, MissionStatus.EXECUTING,
        MissionStatus.REVISION_REQUIRED,
        -> WorkDecision.RUN
        MissionStatus.ESCALATED -> WorkDecision.SKIP
    }

    fun uniqueName(missionId: String) = "mission-$missionId"

    fun shouldRecover(status: MissionStatus): Boolean = decide(
        Mission("", "", "", "", status, 0, 0),
    ) == WorkDecision.RUN
}
