package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.InspectorIssueEntity
import com.agentflow.data.entity.InspectorReviewEntity

@Dao
interface InspectorDao {
    @Query("SELECT * FROM inspector_reviews WHERE missionId = :missionId ORDER BY createdAt DESC")
    suspend fun reviewsForMission(missionId: String): List<InspectorReviewEntity>
    @Query("SELECT * FROM inspector_issues WHERE reviewId = :reviewId")
    suspend fun issuesForReview(reviewId: String): List<InspectorIssueEntity>
    @Query("SELECT i.* FROM inspector_issues i INNER JOIN inspector_reviews r ON i.reviewId = r.id WHERE r.missionId = :missionId AND i.resolved = 0")
    suspend fun openIssues(missionId: String): List<InspectorIssueEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReview(entity: InspectorReviewEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIssue(entity: InspectorIssueEntity)
    @Update
    suspend fun updateReview(entity: InspectorReviewEntity)
    @Update
    suspend fun updateIssue(entity: InspectorIssueEntity)
}
