package com.agentflow.domain.mission

import com.agentflow.domain.agent.AgentDuties
import com.agentflow.domain.inspector.ArtifactService
import com.agentflow.domain.inspector.InMemoryArtifactCatalog
import com.agentflow.domain.persistence.MissionPersistenceConsistency
import com.agentflow.domain.model.CreatedByType
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Priority
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskResult
import com.agentflow.domain.model.TaskStatus
import com.agentflow.domain.task.TaskGraph
import com.agentflow.domain.task.TaskGraphValidator
import com.agentflow.domain.task.TaskReadinessEvaluator
import com.agentflow.domain.task.TaskWorkResult
import com.agentflow.domain.task.TaskFailureKind
import com.agentflow.domain.task.TaskWorker
import com.agentflow.domain.validation.DomainException
import com.agentflow.domain.validation.MissionStateMachine
import com.agentflow.domain.validation.TaskStateMachine
import com.agentflow.domain.retry.WorkFailureClassifier
import com.agentflow.domain.retry.WorkFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * LLM proposes. Engine decides. Room/store is source of truth.
 */
class MissionEngine(
    private val store: MissionStore,
    private val worker: TaskWorker,
    private val policy: MissionExecutionPolicy = MissionExecutionPolicy(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val artifacts: ArtifactService = ArtifactService(InMemoryArtifactCatalog()),
    private val contextFactory: MissionContextFactory? = null,
    private val planner: MissionPlanner? = null,
    private var ingestPlan: (suspend (String, String) -> Unit)? = null,
    private val planningRequired: Boolean = false,
) {
    fun bindPlanIngest(ingest: suspend (String, String) -> Unit) {
        ingestPlan = ingest
    }

    fun planningWired(): Boolean = planner != null && ingestPlan != null
    private val mutex = Mutex()
    private val artifactMutex = Mutex()
    private val inFlight = mutableSetOf<String>()
    private val runningJobs = mutableMapOf<String, Job>()
    private val planningInFlight = mutableSetOf<String>()
    private val resumeTarget = mutableMapOf<String, MissionStatus>()
    private val createdThisCycle = mutableMapOf<String, Int>()

    suspend fun createMission(projectId: String, title: String, description: String, createdBy: String = "user"): Result<String> =
        suspendRunCatching {
            val stamp = now()
            val mission = Mission(
                id = Ids.new(),
                projectId = projectId,
                title = title,
                description = description,
                status = MissionStatus.CREATED,
                createdAt = stamp,
                updatedAt = stamp,
                createdBy = createdBy,
            )
            store.transaction {
                store.insertMission(mission)
                store.appendEvent(event(mission.id, MissionEventType.MISSION_CREATED, "Mission created: $title"))
            }
            mission.id
        }

    suspend fun startMission(missionId: String): Result<Unit> = suspendRunCatching {
        val claimed = claimPlanning(missionId, allowCreated = true)
        if (!claimed) return@suspendRunCatching Unit
        try {
            runPlanning(requireMission(missionId))
            mutex.withLock {
                val afterPlan = requireMission(missionId)
                if (afterPlan.status == MissionStatus.PLANNING) {
                    setStatus(afterPlan, MissionStatus.EXECUTING, "Mission started")
                }
            }
        } finally {
            releasePlanning(missionId)
        }
    }

    suspend fun processAction(missionId: String, action: MissionAction): Result<MissionActionResult> {
        if (action is MissionAction.CreateArtifact) {
            val mission = mutex.withLock { requireMission(missionId) }
            return suspendRunCatching { persistArtifact(mission, action) }
        }
        val result = mutex.withLock {
            suspendRunCatching {
                store.transaction {
                    applyAction(requireMission(missionId), action)
                }
            }
        }
        if (result.isSuccess && action is MissionAction.CompleteTask) {
            val task = mutex.withLock { store.getTask(action.taskId) }
            if (task != null && task.title.startsWith("Synthesize", ignoreCase = true)) {
                val mission = mutex.withLock { store.getMission(task.missionId) }
                if (mission != null && !hasRequiredArtifact(task.missionId)) {
                    persistArtifact(mission, MissionAction.CreateArtifact(
                        type = com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN,
                        name = "Implementation plan",
                        content = action.content,
                    ))
                }
            }
        }
        if (result.isSuccess) recoverRequiredArtifacts(missionId)
        return result
    }

    suspend fun processActionBatch(
        missionId: String,
        actions: List<MissionAction>,
        validateAction: (suspend (Mission, MissionAction) -> Unit)? = null,
    ): Result<List<MissionActionResult>> = mutex.withLock {
        suspendRunCatching {
            store.transaction {
                actions.map { action ->
                    val current = requireMission(missionId)
                    validateAction?.invoke(current, action)
                    applyAction(current, action)
                }
            }
        }
    }

    suspend fun pauseMission(missionId: String): Result<Unit> = suspendRunCatching {
        val jobs = mutex.withLock {
            val mission = requireMission(missionId)
            if (MissionStateMachine.isTerminal(mission.status)) throw DomainException.InvalidAction("terminal")
            resumeTarget[missionId] = mission.status
            val activeTaskIds = store.listTasks(missionId).filter { it.status == TaskStatus.RUNNING }.map { it.id }.toSet()
            val activeJobs = runningJobs.filterKeys { it in activeTaskIds }.values.toList()
            store.transaction {
                setStatus(mission, MissionStatus.PAUSED)
                store.listTasks(missionId)
                    .filter { it.status == TaskStatus.RUNNING }
                    .forEach { task -> updateTask(task.copy(status = TaskStatus.READY, updatedAt = now())) }
            }
            activeJobs
        }
        jobs.forEach { it.cancel(CancellationException("mission paused")) }
        Unit
    }

    suspend fun resumeMission(missionId: String): Result<Unit> = mutex.withLock {
        suspendRunCatching {
            val mission = requireMission(missionId)
            if (mission.status != MissionStatus.PAUSED) throw DomainException.InvalidAction("not paused")
            val target = resumeTarget.remove(missionId) ?: MissionStatus.EXECUTING
            setStatus(mission, target)
        }
    }

    suspend fun cancelMission(missionId: String): Result<Unit> = suspendRunCatching {
        val jobs = mutex.withLock {
            val activeTaskIds = store.listTasks(missionId).filter { !TaskStateMachine.isTerminal(it.status) }.map { it.id }.toSet()
            val activeJobs = runningJobs.filterKeys { it in activeTaskIds }.values.toList()
            store.transaction {
                val mission = requireMission(missionId)
                setStatus(mission, MissionStatus.CANCELLED)
                store.listTasks(missionId)
                    .filter { !TaskStateMachine.isTerminal(it.status) }
                    .forEach { task ->
                        val cancelledStatus = TaskStateMachine.transition(task.status, TaskStatus.CANCELLED)
                        updateTask(task.copy(status = cancelledStatus, completedAt = now(), updatedAt = now()))
                    }
            }
            activeJobs
        }
        jobs.forEach { it.cancel(CancellationException("mission cancelled")) }
        Unit
    }

    suspend fun failMission(missionId: String, reason: String): Result<Unit> = mutex.withLock {
        suspendRunCatching {
            val mission = requireMission(missionId)
            if (mission.status != MissionStatus.FAILED && !MissionStateMachine.isTerminal(mission.status)) {
                setStatus(mission, MissionStatus.FAILED, reason.take(800))
            }
        }
    }

    suspend fun submitUserInput(missionId: String, answer: String): Result<Unit> = mutex.withLock {
        suspendRunCatching {
            val mission = requireMission(missionId)
            val target = resumeTarget[missionId] ?: MissionStatus.EXECUTING
            store.transaction {
                store.appendEvent(event(missionId, MissionEventType.USER_INPUT_RECEIVED, answer.take(500)))
                if (mission.status == MissionStatus.WAITING_FOR_USER) setStatus(mission, target)
                store.listTasks(missionId)
                    .filter { it.status == TaskStatus.WAITING_FOR_USER }
                    .forEach { task ->
                        updateTask(transitionTask(task, TaskStatus.READY))
                    }
            }
            resumeTarget.remove(missionId)
            Unit
        }
    }

    private suspend fun claimPlanning(missionId: String, allowCreated: Boolean): Boolean = mutex.withLock {
        val mission = requireMission(missionId)
        when {
            mission.status == MissionStatus.PLANNING -> {
                if (missionId in planningInFlight) return@withLock false
                planningInFlight.add(missionId)
                true
            }
            allowCreated && mission.status == MissionStatus.CREATED -> {
                setStatus(mission, MissionStatus.PLANNING)
                planningInFlight.add(missionId)
                true
            }
            else -> throw DomainException.InvalidAction("mission is not startable from ${mission.status}")
        }
    }

    private suspend fun releasePlanning(missionId: String) = mutex.withLock {
        planningInFlight.remove(missionId)
    }

    suspend fun continueMission(missionId: String): Result<ContinueOutcome> = suspendRunCatching {
        val planningClaimed = mutex.withLock {
            val mission = store.getMission(missionId) ?: throw DomainException.MissionNotFound(missionId)
            if (mission.status != MissionStatus.PLANNING) return@withLock false
            if (isRuntimeExpired(mission)) {
                setStatus(mission, MissionStatus.FAILED, "Mission runtime limit exceeded (${policy.maxMissionRuntimeMs} ms)")
                return@withLock false
            }
            if (!planningInFlight.add(missionId)) return@withLock false
            true
        }
        if (planningClaimed) {
            try {
                runPlanning(requireMission(missionId))
                mutex.withLock {
                    val afterPlan = requireMission(missionId)
                    if (afterPlan.status == MissionStatus.PLANNING) setStatus(afterPlan, MissionStatus.EXECUTING)
                }
            } finally {
                releasePlanning(missionId)
            }
        }

        var hops = 0
        while (hops++ < policy.maxSchedulerHops) {
            val prep = mutex.withLock {
                val mission = store.getMission(missionId) ?: throw DomainException.MissionNotFound(missionId)
                when {
                    mission.status == MissionStatus.PAUSED -> throw DomainException.MissionPaused(missionId)
                    mission.status == MissionStatus.CANCELLED -> throw DomainException.MissionCancelled(missionId)
                    mission.status == MissionStatus.WAITING_FOR_USER -> throw DomainException.UserInputRequired(missionId)
                    mission.status == MissionStatus.REVISION_REQUIRED -> {
                        setStatus(mission, MissionStatus.EXECUTING, "resuming revision work")
                        true
                    }
                    mission.status == MissionStatus.EXECUTING || mission.status == MissionStatus.RESEARCHING -> true
                    else -> false
                }
            }
            if (!prep) return@suspendRunCatching ContinueOutcome()

            mutex.withLock {
                val current = requireMission(missionId)
                if (isRuntimeExpired(current)) {
                    setStatus(current, MissionStatus.FAILED, "Mission runtime limit exceeded (${policy.maxMissionRuntimeMs} ms)")
                    return@withLock
                }
            }
            recoverStale(missionId)
            artifacts.recoverPending()
            recoverRequiredArtifacts(missionId)
            mutex.withLock { refreshReadiness(missionId) }

            val batch = claimReadyTasks(missionId)
            if (batch.isNotEmpty()) {
                executeClaimedTasks(batch)
                continue
            }

            recoverRequiredArtifacts(missionId)
            mutex.withLock {
                maybeFinish(missionId)
                refreshReadiness(missionId)
            }
            val state = mutex.withLock {
                val mission = requireMission(missionId)
                val tasks = store.listTasks(missionId)
                val ready = tasks.any { it.status == TaskStatus.READY }
                val remaining = tasks.any { it.status in setOf(TaskStatus.READY, TaskStatus.RUNNING, TaskStatus.PENDING, TaskStatus.WAITING_FOR_DEPENDENCY) }
                Triple(ready, remaining, mission.status in setOf(MissionStatus.EXECUTING, MissionStatus.RESEARCHING))
            }
            if (!state.first || !state.third) {
                return@suspendRunCatching ContinueOutcome(workRemaining = state.second && state.third)
            }
        }
        val remaining = mutex.withLock {
            val mission = requireMission(missionId)
            val tasks = store.listTasks(missionId)
            tasks.any { it.status in setOf(TaskStatus.READY, TaskStatus.RUNNING, TaskStatus.PENDING, TaskStatus.WAITING_FOR_DEPENDENCY) } &&
                mission.status in setOf(MissionStatus.EXECUTING, MissionStatus.RESEARCHING)
        }
        ContinueOutcome(exhaustedHops = true, workRemaining = remaining)
    }

    private suspend fun claimReadyTasks(missionId: String): List<Task> = mutex.withLock {
        val mission = requireMission(missionId)
        if (mission.status !in setOf(MissionStatus.EXECUTING, MissionStatus.RESEARCHING)) return@withLock emptyList()
        val graph = graph(missionId)
        val ready = TaskReadinessEvaluator.readyTasks(graph, mission.status)
            .filter { it.id !in inFlight }
        val running = graph.tasks.count { it.status == TaskStatus.RUNNING || it.id in inFlight }
        val slots = (policy.maxConcurrentTasks - running).coerceAtLeast(0)
        val batch = ready.take(slots)
        batch.forEach { task ->
            val runningStatus = TaskStateMachine.transition(task.status, TaskStatus.RUNNING)
            inFlight.add(task.id)
            store.persistTaskEvent(
                task.copy(status = runningStatus, startedAt = now(), updatedAt = now()),
                event(mission.id, MissionEventType.TASK_STARTED, task.title, task.id),
            )
        }
        batch.mapNotNull { store.getTask(it.id) }
    }

    private suspend fun executeClaimedTasks(tasks: List<Task>) = coroutineScope {
        val deferred = tasks.map { task ->
            val job = async(start = CoroutineStart.LAZY) { runTask(task) }
            mutex.withLock { runningJobs[task.id] = job }
            job
        }
        deferred.forEach { it.start() }
        deferred.awaitAll()
    }

    suspend fun recoverActive() {
        val missions = mutex.withLock { store.listActiveMissions() }
        artifacts.recoverPending()
        missions.forEach { mission ->
            recoverStale(mission.id)
            recoverRequiredArtifacts(mission.id)
            auditPersistence(mission.id)
        }
    }

    private suspend fun auditPersistence(missionId: String) {
        val mission = store.getMission(missionId) ?: return
        val tasks = store.listTasks(missionId)
        val results = store.resultsForMission(missionId)
        val events = store.events(missionId)
        val artifactsWithContent = artifacts.versions(missionId).map { artifact ->
            artifact to artifacts.catalogRead(artifact.id)
        }
        val report = MissionPersistenceConsistency.check(mission, tasks, results, events, artifactsWithContent)
        if (report.criticalErrors.isNotEmpty()) {
            mutex.withLock {
                val current = store.getMission(missionId) ?: return@withLock
                if (!MissionStateMachine.isTerminal(current.status)) {
                    setStatus(
                        current,
                        MissionStatus.FAILED,
                        "persistence recovery failed: ${report.criticalErrors.joinToString("; ").take(800)}",
                    )
                }
            }
        }
    }

    private suspend fun applyAction(mission: Mission, action: MissionAction): MissionActionResult {
        if (mission.status == MissionStatus.CANCELLED) throw DomainException.MissionCancelled(mission.id)
        return when (action) {
            is MissionAction.CreateTask -> createTask(mission, action)
            is MissionAction.AssignTask -> {
                val task = requireTaskInMission(mission, action.taskId)
                val agent = requireAgentInProject(action.agentId, mission.projectId)
                updateTask(task.copy(assignedAgentId = agent.id, updatedAt = now()))
                store.appendEvent(event(mission.id, MissionEventType.TASK_ASSIGNED, "Assigned ${agent.name}", task.id))
                MissionActionResult(true, "assigned")
            }
            is MissionAction.AddDependency -> {
                requireTaskInMission(mission, action.taskId)
                requireTaskInMission(mission, action.dependsOnTaskId)
                addDependency(mission, action.taskId, action.dependsOnTaskId, action.type)
                MissionActionResult(true, "dependency added")
            }
            is MissionAction.RemoveDependency -> {
                val id = action.dependencyId
                if (id.contains(':')) {
                    val taskId = id.substringBefore(':')
                    val depends = id.substringAfter(':')
                    store.listDependencies(mission.id)
                        .firstOrNull { it.taskId == taskId && it.dependsOnTaskId == depends }
                        ?.let { store.deleteDependency(it.id) }
                } else {
                    val dependency = store.listDependencies(mission.id).firstOrNull { it.id == id }
                        ?: throw DomainException.InvalidAction("dependency not in mission")
                    store.deleteDependency(dependency.id)
                }
                MissionActionResult(true, "dependency removed")
            }
            is MissionAction.RequestUserInput -> {
                resumeTarget[mission.id] = mission.status
                setStatus(mission, MissionStatus.WAITING_FOR_USER)
                action.taskId?.let { id ->
                    val task = requireTaskInMission(mission, id)
                    updateTask(transitionTask(task, TaskStatus.WAITING_FOR_USER))
                }
                store.appendEvent(event(mission.id, MissionEventType.USER_INPUT_REQUIRED, action.question, action.taskId))
                MissionActionResult(true, "waiting for user")
            }
            is MissionAction.CompleteTask -> {
                requireTaskInMission(mission, action.taskId)
                completeTask(action.taskId, action.content, action.confidence)
                MissionActionResult(true, "completed")
            }
            is MissionAction.FailTask -> {
                val task = requireTaskInMission(mission, action.taskId)
                val updated = transitionTask(task, TaskStatus.FAILED)
                updateTask(updated.copy(completedAt = now()))
                store.appendEvent(event(mission.id, MissionEventType.TASK_FAILED, action.reason, task.id))
                MissionActionResult(true, "failed")
            }
            is MissionAction.RetryTask -> {
                val task = requireTaskInMission(mission, action.taskId)
                if (task.retryCount >= policy.maxTaskRetries) throw DomainException.PolicyViolation("retries exhausted")
                val updated = TaskStateMachine.transition(task.status, TaskStatus.READY)
                updateTask(task.copy(status = updated, retryCount = task.retryCount + 1, updatedAt = now()))
                MissionActionResult(true, "retry scheduled")
            }
            MissionAction.RequestMoreResearch -> {
                setStatus(mission, MissionStatus.RESEARCHING)
                MissionActionResult(true, "research")
            }
            MissionAction.RequestSynthesis -> createSynthesis(mission)
            MissionAction.RequestReview -> {
                if (!hasRequiredArtifact(mission.id)) {
                    return createSynthesis(mission)
                }
                setStatus(mission, MissionStatus.REVIEWING)
                MissionActionResult(true, "review")
            }
            MissionAction.PauseMission -> {
                resumeTarget[mission.id] = mission.status
                setStatus(mission, MissionStatus.PAUSED)
                MissionActionResult(true, "paused")
            }
            MissionAction.ResumeMission -> {
                val target = resumeTarget.remove(mission.id) ?: MissionStatus.EXECUTING
                setStatus(mission, target)
                MissionActionResult(true, "resumed")
            }
            MissionAction.CancelMission -> {
                setStatus(mission, MissionStatus.CANCELLED)
                MissionActionResult(true, "cancelled")
            }
            MissionAction.Continue -> MissionActionResult(true, "continue")
            is MissionAction.CreateArtifact -> persistArtifact(mission, action)
        }
    }

    private suspend fun createTask(mission: Mission, action: MissionAction.CreateTask): MissionActionResult {
        val tasks = store.listTasks(mission.id)
        TaskGraphValidator.validateTaskCount(tasks, policy)
        val created = createdThisCycle[mission.id] ?: 0
        if (created >= policy.maxCreatesPerPlanningCycle) {
            throw DomainException.TaskLimitExceeded("planning cycle cap")
        }
        val agent = requireAgentInProject(action.assignedAgentId, mission.projectId)
        action.parentTaskId?.let { parent ->
            val p = store.getTask(parent) ?: throw DomainException.TaskNotFound(parent)
            if (p.missionId != mission.id) throw DomainException.InvalidAction("parent cross-mission")
        }
        val stamp = now()
        val task = Task(
            id = Ids.new(),
            missionId = mission.id,
            parentTaskId = action.parentTaskId,
            createdByType = CreatedByType.ENGINE,
            createdById = "engine",
            assignedAgentId = agent.id,
            title = action.title,
            description = action.description,
            status = TaskStatus.PENDING,
            priority = action.priority,
            createdAt = stamp,
            updatedAt = stamp,
        )
        store.insertTask(task)
        createdThisCycle[mission.id] = created + 1
        store.appendEvent(event(mission.id, MissionEventType.TASK_CREATED, task.title, task.id))
        refreshReadiness(mission.id)
        return MissionActionResult(true, "created", task.id)
    }

    private suspend fun createSynthesis(mission: Mission): MissionActionResult {
        val existingSynthesis = store.listTasks(mission.id)
            .firstOrNull {
                it.title.equals("Synthesize implementation strategy", ignoreCase = true) &&
                    it.status !in setOf(TaskStatus.CANCELLED, TaskStatus.FAILED)
            }
        if (existingSynthesis != null) {
            return MissionActionResult(true, "synthesis already scheduled", existingSynthesis.id)
        }

        val rd = AgentDuties.synthesizer(store.listAgents(mission.projectId))
            ?: throw DomainException.AgentNotFound("orchestrator")
        val existing = store.listTasks(mission.id)
        val created = createTask(
            mission,
            MissionAction.CreateTask(
                title = "Synthesize implementation strategy",
                description = "Combine completed specialist results into one implementation plan.",
                assignedAgentId = rd.id,
                priority = Priority.HIGH,
                isSynthesis = true,
            ),
        )
        val synthId = created.createdTaskId!!
        existing.filter { it.status != TaskStatus.CANCELLED }.forEach { prior ->
            addDependency(mission, synthId, prior.id)
        }
        return created
    }

    private suspend fun addDependency(
        mission: Mission,
        taskId: String,
        dependsOn: String,
        type: com.agentflow.domain.model.DependencyType = com.agentflow.domain.model.DependencyType.REQUIRED,
    ) {
        val graph = graph(mission.id)
        val dep = TaskDependency(
            id = Ids.new(),
            missionId = mission.id,
            taskId = taskId,
            dependsOnTaskId = dependsOn,
            type = type,
            createdAt = now(),
        )
        TaskGraphValidator.validateNewDependency(graph, dep, policy)
        store.insertDependency(dep)
        refreshReadiness(mission.id)
    }

    private suspend fun runTask(task: Task) {
        try {
            val current = mutex.withLock { store.getTask(task.id) } ?: return
            if (current.status != TaskStatus.RUNNING) return
            val mission = mutex.withLock { store.getMission(current.missionId) } ?: return
            if (mission.status !in setOf(MissionStatus.EXECUTING, MissionStatus.RESEARCHING)) return
            val agent = mutex.withLock { store.getAgent(current.assignedAgentId) }
                ?: throw DomainException.AgentNotFound(current.assignedAgentId)
            val deps = mutex.withLock { store.listDependencies(mission.id).filter { it.taskId == current.id } }
            val depResults = mutex.withLock { deps.flatMap { store.resultsForTask(it.dependsOnTaskId) } }
            val context = contextFactory?.build(store, mission, current, agent, depResults)
                ?: depResults.joinToString("\n") { it.content.take(1_500) }
            val work = try {
                worker.execute(current, agent, depResults, context)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Exception) {
                val kind = when (WorkFailureClassifier.classify(t)) {
                    WorkFailureKind.TRANSIENT -> TaskFailureKind.TRANSIENT
                    else -> TaskFailureKind.PERMANENT
                }
                TaskWorkResult(t.message ?: "execution failed", success = false, failureKind = kind)
            }
            var synthesisArtifact: Pair<Mission, String>? = null
            val executionStillActive = currentCoroutineContext().isActive
            withContext(NonCancellable) {
                mutex.withLock {
                if (!executionStillActive) return@withLock
                val latestTask = store.getTask(current.id) ?: return@withLock
                val latestMission = store.getMission(current.missionId) ?: return@withLock
                if (latestTask.status != TaskStatus.RUNNING || latestMission.status !in setOf(MissionStatus.EXECUTING, MissionStatus.RESEARCHING)) return@withLock
                if (work.success) {
                    val result = TaskResult(Ids.new(), latestTask.id, latestTask.missionId, work.content, confidence = work.confidence, createdAt = now())
                    val completed = latestTask.copy(status = TaskStatus.COMPLETED, completedAt = now(), updatedAt = now())
                    store.transaction {
                        store.saveResult(result)
                        store.updateTask(completed)
                        store.appendEvent(event(latestTask.missionId, MissionEventType.TASK_COMPLETED, latestTask.title, latestTask.id))
                    }
                    if (latestTask.title.startsWith("Synthesize", ignoreCase = true)) synthesisArtifact = latestMission to work.content
                } else if (work.failureKind == TaskFailureKind.TRANSIENT && latestTask.retryCount < policy.maxTaskRetries) {
                    updateTask(latestTask.copy(status = TaskStatus.READY, retryCount = latestTask.retryCount + 1, updatedAt = now()))
                } else {
                    val failed = latestTask.copy(status = TaskStatus.FAILED, completedAt = now(), updatedAt = now())
                    updateTask(failed)
                    store.appendEvent(event(latestMission.id, MissionEventType.TASK_FAILED, work.content.take(200), latestTask.id))
                }
                }
            }
            synthesisArtifact?.let { (m, content) ->
                persistArtifact(m, MissionAction.CreateArtifact(
                    type = com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN,
                    name = "Implementation plan",
                    content = content,
                ))
            }
            mutex.withLock { refreshReadiness(task.missionId) }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    inFlight.remove(task.id)
                    runningJobs.remove(task.id)
                }
            }
        }
    }

    private suspend fun completeTask(taskId: String, content: String, confidence: Double?) {
        val task = store.getTask(taskId) ?: throw DomainException.TaskNotFound(taskId)
        if (task.status == TaskStatus.COMPLETED && store.resultsForTask(task.id).isNotEmpty()) return
        val result = TaskResult(
            id = Ids.new(),
            taskId = task.id,
            missionId = task.missionId,
            content = content,
            confidence = confidence,
            createdAt = now(),
        )
        val completedStatus = TaskStateMachine.transition(task.status, TaskStatus.COMPLETED)
        val completed = task.copy(status = completedStatus, completedAt = now(), updatedAt = now())
        store.transaction {
            store.saveResult(result)
            store.updateTask(completed)
            store.appendEvent(event(task.missionId, MissionEventType.TASK_COMPLETED, task.title, task.id))
        }
        refreshReadiness(task.missionId)
    }

    private suspend fun recoverStale(missionId: String) {
        val stale = mutex.withLock {
            store.listTasks(missionId).filter { it.status == TaskStatus.RUNNING && it.id !in inFlight }
        }
        stale.forEach { task ->
            val result = mutex.withLock { store.resultsForTask(task.id).firstOrNull() }
            if (result != null) {
                val synthesis = mutex.withLock {
                    val current = store.getTask(task.id) ?: return@withLock null
                    if (current.status != TaskStatus.RUNNING) return@withLock null
                    val completed = transitionTask(current, TaskStatus.COMPLETED).copy(completedAt = now())
                    store.persistTaskEvent(completed, event(current.missionId, MissionEventType.TASK_COMPLETED, "recovered ${current.title}", current.id))
                    if (current.title.startsWith("Synthesize", ignoreCase = true)) store.getMission(current.missionId) to result.content else null
                }
                if (synthesis != null) {
                    val (mission, content) = synthesis
                    if (mission != null && !hasRequiredArtifact(mission.id)) {
                        persistArtifact(mission, MissionAction.CreateArtifact(
                            type = com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN,
                            name = "Implementation plan",
                            content = content,
                        ))
                    }
                }
            } else {
                mutex.withLock {
                    val current = store.getTask(task.id) ?: return@withLock
                    if (current.status == TaskStatus.RUNNING && current.id !in inFlight) {
                        updateTask(transitionTask(current, TaskStatus.READY))
                    }
                }
            }
        }
    }

    private suspend fun recoverRequiredArtifacts(missionId: String) {
        if (hasRequiredArtifact(missionId)) return
        val mission = store.getMission(missionId) ?: return
        val synthesis = store.listTasks(missionId).firstOrNull {
            it.title.startsWith("Synthesize", ignoreCase = true) && it.status == TaskStatus.COMPLETED
        } ?: return
        val result = store.resultsForTask(synthesis.id).firstOrNull() ?: return
        persistArtifact(mission, MissionAction.CreateArtifact(
            type = com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN,
            name = "Implementation plan",
            content = result.content,
        ))
    }

    private suspend fun refreshReadiness(missionId: String) {
        val mission = store.getMission(missionId) ?: return
        val graph = graph(missionId)
        graph.tasks.forEach { task ->
            if (task.status in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.SKIPPED, TaskStatus.RUNNING, TaskStatus.WAITING_FOR_USER)) return@forEach
            val next = TaskReadinessEvaluator.evaluate(task, graph, mission.status)
            if (next != task.status && TaskStateMachine.canTransition(task.status, next)) {
                updateTask(task.copy(status = next, updatedAt = now()))
            }
        }
    }

    private suspend fun maybeFinish(missionId: String) {
        val mission = store.getMission(missionId) ?: return
        val tasks = store.listTasks(missionId)
        if (tasks.isEmpty()) return
        val failed = tasks.any { it.status == TaskStatus.FAILED }
        if (failed) {
            setStatus(mission, MissionStatus.FAILED, "task failure prevents mission completion")
            return
        }
        val open = tasks.filter { it.status !in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.SKIPPED, TaskStatus.FAILED) }
        if (open.isNotEmpty()) {
            val hasActionableWork = open.any { it.status in setOf(TaskStatus.READY, TaskStatus.RUNNING, TaskStatus.RETRYING, TaskStatus.WAITING_FOR_USER, TaskStatus.WAITING_FOR_AGENT) }
            if (!hasActionableWork) {
                setStatus(mission, MissionStatus.FAILED, "mission has no runnable work")
            }
            return
        }
        if (!hasRequiredArtifact(missionId)) {
            if (AgentDuties.synthesizer(store.listAgents(mission.projectId)) != null) {
                createSynthesis(mission)
                refreshReadiness(missionId)
            }
            return
        }
        setStatus(mission, MissionStatus.REVIEWING)
    }

    private suspend fun runPlanning(mission: Mission) {
        createdThisCycle[mission.id] = 0
        if (planner == null || ingestPlan == null) {
            if (!planningRequired) return
            setStatus(store.getMission(mission.id)!!, MissionStatus.ESCALATED, "planning unavailable")
            throw DomainException.InvalidAction("R&D planner is not wired")
        }
        val orchestrator = AgentDuties.orchestrator(store.listAgents(mission.projectId))
        if (orchestrator == null) {
            setStatus(store.getMission(mission.id)!!, MissionStatus.ESCALATED, "no orchestrator capability")
            throw DomainException.AgentNotFound("orchestrator")
        }
        val raw = try {
            planner.plan(mission)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Exception) {
            val current = store.getMission(mission.id)
            if (current != null && current.status != MissionStatus.FAILED && !MissionStateMachine.isTerminal(current.status)) {
                setStatus(current, MissionStatus.FAILED, "planner failed: ${t.message}")
            }
            throw t
        }
        ingestPlan!!.invoke(mission.id, raw)
        if (planningRequired && store.listTasks(mission.id).isEmpty()) {
            setStatus(store.getMission(mission.id)!!, MissionStatus.WAITING_FOR_USER, "planner produced no tasks")
        }
    }

    suspend fun hasRequiredArtifact(missionId: String): Boolean =
        artifacts.latest(missionId, com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN) != null

    suspend fun latestArtifact(missionId: String) =
        artifacts.latest(missionId, com.agentflow.domain.model.ArtifactType.IMPLEMENTATION_PLAN)

    private suspend fun persistArtifact(mission: Mission, action: MissionAction.CreateArtifact): MissionActionResult = artifactMutex.withLock artifactLock@ {
        if (action.name.contains("..") || action.name.startsWith("/")) {
            throw DomainException.PolicyViolation("illegal artifact path")
        }
        val published = artifacts.publish(mission.id, action.name, action.content, action.type)
        if (published.unchanged) {
            mutex.withLock missionLock@ {
                val current = store.getMission(mission.id) ?: return@missionLock
                store.appendEvent(event(mission.id, MissionEventType.ARTIFACT_CREATED, "unchanged hash ${published.artifact.contentHash}"))
                if (current.status == MissionStatus.REVISION_REQUIRED || current.status == MissionStatus.REVIEWING) {
                    setStatus(current, MissionStatus.ESCALATED, "loop-guard: unchanged artifact")
                }
            }
            return@artifactLock MissionActionResult(true, "unchanged-artifact", published.artifact.id)
        }
        mutex.withLock {
            if (store.getMission(mission.id) != null) {
                store.appendEvent(event(mission.id, MissionEventType.ARTIFACT_CREATED, "${action.name} v${published.artifact.version} ${published.artifact.contentHash}"))
            }
        }
        MissionActionResult(true, "artifact", published.artifact.id)
    }

    private fun isRuntimeExpired(mission: Mission): Boolean {
        val startedAt = mission.startedAt ?: return false
        if (policy.maxMissionRuntimeMs <= 0L) return false
        return now() - startedAt >= policy.maxMissionRuntimeMs
    }

    private suspend fun graph(missionId: String) = TaskGraph(store.listTasks(missionId), store.listDependencies(missionId))

    private suspend fun requireMission(id: String) =
        store.getMission(id) ?: throw DomainException.MissionNotFound(id)

    private suspend fun requireTaskInMission(mission: Mission, taskId: String): Task {
        val task = store.getTask(taskId) ?: throw DomainException.TaskNotFound(taskId)
        if (task.missionId != mission.id) {
            throw DomainException.InvalidAction("task not in mission")
        }
        return task
    }

    private suspend fun requireAgentInProject(agentId: String, projectId: String) =
        store.getAgent(agentId)?.also {
            if (it.projectId != projectId) throw DomainException.AgentNotInProject("$agentId not in $projectId")
        } ?: throw DomainException.AgentNotFound(agentId)

    private suspend fun setStatus(mission: Mission, to: MissionStatus, eventMessage: String? = null) {
        MissionStateMachine.requireTransition(mission.status, to)
        val stamp = now()
        val updatedMission = mission.copy(
            status = to,
            updatedAt = stamp,
            startedAt = mission.startedAt ?: stamp,
            completedAt = if (MissionStateMachine.isTerminal(to)) stamp else mission.completedAt,
        )
        val type = when (to) {
            MissionStatus.PAUSED -> MissionEventType.MISSION_PAUSED
            MissionStatus.CANCELLED -> MissionEventType.MISSION_CANCELLED
            MissionStatus.PLANNING -> MissionEventType.MISSION_PLANNING
            MissionStatus.FAILED -> MissionEventType.MISSION_FAILED
            MissionStatus.REVIEWING -> MissionEventType.MISSION_REVIEWING
            MissionStatus.ESCALATED -> MissionEventType.MISSION_ESCALATED
            else -> MissionEventType.MISSION_STARTED
        }
        val statusEvent = event(mission.id, type, eventMessage ?: "${mission.status} → $to")
        store.transaction {
            store.updateMission(updatedMission)
            store.appendEvent(statusEvent)
        }
    }

    private suspend fun transitionTask(task: Task, to: TaskStatus): Task =
        task.copy(status = TaskStateMachine.transition(task.status, to), updatedAt = now())

    private suspend fun updateTask(task: Task) = store.updateTask(task)
}
