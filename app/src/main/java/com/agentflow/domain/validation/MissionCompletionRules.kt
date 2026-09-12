package com.agentflow.domain.validation
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.model.Severity
import com.agentflow.domain.model.TaskStatus

data class CompletionSnapshot(
    val missionStatus: MissionStatus,
    val requiredTasksDone: Boolean,
    val finalArtifactExists: Boolean,
    val inspectorApproved: Boolean,
    val openCriticalIssues: Boolean,
    val openUserInput: Boolean,
    val blockedTasks: Boolean,
    val activeRevision: Boolean,
    val lastReviewStatus: ReviewStatus? = null,
)

object MissionCompletionRules {
    fun canApprove(snapshot: CompletionSnapshot): Boolean {
        if (MissionStateMachine.isTerminal(snapshot.missionStatus) &&
            snapshot.missionStatus != MissionStatus.APPROVED
        ) return false
        val gates = snapshot.requiredTasksDone &&
            snapshot.finalArtifactExists &&
            snapshot.inspectorApproved &&
            !snapshot.openCriticalIssues &&
            !snapshot.openUserInput &&
            !snapshot.blockedTasks &&
            !snapshot.activeRevision &&
            snapshot.lastReviewStatus == ReviewStatus.APPROVED
        return gates && snapshot.missionStatus == MissionStatus.REVIEWING
    }

    fun reasonsBlocked(snapshot: CompletionSnapshot): List<String> {
        val reasons = mutableListOf<String>()
        if (!snapshot.requiredTasksDone) reasons += "required tasks incomplete"
        if (!snapshot.finalArtifactExists) reasons += "missing final artifact"
        if (!snapshot.inspectorApproved) reasons += "inspector not approved"
        if (snapshot.openCriticalIssues) reasons += "open critical issues"
        if (snapshot.openUserInput) reasons += "waiting for user"
        if (snapshot.blockedTasks) reasons += "blocked tasks"
        if (snapshot.activeRevision) reasons += "revision in progress"
        return reasons
    }

    fun isOpenCritical(severity: Severity, resolved: Boolean): Boolean =
        !resolved && (severity == Severity.CRITICAL || severity == Severity.HIGH)

    fun isBlockingTask(status: TaskStatus): Boolean =
        status == TaskStatus.BLOCKED || status == TaskStatus.WAITING_FOR_USER
}
