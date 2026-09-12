package com.agentflow.domain.mission

import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.inspector.ArtifactService
import com.agentflow.domain.inspector.InMemoryArtifactCatalog
import com.agentflow.domain.inspector.InMemoryReviewCatalog
import com.agentflow.domain.inspector.InspectorService
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.ArtifactType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Priority
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.policy.DecisionGateway
import com.agentflow.domain.policy.PolicyEngine
import com.agentflow.domain.task.TaskWorkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FinalInspectorTest {

    private fun agent(id: String, name: String, caps: Set<AgentCapability>, project: String = "p") = Agent(
        id = id,
        projectId = project,
        name = name,
        role = name,
        description = name,
        providerId = ProviderId.GROQ,
        modelId = "m",
        capabilities = caps,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun renamedOrchestratorStillPlans() = runTest {
        val store = InMemoryMissionStore()
        store.agents["brain"] = agent("brain", "Chief Scientist", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev", "Maker", setOf(AgentCapability.CODE))
        val runtime = com.agentflow.domain.mission.MissionRuntime.wire(
            store = store,
            worker = { task, _, _, _ -> TaskWorkResult("ok:${task.title}") },
            planner = {
                """{"version":1,"status":"CONTINUE","message":"plan","actions":[{"type":"CREATE_TASK","title":"Research","description":"r","assignedAgentId":"dev"}]}"""
            },
            artifacts = ArtifactService(InMemoryArtifactCatalog()),
            planningRequired = true,
        )
        val engine = runtime.engine
        val id = engine.createMission("p", "renamed", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        assertThat(store.tasks.values.map { it.title }).contains("Research")
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
    }

    @Test
    fun missingPlannerDoesNotEnterExecution() = runTest {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", setOf(AgentCapability.ORCHESTRATE))
        val engine = MissionEngine(
            store,
            { task, _, _, _ -> TaskWorkResult("x") },
            planningRequired = true,
        )
        val id = engine.createMission("p", "noplan", "d").getOrThrow()
        val result = engine.startMission(id)
        assertThat(result.isFailure).isTrue()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.ESCALATED)
    }

    @Test
    fun specialistCompletionDoesNotMintImplementationPlan() = runTest {
        val catalog = InMemoryArtifactCatalog()
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev", "Dev", setOf(AgentCapability.CODE, AgentCapability.SYNTHESIZE))
        val artifacts = ArtifactService(catalog)
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("specialist notes") }, artifacts = artifacts)
        val id = engine.createMission("p", "spec", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("Write UI notes", "notes", "dev")).getOrThrow()
        // stop before auto-synthesis by using a worker that only ran the specialist:
        // complete specialist only, then assert no artifact yet by checking catalog before maybeFinish synthesis.
        // continueMission will synthesize; isolate by completing task via action without finish:
        val taskId = store.tasks.values.single().id
        engine.processAction(id, MissionAction.CompleteTask(taskId, "specialist notes", 0.5)).getOrThrow()
        assertThat(artifacts.latest(id)).isNull()
    }

    @Test
    fun inspectorRejectThenApproveKeepsVersions() = runTest {
        val catalog = InMemoryArtifactCatalog()
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["insp"] = agent("insp", "QA", setOf(AgentCapability.INSPECT))
        val artifacts = ArtifactService(catalog)
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") }, artifacts = artifacts)
        val reviews = InMemoryReviewCatalog()
        val inspector = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "rev", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "plan.md", "v1 body")).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val v1 = artifacts.latest(id)!!
        inspector.applyProposal(
            id,
            """{"version":1,"decision":"REJECTED","summary":"gaps","reason":"incomplete","confidence":0.9,"severity":"HIGH","issues":[{"severity":"HIGH","description":"missing sync","requiredAction":"add sync","category":"ARCHITECTURE"}]}""",
        ).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVISION_REQUIRED)
        assertThat(reviews.list(id)).isNotEmpty()
        engine.processAction(id, MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "plan.md", "v2 body fixed")).getOrThrow()
        val v2 = artifacts.latest(id)!!
        assertThat(v2.version).isEqualTo(2)
        assertThat(v1.id).isNotEqualTo(v2.id)
        assertThat(catalog.readContent(v1.id)).isEqualTo("v1 body")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        inspector.applyProposal(
            id,
            """{"version":1,"decision":"APPROVED","summary":"ok","reason":"complete","confidence":0.95,"severity":"NONE","issues":[]}""",
        ).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.APPROVED)
        assertThat(reviews.list(id).map { it.status }).containsAtLeast(ReviewStatus.REJECTED, ReviewStatus.APPROVED)
    }

    @Test
    fun unchangedRevisionArtifactEscalates() = runTest {
        val catalog = InMemoryArtifactCatalog()
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        val artifacts = ArtifactService(catalog)
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") }, artifacts = artifacts)
        val id = engine.createMission("p", "same", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "plan.md", "same-body")).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVISION_REQUIRED)
        engine.processAction(id, MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "plan.md", "same-body")).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.ESCALATED)
        assertThat(artifacts.latest(id)!!.version).isEqualTo(1)
    }

    @Test
    fun atomicBatchRejectsEntireProposal() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Dev", setOf(AgentCapability.CODE))
        store.agents["spy"] = agent("spy", "Spy", emptySet(), project = "other")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val gateway = DecisionGateway(engine, store, PolicyEngine(store))
        val id = engine.createMission("p", "batch", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val out = gateway.ingest(
            id,
            """{"version":1,"status":"CONTINUE","message":"x","actions":[
              {"type":"CREATE_TASK","title":"Good","description":"ok","assignedAgentId":"dev"},
              {"type":"CREATE_TASK","title":"Bad","description":"no","assignedAgentId":"spy"}
            ]}""",
        )
        assertThat(out.accepted).isEmpty()
        assertThat(store.tasks).isEmpty()
        assertThat(out.rejected.any { it.reason.contains("project") }).isTrue()
    }

    @Test
    fun linearDagSingleContinueRespectsOrder() = runTest {
        val order = mutableListOf<String>()
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev", "Dev", setOf(AgentCapability.CODE))
        val engine = MissionEngine(store, { task, _, _, _ ->
            order += task.title
            TaskWorkResult("ok")
        }, artifacts = ArtifactService(InMemoryArtifactCatalog()))
        val id = engine.createMission("p", "lin", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "dev")).getOrThrow().createdTaskId!!
        val c = engine.processAction(id, MissionAction.CreateTask("C", "c", "dev")).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.AddDependency(b, a)).getOrThrow()
        engine.processAction(id, MissionAction.AddDependency(c, b)).getOrThrow()
        engine.continueMission(id).getOrThrow()
        assertThat(store.tasks[a]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[b]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[c]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(order.indexOf("A")).isLessThan(order.indexOf("B"))
        assertThat(order.indexOf("B")).isLessThan(order.indexOf("C"))
    }
}
