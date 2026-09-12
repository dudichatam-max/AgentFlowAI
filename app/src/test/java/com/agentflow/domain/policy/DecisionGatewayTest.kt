package com.agentflow.domain.policy

import com.agentflow.domain.mission.InMemoryMissionStore
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.mission.MissionAction
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.task.TaskWorkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DecisionGatewayTest {
    private fun setup(): Triple<InMemoryMissionStore, MissionEngine, DecisionGateway> {
        val store = InMemoryMissionStore()
        store.agents["dev"] = Agent(
            "dev", "p", "Developer", "impl", "d", ProviderId.GROQ, "llama-3.1-8b-instant", createdAt = 1, updatedAt = 1,
        )
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok:${task.title}") })
        val gateway = DecisionGateway(engine, store, PolicyEngine(store), LoopGuard(LoopGuardLimits(maxIdenticalActions = 2)))
        return Triple(store, engine, gateway)
    }

    @Test
    fun acceptedCreateThenLoopBlocked() = runTest {
        val (store, engine, gateway) = setup()
        val missionId = engine.createMission("p", "Offline", "Add offline support").getOrThrow()
        engine.startMission(missionId).getOrThrow()
        val json = """
            {"version":1,"status":"CONTINUE","message":"need persistence analysis","confidence":0.9,
             "actions":[{"type":"CREATE_TASK","title":"Analyze offline persistence","description":"Inspect Room.","assignedAgentId":"dev","priority":"HIGH"}]}
        """.trimIndent()
        val first = gateway.ingest(missionId, json)
        assertThat(first.parseError).isNull()
        assertThat(first.accepted).isNotEmpty()
        gateway.ingest(missionId, json)
        val third = gateway.ingest(missionId, json)
        assertThat(third.rejected.any { it.reason.contains("loop") }).isTrue()
    }

    @Test
    fun foreignAgentDenied() = runTest {
        val (store, engine, gateway) = setup()
        store.agents["spy"] = Agent("spy", "other", "Spy", "x", "x", ProviderId.GROQ, "m", createdAt = 1, updatedAt = 1)
        val missionId = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(missionId).getOrThrow()
        val out = gateway.ingest(
            missionId,
            """{"version":1,"status":"CONTINUE","message":"x","actions":[{"type":"CREATE_TASK","title":"Hack","description":"no","assignedAgentId":"spy"}]}""",
        )
        assertThat(out.accepted).isEmpty()
        assertThat(out.rejected.single().reason).contains("project")
    }

    @Test
    fun secretQuestionDenied() = runTest {
        val (_, engine, gateway) = setup()
        val missionId = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(missionId).getOrThrow()
        val out = gateway.ingest(
            missionId,
            """{"version":1,"status":"WAITING_FOR_USER","message":"need key","requiresUserInput":true,
                "actions":[{"type":"REQUEST_USER_INPUT","question":"Paste your API key","reason":"so I can call the provider"}]}""",
        )
        assertThat(out.rejected.any { it.reason.contains("secret") }).isTrue()
    }

    @Test
    fun policyIsReevaluatedAfterEachBatchAction() = runTest {
        val (store, engine, gateway) = setup()
        val missionId = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(missionId).getOrThrow()

        val out = gateway.ingest(
            missionId,
            """{"version":1,"status":"CONTINUE","message":"pause then create",
                "actions":[
                    {"type":"PAUSE_MISSION"},
                    {"type":"CREATE_TASK","title":"Should be rejected","description":"x","assignedAgentId":"dev","priority":"HIGH"}
                ]}""",
        )

        assertThat(out.accepted).isEmpty()
        assertThat(out.rejected.single().reason).contains("mission paused")
        assertThat(store.getMission(missionId)!!.status.name).isEqualTo("EXECUTING")
        assertThat(store.listTasks(missionId)).isEmpty()
    }

    @Test
    fun loopGuardCountSurvivesRecreationOfLoopGuard() = runTest {
        val store = InMemoryMissionStore()
        val action = MissionAction.CreateTask("same", "d", "dev")
        val first = LoopGuard(LoopGuardLimits(maxIdenticalActions = 3))
        first.record("m1", action, store)
        first.record("m1", action, store)
        first.record("m1", action, store)

        val recreated = LoopGuard(LoopGuardLimits(maxIdenticalActions = 3))
        assertThat(recreated.inspect("m1", listOf(action), store)).isInstanceOf(PolicyDecision.Deny::class.java)
        assertThat(store.loopGuardCounts("m1")[recreated.signature(action)]).isEqualTo(3)
    }

    @Test
    fun invalidPlannerContractProducesFailureStatusAndMatchingEvent() = runTest {
        val (store, engine, gateway) = setup()
        val id = engine.createMission("p", "bad", "d").getOrThrow()
        engine.startMission(id).getOrThrow()

        gateway.ingest(id, "not-json")

        assertThat(store.missions[id]!!.status).isEqualTo(com.agentflow.domain.model.MissionStatus.FAILED)
        assertThat(store.events.last().type).isEqualTo(com.agentflow.domain.model.MissionEventType.MISSION_FAILED)
    }

    @Test
    fun loopGuardCountSurvivesEventHistoryTruncation() = runTest {
        val store = InMemoryMissionStore()
        val guard = LoopGuard(LoopGuardLimits(maxIdenticalActions = 3))
        val action = MissionAction.CreateTask("same", "d", "dev")
        repeat(3) { guard.record("m1", action, store) }
        store.events.clear()

        assertThat(guard.inspect("m1", listOf(action), store)).isInstanceOf(PolicyDecision.Deny::class.java)
        assertThat(store.loopGuardCounts("m1")[guard.signature(action)]).isEqualTo(3)
    }

}
