package com.agentflow.data.repository

import androidx.room.withTransaction
import com.agentflow.data.dao.InspectorDao
import com.agentflow.data.dao.MissionDao
import com.agentflow.data.dao.MissionEventDao
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.inspector.AtomicReviewCatalog
import com.agentflow.domain.inspector.ReviewCatalog
import com.agentflow.domain.mission.MissionStore
import com.agentflow.domain.model.InspectorIssue
import com.agentflow.domain.model.InspectorReview
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionEvent

class RoomReviewCatalog(
    private val db: AgentFlowDatabase,
    private val dao: InspectorDao = db.inspectorDao(),
    private val missions: MissionDao = db.missionDao(),
    private val events: MissionEventDao = db.missionEventDao(),
) : AtomicReviewCatalog {
    override suspend fun insert(review: InspectorReview) = dao.insertReview(review.toEntity())
    override suspend fun insertIssue(issue: InspectorIssue) = dao.insertIssue(issue.toEntity())
    override suspend fun list(missionId: String): List<InspectorReview> = dao.reviewsForMission(missionId).map { it.toDomain() }
    override suspend fun issues(reviewId: String): List<InspectorIssue> = dao.issuesForReview(reviewId).map { it.toDomain() }

    override suspend fun persistDecision(
        review: InspectorReview,
        issues: List<InspectorIssue>,
        mission: Mission,
        event: MissionEvent,
        store: MissionStore,
    ) {
        db.withTransaction {
            dao.insertReview(review.toEntity())
            issues.forEach { dao.insertIssue(it.toEntity()) }
            missions.update(mission.toEntity())
            events.insert(event.toEntity())
        }
    }
}
