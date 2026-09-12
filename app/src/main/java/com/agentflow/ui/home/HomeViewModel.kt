package com.agentflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentflow.data.repository.MissionRepository
import com.agentflow.data.repository.ProjectRepository
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Project
import com.agentflow.domain.storage.StorageBreakdown
import com.agentflow.domain.storage.StorageManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val activeMissions: List<Mission> = emptyList(),
    val waiting: List<Mission> = emptyList(),
    val storage: StorageBreakdown? = null,
)

class HomeViewModel(
    projects: ProjectRepository,
    missions: MissionRepository,
    storage: StorageManager,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        projects.observeActive(),
        missions.observeActive(),
    ) { projectList, missionList ->
        HomeUiState(
            projects = projectList,
            activeMissions = missionList.filter {
                it.status in setOf(MissionStatus.EXECUTING, MissionStatus.RESEARCHING, MissionStatus.PLANNING, MissionStatus.REVIEWING)
            },
            waiting = missionList.filter {
                it.status in setOf(MissionStatus.WAITING_FOR_USER, MissionStatus.ESCALATED, MissionStatus.REVISION_REQUIRED)
            },
            storage = storage.breakdown(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
