package com.agentflow.domain.policy

import com.agentflow.domain.mission.InMemoryMissionStore
import com.agentflow.domain.mission.MissionAction
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.ArtifactType
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PolicyEngineTest {
    @Test
    fun blocksCrossProjectAndPathsAndSecrets() = runTest {
        val store = InMemoryMissionStore()
        store.missions["m"] = Mission("m", "p", "t", "d", MissionStatus.EXECUTING, 1, 1)
        store.agents["dev"] = Agent("dev", "p", "Dev", "d", "d", ProviderId.GROQ, "m", createdAt = 1, updatedAt = 1)
        store.agents["spy"] = Agent("spy", "other", "Spy", "d", "d", ProviderId.GROQ, "m", createdAt = 1, updatedAt = 1)
        store.tasks["t1"] = Task("t1", "m", null, com.agentflow.domain.model.CreatedByType.ENGINE, "e", "dev", "A", "d", TaskStatus.READY, createdAt = 1, updatedAt = 1)
        val policy = PolicyEngine(store)
        val mission = store.missions["m"]!!

        assertThat(policy.evaluate(mission, MissionAction.CreateTask("x", "y", "spy"))).isInstanceOf(PolicyDecision.Deny::class.java)
        assertThat(
            policy.evaluate(mission, MissionAction.RequestUserInput("paste your API key please", "need it")),
        ).isInstanceOf(PolicyDecision.Deny::class.java)
        assertThat(
            policy.evaluate(
                mission,
                MissionAction.CreateArtifact(ArtifactType.CODE_PATCH, "../../sdcard/secret", "x"),
            ),
        ).isInstanceOf(PolicyDecision.Deny::class.java)
        assertThat(policy.evaluate(mission, MissionAction.Continue)).isEqualTo(PolicyDecision.Allow)
    }
}
