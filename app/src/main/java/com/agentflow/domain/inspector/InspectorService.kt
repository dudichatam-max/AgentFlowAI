package com.agentflow.domain.inspector

import com.agentflow.domain.mission.MissionAction
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.mission.event
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.InspectorIssue
import com.agentflow.domain.model.InspectorReview
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.Priority
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.policy.LoopGuard
import com.agentflow.domain.policy.LoopGuardLimits
import com.agentflow.domain.policy.PolicyDecision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InspectorService(
    private val store: MissionStore,
    private val reviews: ReviewCatalog,
    private val engine: MissionEngine,
    private val artifacts: ArtifactService,
    private val policy: InspectorApprovalPolicy = InspectorApprovalPolicy(),
    private val loopGuard: LoopGuard = LoopGuard(LoopGuardLimits(maxIdenticalActions = InspectorReviewLimits.MAX_INSPECTOR_REJECTIONS + 1)),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    suspend fun applyProposal(missionId: String, rawJson: String): Result<InspectorReview> = mutex.withLock {
        com.agentflow.domain.mission.suspendRunCatching {
        val mission = store.getMission(missionId) ?: error("mission missing")
        if (mission.status != MissionStatus.REVIEWING && mission.status != MissionStatus.REVISION_REQUIRED) {
            error("mission not reviewable")
        }
        val validated = InspectorReviewValidator.validate(InspectorReviewParser.parse(rawJson))
        if (validated.decision == ReviewStatus.APPROVED) {
            if (!policy.canApprove(validated.decision, validated.issues)) {
                error("approval policy rejected")
            }
            val artifact = artifacts.latest(missionId)
                ?: error("cannot approve without an implementation plan artifact")
            val latestRejection = reviews.list(missionId)
                .filter { it.status == ReviewStatus.REJECTED }
                .maxByOrNull { it.createdAt }
            if (latestRejection != null && artifact.updatedAt <= latestRejection.createdAt) {
                error("approval requires a new implementation plan after the latest rejection")
            }
        }
        val inspector = com.agentflow.domain.agent.AgentDuties.inspector(store.listAgents(mission.projectId))
            ?: error("no inspector agent")
        if (inspector.projectId != mission.projectId) error("cross-project inspector")

        val rejectedSoFar = reviews.list(missionId).count { it.status == ReviewStatus.REJECTED }
        if (validated.decision == ReviewStatus.REJECTED && rejectedSoFar >= InspectorReviewLimits.MAX_INSPECTOR_REJECTIONS) {
            val current = store.getMission(missionId)!!
            val review = buildReview(missionId, inspector.id, ReviewStatus.ESCALATED, validated)
            val issues = validated.issues.map { issue ->
                InspectorIssue(Ids.new(), review.id, issue.severity, "[${issue.category}] ${issue.description}", issue.requiredAction, createdAt = now())
            }
            val reviewEvent = event(missionId, MissionEventType.INSPECTOR_REJECTED, "rejection cap → ESCALATED")
            val atomic = reviews as? AtomicReviewCatalog
            if (atomic != null) {
                atomic.persistDecision(review, issues, current.copy(status = MissionStatus.ESCALATED, updatedAt = now()), reviewEvent, store)
            } else {
                persistReview(review, issues)
                store.persistMissionStatus(current.copy(status = MissionStatus.ESCALATED, updatedAt = now()), reviewEvent)
            }
            error("escalated after ${InspectorReviewLimits.MAX_INSPECTOR_REJECTIONS} rejections")
        }

        val loop = loopGuard.inspect(missionId, listOf(MissionAction.RequestReview))
        if (validated.decision == ReviewStatus.REJECTED && loop is PolicyDecision.Deny) {
            error(loop.reason)
        }

        val current = store.getMission(missionId)!!
        val nextStatus = when (validated.decision) {
            ReviewStatus.APPROVED -> MissionStatus.APPROVED
            ReviewStatus.REJECTED -> if (current.status == MissionStatus.REVIEWING) MissionStatus.REVISION_REQUIRED else current.status
            ReviewStatus.ESCALATED -> MissionStatus.ESCALATED
            ReviewStatus.PENDING -> current.status
        }
        val review = buildReview(missionId, inspector.id, validated.decision, validated)
        val issues = validated.issues.map { issue ->
            InspectorIssue(
                id = Ids.new(), reviewId = review.id, severity = issue.severity,
                description = "[${issue.category}] ${issue.description}", requiredAction = issue.requiredAction,
                createdAt = now(),
            )
        }
        val reviewEvent = when (validated.decision) {
            ReviewStatus.APPROVED -> event(missionId, MissionEventType.INSPECTOR_APPROVED, validated.summary)
            ReviewStatus.REJECTED -> event(missionId, MissionEventType.INSPECTOR_REJECTED, validated.summary)
            ReviewStatus.ESCALATED -> event(missionId, MissionEventType.INSPECTOR_STARTED, "escalated: ${validated.reason}")
            ReviewStatus.PENDING -> event(missionId, MissionEventType.INSPECTOR_STARTED, "pending review")
        }
        val updatedMission = current.copy(
            status = nextStatus,
            completedAt = if (nextStatus == MissionStatus.APPROVED) now() else current.completedAt,
            updatedAt = now(),
        )
        val atomic = reviews as? AtomicReviewCatalog
        if (atomic != null) {
            atomic.persistDecision(review, issues, updatedMission, reviewEvent, store)
        } else {
            persistReview(review, issues)
            if (nextStatus != current.status) store.persistMissionStatus(updatedMission, reviewEvent)
            else store.appendEvent(reviewEvent)
        }
        if (validated.decision == ReviewStatus.REJECTED) {
            loopGuard.record(missionId, MissionAction.RequestReview, store)
            val rd = com.agentflow.domain.agent.AgentDuties.orchestrator(store.listAgents(current.projectId)) ?: inspector
            engine.processAction(
                missionId,
                MissionAction.CreateTask(
                    title = "Review Inspector Findings",
                    description = validated.issues.joinToString("\n") { "${it.severity}: ${it.description} → ${it.requiredAction}" },
                    assignedAgentId = rd.id,
                    priority = Priority.HIGH,
                ),
            )
        }
        review
        }
    }

    suspend fun approve(missionId: String, summary: String = "Approved", reason: String = "Requirements met"): Result<InspectorReview> =
        applyProposal(
            missionId,
            """{"version":1,"decision":"APPROVED","summary":${jsonString(summary)},"reason":${jsonString(reason)},"confidence":0.95,"severity":"NONE","issues":[]}""",
        )

    suspend fun reject(
        missionId: String,
        summary: String,
        reason: String,
        issueDescription: String,
        requiredAction: String,
        severity: String = "HIGH",
        category: String = "ARCHITECTURE",
    ): Result<InspectorReview> = applyProposal(
        missionId,
        """{"version":1,"decision":"REJECTED","summary":${jsonString(summary)},"reason":${jsonString(reason)},"confidence":0.9,"severity":${jsonString(severity)},"issues":[{"severity":${jsonString(severity)},"description":${jsonString(issueDescription)},"requiredAction":${jsonString(requiredAction)},"category":${jsonString(category)}}]}""",
    )

    suspend fun escalate(missionId: String, reason: String): Result<InspectorReview> = applyProposal(
        missionId,
        """{"version":1,"decision":"ESCALATED","summary":"Escalated","reason":${jsonString(reason)},"confidence":0.5,"severity":"HIGH","issues":[]}""",
    )

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    suspend fun userApproveAnyway(missionId: String, reason: String): Result<Unit> = mutex.withLock {
        com.agentflow.domain.mission.suspendRunCatching {
        require(reason.isNotBlank()) { "override reason is required" }
        val mission = store.getMission(missionId) ?: error("missing")
        if (mission.status !in setOf(MissionStatus.REVIEWING, MissionStatus.REVISION_REQUIRED, MissionStatus.ESCALATED)) {
            error("user override is only available for review or escalation states")
        }
        store.persistMissionStatus(
            mission.copy(status = MissionStatus.APPROVED, completedAt = now(), updatedAt = now()),
            event(missionId, MissionEventType.INSPECTOR_APPROVED, "USER_OVERRIDE_APPROVAL: $reason"),
        )
        }
    }

    private fun buildReview(
        missionId: String,
        inspectorId: String,
        status: ReviewStatus,
        validated: ValidatedInspectorReview,
    ): InspectorReview = InspectorReview(
        id = Ids.new(), missionId = missionId, taskId = null, inspectorAgentId = inspectorId,
        status = status, summary = validated.summary, reason = validated.reason,
        severity = validated.severity, createdAt = now(),
    )

    private suspend fun persistReview(review: InspectorReview, issues: List<InspectorIssue>) {
        reviews.insert(review)
        issues.forEach { reviews.insertIssue(it) }
    }

}

