package com.agentflow.domain.validation
import com.agentflow.domain.model.MissionStatus

object MissionStateMachine {
    private val allowed: Map<MissionStatus, Set<MissionStatus>> = mapOf(
        MissionStatus.CREATED to setOf(MissionStatus.RESEARCHING, MissionStatus.PLANNING, MissionStatus.EXECUTING, MissionStatus.WAITING_FOR_USER, MissionStatus.PAUSED, MissionStatus.CANCELLED),
        MissionStatus.RESEARCHING to setOf(MissionStatus.PLANNING, MissionStatus.EXECUTING, MissionStatus.WAITING_FOR_USER, MissionStatus.PAUSED, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.ESCALATED),
        MissionStatus.PLANNING to setOf(MissionStatus.RESEARCHING, MissionStatus.EXECUTING, MissionStatus.REVIEWING, MissionStatus.WAITING_FOR_USER, MissionStatus.PAUSED, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.ESCALATED),
        MissionStatus.WAITING_FOR_USER to setOf(MissionStatus.RESEARCHING, MissionStatus.PLANNING, MissionStatus.EXECUTING, MissionStatus.PAUSED, MissionStatus.CANCELLED, MissionStatus.ESCALATED),
        MissionStatus.EXECUTING to setOf(MissionStatus.REVIEWING, MissionStatus.WAITING_FOR_USER, MissionStatus.REVISION_REQUIRED, MissionStatus.PAUSED, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.ESCALATED),
        MissionStatus.REVIEWING to setOf(MissionStatus.APPROVED, MissionStatus.REVISION_REQUIRED, MissionStatus.ESCALATED, MissionStatus.PAUSED, MissionStatus.FAILED, MissionStatus.CANCELLED),
        MissionStatus.REVISION_REQUIRED to setOf(MissionStatus.EXECUTING, MissionStatus.REVIEWING, MissionStatus.WAITING_FOR_USER, MissionStatus.PAUSED, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.ESCALATED),
        MissionStatus.PAUSED to setOf(MissionStatus.RESEARCHING, MissionStatus.PLANNING, MissionStatus.EXECUTING, MissionStatus.REVIEWING, MissionStatus.WAITING_FOR_USER, MissionStatus.REVISION_REQUIRED, MissionStatus.CANCELLED, MissionStatus.FAILED),
        MissionStatus.ESCALATED to setOf(MissionStatus.EXECUTING, MissionStatus.WAITING_FOR_USER, MissionStatus.PAUSED, MissionStatus.CANCELLED, MissionStatus.FAILED, MissionStatus.APPROVED),
        MissionStatus.APPROVED to emptySet(),
        MissionStatus.FAILED to emptySet(),
        MissionStatus.CANCELLED to emptySet(),
    )
    fun canTransition(from: MissionStatus, to: MissionStatus): Boolean =
        from == to || allowed[from].orEmpty().contains(to)
    fun requireTransition(from: MissionStatus, to: MissionStatus): MissionStatus = transition(from, to)
    fun transition(from: MissionStatus, to: MissionStatus): MissionStatus {
        if (!canTransition(from, to)) throw DomainException.InvalidTransition(from, to)
        return to
    }
    fun isTerminal(status: MissionStatus): Boolean =
        status == MissionStatus.APPROVED || status == MissionStatus.FAILED || status == MissionStatus.CANCELLED
}
