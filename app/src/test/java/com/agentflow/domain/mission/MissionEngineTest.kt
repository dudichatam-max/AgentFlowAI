package com.agentflow.domain.mission

import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.CreatedByType
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.Priority
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.task.TaskWorkResult
import com.agentflow.domain.validation.DomainException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MissionEngineTest {
    private fun agent(
        id: String,
        name: String,
        project: String = "p",
        role: String = name,
        capabilities: Set<AgentCapability> = emptySet(),
    ) = Agent(
        id = id,
        projectId = project,
        name = name,
        role = role,
        description = role,
        providerId = ProviderId.GROQ,
        modelId = "llama-3.1-8b-instant",
        capabilities = capabilities,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun seeded(): Pair<InMemoryMissionStore, MissionEngine> {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent(
            "rd",
            "R&D",
            role = "Research and development orchestrator",
            capabilities = setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE),
        )
        store.agents["insp"] = agent(
            "insp",
            "Inspector",
            role = "Quality inspector",
            capabilities = setOf(AgentCapability.INSPECT),
        )
        store.agents["dev"] = agent("dev", "Developer")
        store.agents["ui"] = agent("ui", "UI Expert")
        store.agents["dsp"] = agent("dsp", "DSP Expert")
        store.agents["other"] = agent("other", "Spy", project = "other")
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("done:${task.title}") })
        return store to engine
    }

    @Test
    fun offlineSupportGraphReachesReviewing() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "Offline", "Add offline support to the application.").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("Research offline architecture", "arch", "dev", Priority.HIGH)).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("Analyze UI while offline", "ui", "ui")).getOrThrow().createdTaskId!!
        val c = engine.processAction(id, MissionAction.CreateTask("Analyze data synchronization", "sync", "dsp")).getOrThrow().createdTaskId!!
        engine.continueMission(id).getOrThrow()
        if (store.missions[id]!!.status != MissionStatus.REVIEWING) {
            engine.continueMission(id).getOrThrow()
        }
        assertThat(store.tasks[a]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[b]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.tasks[c]!!.status).isEqualTo(TaskStatus.COMPLETED)
        val synth = store.tasks.values.single { it.title.contains("Synthesize") }
        assertThat(store.deps.values.count { it.taskId == synth.id }).isEqualTo(3)
        assertThat(store.tasks[synth.id]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVIEWING)
        assertThat(store.results.filter { it.taskId == synth.id }).isNotEmpty()
        assertThat(engine.hasRequiredArtifact(id)).isTrue()
        assertThat(store.events.any { it.message.contains("created") || it.type.name.contains("TASK") }).isTrue()
    }

    @Test
    fun rejectsForeignAgent() = runTest {
        val (_, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val result = engine.processAction(id, MissionAction.CreateTask("x", "x", "other"))
        assertThat(result.exceptionOrNull()).isInstanceOf(DomainException.AgentNotInProject::class.java)
    }

    @Test
    fun pauseResumeCancel() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.pauseMission(id).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.PAUSED)
        engine.resumeMission(id).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
        engine.cancelMission(id).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.CANCELLED)
        assertThat(engine.continueMission(id).exceptionOrNull()).isInstanceOf(DomainException.MissionCancelled::class.java)
    }

    @Test
    fun cancelCancelsEveryNonTerminalTask() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val statuses = listOf(
            TaskStatus.PENDING, TaskStatus.READY, TaskStatus.WAITING_FOR_DEPENDENCY,
            TaskStatus.RUNNING, TaskStatus.WAITING_FOR_USER, TaskStatus.WAITING_FOR_AGENT,
            TaskStatus.RETRYING, TaskStatus.BLOCKED, TaskStatus.FAILED,
        )
        statuses.forEachIndexed { index, status ->
            val taskId = engine.processAction(id, MissionAction.CreateTask("t$index", "d", "dev")).getOrThrow().createdTaskId!!
            store.tasks[taskId] = store.tasks[taskId]!!.copy(status = status)
        }

        engine.cancelMission(id).getOrThrow()

        assertThat(store.tasks.values.filter { it.missionId == id }.all { it.status == TaskStatus.CANCELLED }).isTrue()
    }

    @Test
    fun startDoesNotDuplicateMissionStartedEvent() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()

        engine.startMission(id).getOrThrow()

        assertThat(store.events.count { it.missionId == id && it.type == MissionEventType.MISSION_STARTED }).isEqualTo(1)
    }

    @Test
    fun cancelRollsBackMissionWhenTaskCancellationFails() = runTest {
        val (baseStore, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        baseStore.tasks[taskId] = baseStore.tasks[taskId]!!.copy(status = TaskStatus.RUNNING)
        val failingStore = FailingTaskUpdateMissionStore(baseStore)
        val failingEngine = MissionEngine(failingStore, { _, _, _, _ -> TaskWorkResult("done") })

        val result = failingEngine.cancelMission(id)

        assertThat(result.isFailure).isTrue()
        assertThat(baseStore.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
        assertThat(baseStore.tasks[taskId]!!.status).isEqualTo(TaskStatus.RUNNING)
    }

    @Test
    fun submitUserInputRollsBackAuditAndStatusWhenTaskUpdateFails() = runTest {
        val (baseStore, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.RequestUserInput("writes?", "need policy", taskId)).getOrThrow()
        baseStore.tasks[taskId] = baseStore.tasks[taskId]!!.copy(status = TaskStatus.WAITING_FOR_USER)
        val failingStore = FailingTaskUpdateMissionStore(baseStore)
        val failingEngine = MissionEngine(failingStore, { _, _, _, _ -> TaskWorkResult("done") })

        val result = failingEngine.submitUserInput(id, "yes")

        assertThat(result.isFailure).isTrue()
        assertThat(baseStore.missions[id]!!.status).isEqualTo(MissionStatus.WAITING_FOR_USER)
        assertThat(baseStore.tasks[taskId]!!.status).isEqualTo(TaskStatus.WAITING_FOR_USER)
        assertThat(baseStore.events.count { it.type == MissionEventType.USER_INPUT_RECEIVED }).isEqualTo(0)
    }

    @Test
    fun userInputRoundTrip() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        engine.processAction(id, MissionAction.RequestUserInput("writes?", "need policy")).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.WAITING_FOR_USER)
        engine.submitUserInput(id, "yes, queued writes").getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
    }

    @Test
    fun recoversRunningWithResult() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        store.tasks[taskId] = store.tasks[taskId]!!.copy(status = TaskStatus.RUNNING)
        store.results += com.agentflow.domain.model.TaskResult("r", taskId, id, "already", createdAt = 1)
        engine.continueMission(id).getOrThrow()
        assertThat(store.tasks[taskId]!!.status).isEqualTo(TaskStatus.COMPLETED)
    }

    @Test
    fun recoveryOfCompletedSynthesisRestoresArtifactWithoutCreatingDuplicate() = runTest {
        val (store, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val first = engine.processAction(id, MissionAction.CreateTask("Synthesize implementation strategy", "s", "rd", Priority.HIGH)).getOrThrow().createdTaskId!!
        store.tasks[first] = store.tasks[first]!!.copy(status = TaskStatus.RUNNING)
        store.results += com.agentflow.domain.model.TaskResult("r", first, id, "implementation plan", createdAt = 1)

        engine.continueMission(id).getOrThrow()

        assertThat(store.tasks.values.count { it.title == "Synthesize implementation strategy" }).isEqualTo(1)
        assertThat(store.tasks[first]!!.status).isEqualTo(TaskStatus.COMPLETED)
        assertThat(engine.hasRequiredArtifact(id)).isTrue()
        assertThat(engine.latestArtifact(id)?.version).isEqualTo(1)
    }

    @Test
    fun createLimitIsEnforcedWithoutCountingHistoricalEvents() = runTest {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", role = "orchestrator", capabilities = setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        val policy = MissionExecutionPolicy(maxCreatesPerPlanningCycle = 2)
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("done:${task.title}") }, policy = policy)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()

        engine.processAction(id, MissionAction.CreateTask("one", "", "rd")).getOrThrow()
        engine.processAction(id, MissionAction.CreateTask("two", "", "rd")).getOrThrow()
        assertThat(engine.processAction(id, MissionAction.CreateTask("three", "", "rd")).isFailure).isTrue()
    }

    @Test
    fun missionRuntimeLimitFailsExpiredMissionBeforeExecutingTasks() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Developer")
        var workerCalls = 0
        val policy = MissionExecutionPolicy(maxMissionRuntimeMs = 1_000L)
        val engine = MissionEngine(
            store,
            { task, _, _, _ ->
                workerCalls++
                TaskWorkResult("done:${task.title}")
            },
            policy = policy,
            now = { 10_000L },
        )
        val id = engine.createMission("p", "expired", "d").getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(
            status = MissionStatus.EXECUTING,
            startedAt = 8_000L,
        )
        engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow()

        val result = engine.continueMission(id).getOrThrow()

        assertThat(result.workRemaining).isFalse()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.FAILED)
        assertThat(workerCalls).isEqualTo(0)
        assertThat(store.events.any { it.type == MissionEventType.MISSION_FAILED && it.message.contains("runtime") }).isTrue()
    }

    @Test
    fun recoverActiveDoesNotResetTaskAlreadyInFlight() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Developer")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MissionEngine(
            store,
            { task, _, _, _ ->
                started.complete(Unit)
                release.await()
                TaskWorkResult("done:${task.title}")
            },
        )
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!

        val continuation = launch { engine.continueMission(id) }
        started.await()
        assertThat(store.tasks[taskId]!!.status).isEqualTo(TaskStatus.RUNNING)

        val recovery = launch { engine.recoverActive() }
        release.complete(Unit)
        continuation.join()
        recovery.join()
        assertThat(store.tasks[taskId]!!.status).isEqualTo(TaskStatus.COMPLETED)
    }

    @Test
    fun continueMissionRecoversInterruptedPlanning() = runTest {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent(
            "rd", "R&D", role = "orchestrator",
            capabilities = setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE),
        )
        var plannerCalls = 0
        val engine = MissionEngine(
            store,
            { _, _, _, _ -> TaskWorkResult("done") },
            planner = MissionPlanner {
                plannerCalls++
                "{}"
            },
            planningRequired = false,
        )
        engine.bindPlanIngest { _, _ -> }
        val id = engine.createMission("p", "recover planning", "d").getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.PLANNING)

        engine.continueMission(id).getOrThrow()

        assertThat(plannerCalls).isEqualTo(1)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
    }

    @Test
    fun concurrentStartDoesNotPlanSameMissionTwice() = runTest {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent(
            "rd", "R&D", role = "orchestrator",
            capabilities = setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE),
        )
        val planningStarted = CompletableDeferred<Unit>()
        val releasePlanning = CompletableDeferred<Unit>()
        var plannerCalls = 0
        val engine = MissionEngine(
            store,
            { _, _, _, _ -> TaskWorkResult("done") },
            planner = MissionPlanner {
                plannerCalls++
                planningStarted.complete(Unit)
                releasePlanning.await()
                "{}"
            },
            planningRequired = false,
        )
        engine.bindPlanIngest { _, _ -> }
        val id = engine.createMission("p", "concurrent start", "d").getOrThrow()

        val first = launch { engine.startMission(id) }
        planningStarted.await()
        val second = engine.startMission(id)
        releasePlanning.complete(Unit)
        first.join()

        assertThat(second.isSuccess).isTrue()
        assertThat(plannerCalls).isEqualTo(1)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.EXECUTING)
    }


    @Test
    fun cancelRunningAiCancelsActualWorkerJob() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Developer")
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val engine = MissionEngine(store, { task, _, _, _ ->
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
                TaskWorkResult("never")
            } catch (e: CancellationException) {
                cancelled.complete(Unit)
                throw e
            }
        })
        val id = engine.createMission("p", "cancel", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val continuation = launch { engine.continueMission(id) }
        started.await()

        engine.cancelMission(id).getOrThrow()
        cancelled.await()
        continuation.join()

        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.CANCELLED)
        assertThat(store.tasks[taskId]!!.status).isEqualTo(TaskStatus.CANCELLED)
    }

    @Test
    fun pauseRunningAiCancelsWithoutWaitingForWorker() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Developer")
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val engine = MissionEngine(store, { _, _, _, _ ->
            started.complete(Unit)
            try { CompletableDeferred<Unit>().await(); TaskWorkResult("never") }
            catch (e: CancellationException) { cancelled.complete(Unit); throw e }
        })
        val id = engine.createMission("p", "pause", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val continuation = launch { engine.continueMission(id) }
        started.await()

        engine.pauseMission(id).getOrThrow()

        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.PAUSED)
        assertThat(store.tasks[taskId]!!.status).isEqualTo(TaskStatus.READY)
        cancelled.await()
        continuation.join()
    }

    @Test
    fun staleCompletionCannotResurrectCancelledTask() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Developer")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MissionEngine(store, { _, _, _, _ ->
            started.complete(Unit)
            try { CompletableDeferred<Unit>().await() }
            catch (_: CancellationException) { withContext(NonCancellable) { release.await() } }
            TaskWorkResult("late")
        })
        val id = engine.createMission("p", "stale", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val taskId = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val continuation = launch { engine.continueMission(id) }
        started.await()
        engine.cancelMission(id).getOrThrow()
        release.complete(Unit)
        continuation.join()

        assertThat(store.tasks[taskId]!!.status).isEqualTo(TaskStatus.CANCELLED)
        assertThat(store.results.count { it.taskId == taskId }).isEqualTo(0)
    }

    @Test
    fun failedDependencyBlocksDependentAndFailsMission() = runTest {
        val store = InMemoryMissionStore()
        store.agents["dev"] = agent("dev", "Developer")
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "failure", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "dev")).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.AddDependency(b, a)).getOrThrow()
        store.tasks[a] = store.tasks[a]!!.copy(status = TaskStatus.FAILED)

        engine.continueMission(id).getOrThrow()

        assertThat(store.tasks[b]!!.status).isEqualTo(TaskStatus.BLOCKED)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.FAILED)
    }



    @Test
    fun missionWithNoRunnableWorkCannotRemainExecuting() = runTest {
        val store = InMemoryMissionStore()
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val id = engine.createMission("p", "stuck", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.tasks["stuck-task"] = Task(
            id = "stuck-task", missionId = id, createdByType = CreatedByType.ENGINE, createdById = "engine",
            assignedAgentId = "", title = "stuck", description = "", status = TaskStatus.PENDING,
            createdAt = 1, updatedAt = 1,
        )

        engine.continueMission(id).getOrThrow()

        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.FAILED)
    }

    @Test
    fun plannerCancellationEscapesWithoutWaitingForUser() = runTest {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", role = "orchestrator", capabilities = setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        val engine = MissionEngine(
            store,
            { _, _, _, _ -> TaskWorkResult("ok") },
            planner = MissionPlanner { throw CancellationException("planner cancelled") },
            planningRequired = true,
        )
        engine.bindPlanIngest { _, _ -> }
        val id = engine.createMission("p", "cancel planner", "d").getOrThrow()

        try {
            engine.startMission(id)
            throw AssertionError("expected planner cancellation")
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("planner cancelled")
        }
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.PLANNING)
    }

    @Test
    fun cycleRejected() = runTest {
        val (_, engine) = seeded()
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        val a = engine.processAction(id, MissionAction.CreateTask("A", "a", "dev")).getOrThrow().createdTaskId!!
        val b = engine.processAction(id, MissionAction.CreateTask("B", "b", "ui")).getOrThrow().createdTaskId!!
        engine.processAction(id, MissionAction.AddDependency(b, a)).getOrThrow()
        val cycle = engine.processAction(id, MissionAction.AddDependency(a, b))
        assertThat(cycle.isFailure).isTrue()
    }

    @Test
    fun continueMissionResumesRevisionRequiredWork() = runTest {
        val store = InMemoryMissionStore()
        store.agents["rd"] = agent("rd", "R&D", capabilities = setOf(AgentCapability.ORCHESTRATE, AgentCapability.SYNTHESIZE))
        store.agents["dev"] = agent("dev", "Dev", capabilities = setOf(AgentCapability.CODE))
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("fixed") })
        val id = engine.createMission("p", "revision", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVISION_REQUIRED)
        engine.processAction(id, MissionAction.CreateTask("Fix finding", "fix", "dev")).getOrThrow()

        val result = engine.continueMission(id).getOrThrow()

        assertThat(result.workRemaining).isFalse()
        assertThat(store.tasks.values.first { it.title == "Fix finding" }.status).isEqualTo(TaskStatus.COMPLETED)
    }
}

private class FailingTaskUpdateMissionStore(
    private val delegate: InMemoryMissionStore,
) : MissionStore by delegate {
    override suspend fun updateTask(task: com.agentflow.domain.model.Task) {
        throw IllegalStateException("task update failed")
    }
}