interface AtomicReviewCatalog : ReviewCatalog {
    suspend fun persistDecision(
        review: InspectorReview,
        issues: List<InspectorIssue>,
        mission: com.agentflow.domain.model.Mission,
        event: com.agentflow.domain.model.MissionEvent,
        store: MissionStore,
    )
}

interface ReviewCatalog {
    suspend fun insert(review: InspectorReview)
    suspend fun insertIssue(issue: InspectorIssue)
    suspend fun list(missionId: String): List<InspectorReview>
    suspend fun issues(reviewId: String): List<InspectorIssue>
}

class InMemoryReviewCatalog : AtomicReviewCatalog {
    val reviews = mutableListOf<InspectorReview>()
    val issues = mutableListOf<InspectorIssue>()
    override suspend fun insert(review: InspectorReview) {
        reviews += review
    }
    override suspend fun insertIssue(issue: InspectorIssue) {
        issues += issue
    }
    override suspend fun list(missionId: String) = reviews.filter { it.missionId == missionId }
    override suspend fun issues(reviewId: String) = issues.filter { it.reviewId == reviewId }
    override suspend fun persistDecision(
        review: InspectorReview,
        issues: List<InspectorIssue>,
        mission: com.agentflow.domain.model.Mission,
        event: com.agentflow.domain.model.MissionEvent,
        store: MissionStore,
    ) {
        val oldReviews = reviews.toList()
        val oldIssues = this@InMemoryReviewCatalog.issues.toList()
        try {
            store.transaction {
                reviews += review
                this@InMemoryReviewCatalog.issues += issues
                store.persistMissionStatus(mission, event)
            }
        } catch (t: Throwable) {
            reviews.clear(); reviews.addAll(oldReviews)
            this@InMemoryReviewCatalog.issues.clear(); this@InMemoryReviewCatalog.issues.addAll(oldIssues)
            throw t
        }
    }
}
