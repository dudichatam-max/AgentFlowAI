package com.agentflow.ui.mission

import com.agentflow.domain.model.MissionStatus

object MissionUiPolicy {
    fun shouldEnqueueAfterStart(success: Boolean): Boolean = success
    fun canStart(status: MissionStatus): Boolean = status == MissionStatus.CREATED

    fun canPause(status: MissionStatus): Boolean = status in setOf(
        MissionStatus.RESEARCHING,
        MissionStatus.PLANNING,
        MissionStatus.EXECUTING,
        MissionStatus.REVIEWING,
        MissionStatus.REVISION_REQUIRED,
        MissionStatus.WAITING_FOR_USER,
    )

    fun canResume(status: MissionStatus): Boolean = status == MissionStatus.PAUSED

    fun canCancel(status: MissionStatus): Boolean = status !in setOf(
        MissionStatus.APPROVED,
        MissionStatus.FAILED,
        MissionStatus.CANCELLED,
    )

    fun canOpenInspector(status: MissionStatus): Boolean = status in setOf(
        MissionStatus.REVIEWING,
        MissionStatus.REVISION_REQUIRED,
        MissionStatus.ESCALATED,
    )

    fun canUserOverride(status: MissionStatus): Boolean = canOpenInspector(status)
}
