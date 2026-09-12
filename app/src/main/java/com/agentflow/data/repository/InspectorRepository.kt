package com.agentflow.data.repository
import com.agentflow.data.dao.InspectorDao
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.InspectorIssue
import com.agentflow.domain.model.InspectorReview

class InspectorRepository(private val dao: InspectorDao) {
    suspend fun insertReview(review: InspectorReview) = dao.insertReview(review.toEntity())
    suspend fun insertIssue(issue: InspectorIssue) = dao.insertIssue(issue.toEntity())
    suspend fun updateReview(review: InspectorReview) = dao.updateReview(review.toEntity())
    suspend fun reviews(missionId: String): List<InspectorReview> = dao.reviewsForMission(missionId).map { it.toDomain() }
    suspend fun openIssues(missionId: String): List<InspectorIssue> = dao.openIssues(missionId).map { it.toDomain() }
}
