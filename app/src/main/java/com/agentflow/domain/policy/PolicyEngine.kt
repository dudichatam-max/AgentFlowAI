package com.agentflow.domain.policy

import com.agentflow.domain.mission.MissionAction
import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.reference.SecretDenylist
import com.agentflow.domain.validation.MissionStateMachine

sealed class PolicyDecision {
    data object Allow : PolicyDecision()
    data class Deny(val reason: String) : PolicyDecision()
}

class PolicyEngine(
    private val store: MissionStore,
) {
    suspend fun evaluate(mission: Mission, action: MissionAction): PolicyDecision {
        if (mission.status == MissionStatus.CANCELLED) return PolicyDecision.Deny("mission cancelled")
        if (mission.status == MissionStatus.APPROVED) return PolicyDecision.Deny("mission already approved")
        if (mission.status == MissionStatus.PAUSED && action !is MissionAction.ResumeMission && action !is MissionAction.CancelMission) {
            return PolicyDecision.Deny("mission paused")
        }
        return when (action) {
            is MissionAction.CreateTask -> {
                val agent = store.getAgent(action.assignedAgentId)
                    ?: return PolicyDecision.Deny("agent missing")
                if (agent.projectId != mission.projectId) PolicyDecision.Deny("agent not in project")
                else if (action.title.isBlank()) PolicyDecision.Deny("blank title")
                else PolicyDecision.Allow
            }
            is MissionAction.AssignTask -> ownership(mission, action.taskId, action.agentId)
            is MissionAction.AddDependency -> {
                val a = store.getTask(action.taskId) ?: return PolicyDecision.Deny("task missing")
                val b = store.getTask(action.dependsOnTaskId) ?: return PolicyDecision.Deny("dependsOn missing")
                if (a.missionId != mission.id || b.missionId != mission.id) PolicyDecision.Deny("cross-mission dependency")
                else PolicyDecision.Allow
            }
            is MissionAction.CompleteTask, is MissionAction.FailTask, is MissionAction.RetryTask -> {
                val taskId = when (action) {
                    is MissionAction.CompleteTask -> action.taskId
                    is MissionAction.FailTask -> action.taskId
                    is MissionAction.RetryTask -> action.taskId
                    else -> return PolicyDecision.Deny("bad task action")
                }
                val task = store.getTask(taskId) ?: return PolicyDecision.Deny("task missing")
                if (task.missionId != mission.id) PolicyDecision.Deny("task not in mission")
                else PolicyDecision.Allow
            }
            is MissionAction.RequestUserInput -> {
                if (looksLikeSecretPrompt(action.question) || looksLikeSecretPrompt(action.reason)) {
                    PolicyDecision.Deny("secret request blocked")
                } else {
                    val taskId = action.taskId
                    if (taskId == null) PolicyDecision.Allow
                    else ownershipTask(mission, taskId)
                }
            }
            is MissionAction.RemoveDependency -> {
                val dependency = store.listDependencies(mission.id).firstOrNull { it.id == action.dependencyId }
                if (dependency != null) PolicyDecision.Allow
                else PolicyDecision.Deny("dependency not in mission")
            }
            is MissionAction.RequestReview -> {
                if (!MissionStateMachine.canTransition(mission.status, MissionStatus.REVIEWING)) {
                    PolicyDecision.Deny("cannot enter REVIEWING from ${mission.status}")
                } else PolicyDecision.Allow
            }
            is MissionAction.RequestSynthesis -> {
                val blocked = store.listTasks(mission.id).any { it.status == TaskStatus.BLOCKED }
                if (blocked) PolicyDecision.Deny("blocked tasks remain") else PolicyDecision.Allow
            }
            is MissionAction.CreateArtifact -> {
                if (action.name.contains("..") || action.name.startsWith("/")) {
                    PolicyDecision.Deny("illegal artifact name")
                } else PolicyDecision.Allow
            }
            MissionAction.Continue -> PolicyDecision.Allow
            else -> PolicyDecision.Allow
        }
    }

    private suspend fun ownership(mission: Mission, taskId: String, agentId: String): PolicyDecision {
        val taskDecision = ownershipTask(mission, taskId)
        if (taskDecision != PolicyDecision.Allow) return taskDecision
        val agent = store.getAgent(agentId) ?: return PolicyDecision.Deny("agent missing")
        if (agent.projectId != mission.projectId) return PolicyDecision.Deny("agent not in project")
        return PolicyDecision.Allow
    }

    private suspend fun ownershipTask(mission: Mission, taskId: String): PolicyDecision {
        val task = store.getTask(taskId) ?: return PolicyDecision.Deny("task missing")
        return if (task.missionId == mission.id) {
            PolicyDecision.Allow
        } else {
            PolicyDecision.Deny("task not in mission")
        }
    }

    private fun looksLikeSecretPrompt(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("api key", "password", "private key", "keystore", "token secret").any { it in lower } ||
            SecretDenylist.looksLikeSecretContent(text)
    }
}
