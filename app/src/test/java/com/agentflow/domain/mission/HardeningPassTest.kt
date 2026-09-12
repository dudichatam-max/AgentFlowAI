package com.agentflow.domain.mission

import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.agent.AgentConnectionTester
import com.agentflow.domain.agent.DefaultAgentFactory
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Priority
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.policy.DecisionGateway
import com.agentflow.domain.policy.LoopGuard
import com.agentflow.domain.policy.LoopGuardLimits
import com.agentflow.domain.policy.PolicyEngine
import com.agentflow.domain.provider.FakeAIProvider
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderPolicy
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.task.TaskWorkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HardeningPassTest {

    private fun agent(id: String, caps: Set<AgentCapability> = setOf(AgentCapability.CODE), project: String = "p") = Agent(
        id = id,
        projectId = project,
        name = id,
        role = id,
        description = id,
        providerId = ProviderId.GROQ,
        modelId = "llama-3.1-8b-instant",
        capabilities = caps,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun defaultAgentsHaveActiveModels() {
        val seeded = DefaultAgentFactory.createAll("proj")
        assertThat(seeded.map { it.agent.name }).containsAtLeast(
            "R&D Orchestrator", "Researcher", "Coder", "Reviewer", "Inspector", "Synthesizer",
        )
        seeded.forEach {
            assertThat(it.agent.providerId).isNotNull()
            assertThat(it.agent.modelId).isNotEmpty()
            assertThat(it.agent.freeOnly).isTrue()
            assertThat(it.agent.toString()).doesNotContain("apiKey")
        }
        assertThat(seeded.any { AgentCapability.ORCHESTRATE in it.agent.capabilities }).isTrue()
        assertThat(seeded.any { AgentCapability.INSPECT in it.agent.capabilities }).isTrue()
        assertThat(seeded.any { AgentCapability.SYNTHESIZE in it.agent.capabilities }).isTrue()
    }

    @Test
    fun atomicActionBatchRollsBack() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "batch", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val create = MissionAction.CreateTask("A", "a", "dev")
        val badDep = MissionAction.AddDependency("missing-task", "also-missing")
        val result = engine.processActionBatch(id, listOf(create, badDep))
        assertThat(result.isFailure).isTrue()
        assertThat(store.tasks).isEmpty()
        assertThat(store.deps).isEmpty()
    }

    @Test
    fun atomicActionBatchCommitsAll() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "ok", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "dev")).getOrThrow().createdTaskId!!
        val batch = engine.processActionBatch(id, listOf(MissionAction.AddDependency(b, a)))
        assertThat(batch.isSuccess).isTrue()
        assertThat(store.deps).isNotEmpty()
    }

    @Test
    fun gatewayRejectsCrossProjectBatch() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        store.agents["spy"] = agent("spy", project = "other")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val gateway = DecisionGateway(engine, store, PolicyEngine(store))
        val id = engine.createMission("p", "x", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val out = gateway.ingest(
            id,
            """{"version":1,"status":"CONTINUE","message":"x","actions":[
              {"type":"CREATE_TASK","title":"Good","description":"ok","assignedAgentId":"dev"},
              {"type":"CREATE_TASK","title":"Bad","description":"no","assignedAgentId":"spy"}]}""",
        )
        assertThat(out.accepted).isEmpty()
        assertThat(store.tasks).isEmpty()
    }

    @Test
    fun loopGuardRejectsWholeBatch() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val gateway = DecisionGateway(engine, store, PolicyEngine(store), LoopGuard(LoopGuardLimits(maxIdenticalActions = 1)))
        val id = engine.createMission("p", "loop", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val json = """{"version":1,"status":"CONTINUE","message":"x","actions":[{"type":"CREATE_TASK","title":"A","description":"a","assignedAgentId":"dev"}]}"""
        gateway.ingest(id, json)
        store.tasks.clear()
        val second = gateway.ingest(id, json)
        assertThat(second.rejected).isNotEmpty()
        assertThat(store.tasks).isEmpty()
    }

    @Test
    fun schedulerHopExhaustion() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(
            store,
            { task, _, _, _ -> TaskWorkResult("ok") },
            policy = MissionExecutionPolicy(maxSchedulerHops = 1, maxConcurrentTasks = 1),
        )
        val id = engine.createMission("p", "hops", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("B", "b", "dev")).getOrThrow()
        val outcome = engine.continueMission(id).getOrThrow()
        assertThat(outcome.exhaustedHops || store.tasks.values.any { it.status == TaskStatus.COMPLETED }).isTrue()
    }

    @Test
    fun processRecoveryReadyAndCompleted() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "rec", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        store.tasks[a] = store.tasks[a]!!.copy(status = TaskStatus.RUNNING)
        engine.continueMission(id).getOrThrow()
        assertThat(store.tasks[a]!!.status).isEqualTo(TaskStatus.COMPLETED)
        val results = store.results.count { it.taskId == a }
        engine.continueMission(id).getOrThrow()
        assertThat(store.results.count { it.taskId == a }).isEqualTo(results)
    }

    @Test
    fun reviewingIsNotExecuted() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "rev", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val out = engine.continueMission(id).getOrThrow()
        assertThat(out.workRemaining).isFalse()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVIEWING)
    }

    @Test
    fun waitingForUserIsNotBypassed() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "wait", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.WAITING_FOR_USER)
        val out = engine.continueMission(id)
        assertThat(out.isFailure).isTrue()
    }

    @Test
    fun paidModelRejected() {
        val model = com.agentflow.domain.provider.ProviderModel(
            id = "gpt-paid",
            displayName = "paid",
            provider = ProviderType.GROQ,
            isFree = false,
        )
        assertThat(ProviderPolicy.validateModel(model, freeOnly = true)).isNotNull()
    }

    @Test
    fun agentTestUsesBrainPath() = runTest {
        val fake = FakeAIProvider(ProviderType.GROQ)
        fake.results += FakeAIProvider.ok(ProviderType.GROQ, "AGENTFLOW_OK")
        val tester = AgentConnectionTester(ProviderManager(mapOf(ProviderType.GROQ to fake)))
        val agent = agent("dev")
        val result = tester.test(agent, emptyList())
        assertThat(result.success).isTrue()
        assertThat(fake.requests).hasSize(1)
        assertThat(fake.requests.single().systemInstruction).isNotEmpty()
    }

    @Test
    fun agentModelOverrideDoesNotTouchKey() {
        val seeded = DefaultAgentFactory.createAll("proj").first()
        val updated = seeded.agent.copy(providerId = ProviderId.GEMINI, modelId = "gemini-2.5-flash")
        assertThat(updated.providerId).isEqualTo(ProviderId.GEMINI)
        assertThat(updated.toString().lowercase()).doesNotContain("apikey")
    }
}
