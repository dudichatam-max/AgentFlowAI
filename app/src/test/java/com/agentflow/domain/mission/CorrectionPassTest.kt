package com.agentflow.domain.mission

import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.context.ContextBuilder
import com.agentflow.domain.inspector.ArtifactService
import com.agentflow.domain.inspector.InMemoryArtifactCatalog
import com.agentflow.domain.inspector.InMemoryReviewCatalog
import com.agentflow.domain.inspector.InspectorService
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.ArtifactType
import com.agentflow.domain.model.DependencyType
import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Priority
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.Reference
import com.agentflow.domain.model.ReferenceType
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.policy.DecisionGateway
import com.agentflow.domain.policy.PolicyEngine
import com.agentflow.domain.reference.ReferenceStorage
import com.agentflow.domain.reference.StoredBlob
import com.agentflow.domain.task.TaskWorkResult
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CorrectionPassTest {

    private val catalog = InMemoryArtifactCatalog()

    private fun agent(
        id: String,
        caps: Set<AgentCapability> = emptySet(),
        project: String = "p",
    ) = Agent(
        id = id,
        projectId = project,
        name = id,
        role = id,
        description = id,
        providerId = ProviderId.GROQ,
        modelId = "m",
        capabilities = caps,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun harness(
        planner: MissionPlanner? = null,
        ingest: (suspend (String, String) -> Unit)? = null,
        context: MissionContextFactory? = null,
    ): Triple<InMemoryMissionStore, MissionEngine, ArtifactService> {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev", setOf(AgentCapability.CODE))
        store.agents["ui"] = agent("ui", setOf(AgentCapability.CODE))
        store.agents["insp"] = agent("insp", setOf(AgentCapability.INSPECT))
        store.agents["spy"] = agent("spy", project = "other")
        val artifacts = ArtifactService(catalog)
        val engine = MissionEngine(
            store = store,
            worker = { task, _, _, contextBlock ->
                lastContext = contextBlock
                TaskWorkResult("done:${task.title}\n$contextBlock")
            },
            artifacts = artifacts,
            contextFactory = context,
            planner = planner,
            ingestPlan = ingest,
        )
        return Triple(store, engine, artifacts)
    }

    private var lastContext: String = ""

    @Test
    fun linearDagContinuesWithoutRestart() = runTest {
        val (store, engine, _) = harness()
        val id = engine.createMission("p", "lin", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "dev")).getOrThrow().createdTaskId!!
        val c = engine.processAction(id, MissionAction.CreateTask("C", "c", "dev", Priority.HIGH)).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.AddDependency(b, a)).getOrThrow()
        engine.processAction(id, MissionAction.AddDependency(c, b)).getOrThrow()
        engine.continueMission(id).getOrThrow()
        assertThat(store.tasks[a]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[b]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[c]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVIEWING)
    }

    @Test
    fun parallelDagWaitsForRequiredDeps() = runTest {
        val order = mutableListOf<String>()
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ ->
            order += task.title
            TaskWorkResult("ok")
        }, artifacts = ArtifactService(catalog))
        val id = engine.createMission("p", "par", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "dev")).getOrThrow().createdTaskId!!
        val c = engine.processAction(id, MissionAction.CreateTask("C", "c", "rd")).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.AddDependency(c, a)).getOrThrow()
        engine.processAction(id, MissionAction.AddDependency(c, b)).getOrThrow()
        engine.continueMission(id).getOrThrow()
        assertThat(order.take(2)).containsExactly("A", "B")
        assertThat(order).contains("C")
        assertThat(order.indexOf("C")).isGreaterThan(order.indexOf("A"))
        assertThat(order.indexOf("C")).isGreaterThan(order.indexOf("B"))
        assertThat(store.tasks[c]!!.status).isEqualTo(TaskStatus.COMPLETED)
    }

    @Test
    fun dynamicTaskCreationIsPickedUp() = runTest {
        val (store, engine, _) = harness()
        val id = engine.createMission("p", "dyn", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow()
        engine.continueMission(id).getOrThrow()
        // after A, synthesis may already have run; create another specialist mid-mission
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.EXECUTING)
        val extra = engine.processAction(id, MissionAction.CreateTask("Extra research", "x", "dev")).getOrThrow().createdTaskId!!
        engine.continueMission(id).getOrThrow()
        assertThat(store.tasks[extra]!!.status).isEqualTo(TaskStatus.COMPLETED)
    }

    @Test
    fun processRecoveryDoesNotDuplicateResult() = runTest {
        val (store, engine, _) = harness()
        val id = engine.createMission("p", "rec", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "rd")).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.AddDependency(b, a)).getOrThrow()
        store.tasks[a] = store.tasks[a]!!.copy(status = TaskStatus.RUNNING)
        store.results += com.agentflow.domain.model.TaskResult("r1", a, id, "already", createdAt = 1)
        engine.continueMission(id).getOrThrow()
        assertThat(store.results.count { it.taskId == a }).isEqualTo(1)
        assertThat(store.tasks[a]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[b]!!.status).isEqualTo(TaskStatus.COMPLETED)
    }

    @Test
    fun planningCreatesGraphThroughGateway() = runTest {
        val store = InMemoryMissionStore()
        store.agents["brain"] = agent("brain", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev", setOf(AgentCapability.CODE))
        var plannerCalls = 0
        val planJson = """{"version":1,"status":"CONTINUE","message":"Create research task","confidence":0.95,"reason":"Research is required before synthesis","requiresUserInput":false,"actions":[{"type":"CREATE_TASK","title":"Research","description":"Research the required topic","assignedAgentId":"dev","priority":"HIGH"}]}"""
        val runtime = MissionRuntime.wire(
            store = store,
            worker = { task, _, _, _ -> TaskWorkResult("ok:${task.title}") },
            planner = {
                plannerCalls += 1
                planJson
            },
            artifacts = ArtifactService(InMemoryArtifactCatalog()),
            planningRequired = true,
        )
        assertThat(runtime.engine.planningWired()).isTrue()
        val id = runtime.engine.createMission("p", "plan", "no graph yet").getOrThrow()
        val start = runtime.engine.startMission(id)
        val titles = store.listTasks(id).map { it.title }
        val snapshot = "status=${store.missions[id]?.status} mission=$id project=${store.missions[id]?.projectId} plannerCalls=$plannerCalls titles=$titles events=${store.events.map { it.message }} start=${start.exceptionOrNull()?.message}"
        assertThat(start.isSuccess).isTrue()
        assertThat(plannerCalls).isEqualTo(1)
        com.google.common.truth.Truth.assertWithMessage(snapshot).that(titles).contains("Research")
        val research = store.listTasks(id).single { it.title == "Research" }
        assertThat(research.missionId).isEqualTo(id)
        assertThat(store.getAgent(research.assignedAgentId)!!.projectId).isEqualTo("p")
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
        assertThat(store.events.any { it.type == MissionEventType.TASK_CREATED && it.message == "Research" }).isTrue()
    }

    @Test
    fun productionContainerWiresPlanner() = runTest {
        val store = InMemoryMissionStore()
        store.agents["brain"] = agent("brain", setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE)).copy(modelId = "llama-3.1-8b-instant")
        store.agents["dev"] = agent("dev", setOf(AgentCapability.CODE)).copy(modelId = "llama-3.1-8b-instant")
        val fake = com.agentflow.domain.provider.FakeAIProvider(com.agentflow.domain.provider.ProviderType.GROQ)
        fake.results += com.agentflow.domain.provider.FakeAIProvider.ok(
            com.agentflow.domain.provider.ProviderType.GROQ,
            """{"version":1,"status":"CONTINUE","message":"Create research task","confidence":0.9,"actions":[{"type":"CREATE_TASK","title":"Research","description":"Research the required topic","assignedAgentId":"dev"}]}""",
        )
        val manager = com.agentflow.domain.provider.ProviderManager(mapOf(com.agentflow.domain.provider.ProviderType.GROQ to fake))
        val runtime = MissionRuntime.wire(
            store = store,
            worker = { task, _, _, _ -> TaskWorkResult("ok:${task.title}") },
            planner = AgentMissionPlanner(store, manager),
            planningRequired = true,
        )
        assertThat(runtime.engine.planningWired()).isTrue()
        val id = runtime.engine.createMission("p", "wired", "prove production planner").getOrThrow()
        runtime.engine.startMission(id).getOrThrow()
        assertThat(fake.requests).hasSize(1)
        assertThat(store.listTasks(id).map { it.title }).contains("Research")
        val research = store.listTasks(id).single { it.title == "Research" }
        assertThat(research.missionId).isEqualTo(id)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
    }

    @Test
    fun contextBuilderFiltersReferences() = runTest {
        val storage = MemoryRefStorage()
        val builder = ContextBuilder(storage)
        val (store, engine, _) = harness(context = MissionContextFactory(builder))
        val always = Reference("r1", "p", null, "notes.md", ReferenceType.TEXT, contentHash = "h1", inclusionMode = InclusionMode.ALWAYS, createdAt = 1, updatedAt = 1)
        val never = Reference("r2", "p", null, "secret.md", ReferenceType.TEXT, contentHash = "h2", inclusionMode = InclusionMode.NEVER, createdAt = 1, updatedAt = 1)
        val dup = Reference("r3", "p", null, "notes-copy.md", ReferenceType.TEXT, contentHash = "h1", inclusionMode = InclusionMode.ALWAYS, createdAt = 1, updatedAt = 1)
        val github = Reference("r4", "p", null, "README.md", ReferenceType.GITHUB, contentHash = "h4", inclusionMode = InclusionMode.RELEVANT, createdAt = 1, updatedAt = 1)
        val secretName = Reference("r5", "p", null, "id_rsa", ReferenceType.FILE, contentHash = "h5", inclusionMode = InclusionMode.ALWAYS, createdAt = 1, updatedAt = 1)
        store.references += listOf(always, never, dup, github, secretName)
        storage.texts["r1"] = "always body about offline cache"
        storage.texts["r2"] = "never body"
        storage.texts["r3"] = "always body about offline cache"
        storage.texts["r4"] = "github readme offline"
        storage.texts["r5"] = "PRIVATE KEY"
        val id = engine.createMission("p", "ctx", "offline cache").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("Use refs", "offline cache", "dev")).getOrThrow()
        engine.continueMission(id).getOrThrow()
        assertThat(lastContext).contains("always body")
        assertThat(lastContext).doesNotContain("never body")
        assertThat(lastContext).doesNotContain("PRIVATE KEY")
    }

    @Test
    fun createArtifactPersistsMetadata() = runTest {
        val (store, engine, artifacts) = harness()
        val id = engine.createMission("p", "art", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val result = engine.processAction(
            id,
            MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "plan.md", "# plan"),
        ).getOrThrow()
        val art = artifacts.latest(id)!!
        assertThat(art.id).isEqualTo(result.createdTaskId ?: art.id)
        assertThat(art.contentHash).isNotEmpty()
        assertThat(art.sizeBytes).isGreaterThan(0)
        assertThat(art.missionId).isEqualTo(id)
        assertThat(store.events.any { it.type == MissionEventType.ARTIFACT_CREATED }).isTrue()
        assertThat(catalog.readContent(art.id)).isEqualTo("# plan")
    }

    @Test
    fun noReviewingWithoutArtifactWhenSynthesisMissing() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("x") }, artifacts = ArtifactService(InMemoryArtifactCatalog()))
        val id = engine.createMission("p", "none", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow()
        engine.continueMission(id).getOrThrow()
        assertThat(store.missions[id]!!.status).isNotEqualTo(MissionStatus.REVIEWING)
    }

    @Test
    fun inspectorRejectCreatesRevisionTask() = runTest {
        val (store, engine, artifacts) = harness()
        val reviews = InMemoryReviewCatalog()
        val inspector = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "rev", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        artifacts.publish(id, "plan", "v1 bad")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        inspector.applyProposal(
            id,
            """{"version":1,"decision":"REJECTED","summary":"no","reason":"gaps","confidence":0.9,"severity":"HIGH","issues":[{"severity":"HIGH","description":"gap","requiredAction":"fix","category":"ARCHITECTURE"}]}""",
        ).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVISION_REQUIRED)
        assertThat(store.tasks.values.any { it.assignedAgentId == "rd" }).isTrue()
    }

    @Test
    fun unchangedArtifactIsIdempotent() = runTest {
        val (store, engine, artifacts) = harness()
        val id = engine.createMission("p", "same", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "p.md", "same")).getOrThrow()
        val first = artifacts.latest(id)!!
        engine.processAction(id, MissionAction.CreateArtifact(ArtifactType.IMPLEMENTATION_PLAN, "p.md", "same")).getOrThrow()
        val second = artifacts.latest(id)!!
        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.version).isEqualTo(1)
    }

    @Test
    fun projectIsolationRejectsForeignAgent() = runTest {
        val (_, engine, _) = harness()
        val id = engine.createMission("p", "iso", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val result = engine.processAction(id, MissionAction.CreateTask("x", "x", "spy"))
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun malformedDecisionDoesNotMutateGraph() = runTest {
        val (store, engine, _) = harness()
        val gateway = DecisionGateway(engine, store, PolicyEngine(store))
        val id = engine.createMission("p", "bad", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val before = store.tasks.size
        val outcome = gateway.ingest(id, "this is not json")
        assertThat(outcome.parseError).isNotNull()
        assertThat(store.tasks.size).isEqualTo(before)
    }
}

private class MemoryRefStorage : ReferenceStorage {
    val texts = mutableMapOf<String, String>()
    override fun save(referenceId: String, filename: String, bytes: ByteArray) =
        StoredBlob(filename, bytes.size.toLong(), referenceId)
    override fun save(referenceId: String, filename: String, stream: InputStream, sizeHint: Long) =
        save(referenceId, filename, stream.readBytes())
    override fun open(referenceId: String): InputStream? = texts[referenceId]?.let { ByteArrayInputStream(it.toByteArray()) }
    override fun readText(referenceId: String, maxBytes: Int) = texts[referenceId]
    override fun delete(referenceId: String) { texts.remove(referenceId) }
    override fun exists(referenceId: String) = texts.containsKey(referenceId)
    override fun size(referenceId: String) = texts[referenceId]?.length?.toLong() ?: 0
    override fun totalBytes() = texts.values.sumOf { it.length.toLong() }
    override fun cleanupOrphans(knownIds: Set<String>) = 0
}
