package com.agentflow.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentflow.data.repository.MissionRepository
import com.agentflow.data.repository.TaskGraphRepository
import com.agentflow.domain.inspector.ArtifactService
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionArtifact
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskDependency
import com.agentflow.domain.model.TaskStatus
import com.agentflow.work.MissionWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MissionUiState(
    val mission: Mission? = null,
    val tasks: List<Task> = emptyList(),
    val dependencies: List<TaskDependency> = emptyList(),
    val readyCount: Int = 0,
    val runningCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val waitingCount: Int = 0,
    val artifact: MissionArtifact? = null,
    val recentEvents: List<MissionEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class MissionViewModel(
    private val missionId: String,
    private val missions: MissionRepository,
    private val graph: TaskGraphRepository,
    private val engine: MissionEngine,
    private val artifacts: ArtifactService,
    private val scheduler: MissionWorkScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(MissionUiState(isLoading = true))
    val state: StateFlow<MissionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            graph.observeTasks(missionId).collect { tasks ->
                refresh(tasks)
            }
        }
        viewModelScope.launch { refresh() }
    }

    fun start() = viewModelScope.launch {
        val result = engine.startMission(missionId)
        if (MissionUiPolicy.shouldEnqueueAfterStart(result.isSuccess)) {
            scheduler.enqueue(missionId)
        }
        refresh()
        if (result.isFailure) {
            _state.value = _state.value.copy(error = result.exceptionOrNull()?.message ?: "Unable to start mission")
        }
    }
    fun pause() = viewModelScope.launch {
        val result = engine.pauseMission(missionId)
        refresh()
        if (result.isFailure) _state.value = _state.value.copy(error = result.exceptionOrNull()?.message ?: "Unable to pause mission")
    }
    fun resume() = viewModelScope.launch {
        val result = engine.resumeMission(missionId)
        if (result.isSuccess) scheduler.enqueue(missionId)
        refresh()
        if (result.isFailure) _state.value = _state.value.copy(error = result.exceptionOrNull()?.message ?: "Unable to resume mission")
    }
    fun cancel() = viewModelScope.launch {
        val result = engine.cancelMission(missionId)
        refresh()
        if (result.isFailure) _state.value = _state.value.copy(error = result.exceptionOrNull()?.message ?: "Unable to cancel mission")
    }

    private suspend fun refresh(observed: List<Task>? = null) {
        val mission = missions.get(missionId)
        val tasks = observed ?: graph.listTasks(missionId)
        val deps = graph.listDependencies(missionId)
        val events = missions.pageEvents(missionId, 40, 0)
        _state.value = MissionUiState(
            mission = mission,
            tasks = tasks,
            dependencies = deps,
            readyCount = tasks.count { it.status == TaskStatus.READY },
            runningCount = tasks.count { it.status == TaskStatus.RUNNING },
            completedCount = tasks.count { it.status == TaskStatus.COMPLETED },
            failedCount = tasks.count { it.status == TaskStatus.FAILED },
            waitingCount = tasks.count { it.status == TaskStatus.WAITING_FOR_USER || it.status == TaskStatus.WAITING_FOR_DEPENDENCY },
            artifact = artifacts.latest(missionId),
            recentEvents = events,
            isLoading = false,
        )
    }

    class Factory(
        private val missionId: String,
        private val missions: MissionRepository,
        private val graph: TaskGraphRepository,
        private val engine: MissionEngine,
        private val artifacts: ArtifactService,
        private val scheduler: MissionWorkScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MissionViewModel(missionId, missions, graph, engine, artifacts, scheduler) as T
    }
}
