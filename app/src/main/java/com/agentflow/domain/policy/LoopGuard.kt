package com.agentflow.domain.policy

import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.mission.MissionAction


data class LoopGuardLimits(
    val maxIdenticalActions: Int = 3,
    val maxActionsPerTick: Int = 12,
    val maxCreateTaskSignatures: Int = 8,
)

class LoopGuard(
    private val limits: LoopGuardLimits = LoopGuardLimits(),
) {
    private val localCounts = mutableMapOf<String, MutableMap<String, Int>>()

    fun signature(action: MissionAction): String = when (action) {
        is MissionAction.CreateTask -> "CREATE:${action.title.trim().lowercase()}:${action.assignedAgentId}"
        is MissionAction.AssignTask -> "ASSIGN:${action.taskId}:${action.agentId}"
        is MissionAction.AddDependency -> "DEP:${action.taskId}:${action.dependsOnTaskId}"
        is MissionAction.RemoveDependency -> "REMOVE_DEP:${action.dependencyId}"
        is MissionAction.CompleteTask -> "COMPLETE:${action.taskId}"
        is MissionAction.FailTask -> "FAIL:${action.taskId}"
        is MissionAction.RetryTask -> "RETRY:${action.taskId}"
        is MissionAction.RequestUserInput -> "USER:${action.question.take(80)}"
        is MissionAction.RequestSynthesis -> "SYNTH"
        is MissionAction.RequestReview -> "REVIEW"
        is MissionAction.RequestMoreResearch -> "RESEARCH"
        is MissionAction.CreateArtifact -> "ART:${action.name}"
        else -> action::class.simpleName ?: "X"
    }

    suspend fun inspect(missionId: String, actions: List<MissionAction>, store: MissionStore? = null): PolicyDecision {
        if (actions.size > limits.maxActionsPerTick) return PolicyDecision.Deny("too many actions in one decision")
        val counts = store?.loopGuardCounts(missionId) ?: localCounts[missionId].orEmpty()
        val batchCounts = counts.toMutableMap()
        for (action in actions) {
            val sig = signature(action)
            val count = batchCounts[sig] ?: 0
            if (count >= limits.maxIdenticalActions) return PolicyDecision.Deny("loop: $sig repeated")
            batchCounts[sig] = count + 1
        }
        val creates = actions.count { it is MissionAction.CreateTask }
        if (creates > limits.maxCreateTaskSignatures) return PolicyDecision.Deny("too many CREATE_TASK in one tick")
        return PolicyDecision.Allow
    }

    suspend fun hydrate(missionId: String, persisted: List<String>) {
        if (persisted.isEmpty()) return
        val log = localCounts.getOrPut(missionId) { mutableMapOf() }
        persisted.forEach { sig -> log[sig] = (log[sig] ?: 0) + 1 }
    }

    suspend fun record(missionId: String, action: MissionAction, store: MissionStore? = null): Int {
        val sig = signature(action)
        if (store != null) return store.incrementLoopGuard(missionId, sig)
        val log = localCounts.getOrPut(missionId) { mutableMapOf() }
        val next = (log[sig] ?: 0) + 1
        log[sig] = next
        return next
    }

    fun reset(missionId: String) {
        localCounts.remove(missionId)
    }
}
