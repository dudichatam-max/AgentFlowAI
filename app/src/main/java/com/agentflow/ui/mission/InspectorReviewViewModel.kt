package com.agentflow.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentflow.data.repository.MissionRepository
import com.agentflow.domain.inspector.ArtifactService
import com.agentflow.domain.inspector.InspectorService
import com.agentflow.domain.inspector.ReviewCatalog
import com.agentflow.domain.model.InspectorIssue
import com.agentflow.domain.model.InspectorReview
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionArtifact
import com.agentflow.domain.model.MissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InspectorReviewUiState(
    val mission: Mission? = null,
    val artifact: MissionArtifact? = null,
    val previousArtifact: MissionArtifact? = null,
    val artifactContent: String? = null,
    val review: InspectorReview? = null,
    val issues: List<InspectorIssue> = emptyList(),
    val submitting: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class InspectorReviewViewModel(
    private val missionId: String,
    private val missions: MissionRepository,
    private val artifacts: ArtifactService,
    private val inspector: InspectorService,
    private val reviews: ReviewCatalog,
) : ViewModel() {
    private val _state = MutableStateFlow(InspectorReviewUiState())
    val state: StateFlow<InspectorReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    fun approve() = decide {
        inspector.approve(missionId).exceptionOrNull()?.message
    }

    fun reject() = decide {
        inspector.reject(
            missionId,
            summary = "Rejected",
            reason = "Open issues require revision",
            issueDescription = "Implementation plan needs revision",
            requiredAction = "R&D must create revision tasks from Inspector findings",
        ).exceptionOrNull()?.message
    }

    fun escalate() = decide {
        inspector.escalate(missionId, "User escalated review").exceptionOrNull()?.message
    }

    fun approveAnyway(reason: String) = decide {
        inspector.userApproveAnyway(missionId, reason.ifBlank { "User override approval" }).exceptionOrNull()?.message
    }

    private fun decide(block: suspend () -> String?) {
        val current = _state.value
        if (current.submitting) return
        if (current.mission?.status == MissionStatus.APPROVED) {
            _state.value = current.copy(error = "Already approved")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(submitting = true, error = null)
            val err = block()
            refresh()
            _state.value = _state.value.copy(submitting = false, error = err, info = if (err == null) "Saved" else null)
        }
    }

    private suspend fun refresh() {
        val mission = missions.get(missionId)
        val versions = artifacts.versions(missionId)
        val latest = versions.lastOrNull()
        _state.value = _state.value.copy(
            mission = mission,
            artifact = latest,
            previousArtifact = versions.dropLast(1).lastOrNull(),
            artifactContent = latest?.let { artifacts.catalogRead(it.id) },
            review = reviews.list(missionId).firstOrNull(),
            issues = reviews.list(missionId).firstOrNull()?.let { reviews.issues(it.id) }.orEmpty(),
        )
    }

    class Factory(
        private val missionId: String,
        private val missions: MissionRepository,
        private val artifacts: ArtifactService,
        private val inspector: InspectorService,
        private val reviews: ReviewCatalog,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InspectorReviewViewModel(missionId, missions, artifacts, inspector, reviews) as T
    }
}
