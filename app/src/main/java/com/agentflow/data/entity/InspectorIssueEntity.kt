package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspector_issues",
    foreignKeys = [ForeignKey(entity = InspectorReviewEntity::class, parentColumns = ["id"], childColumns = ["reviewId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reviewId"), Index("resolved"), Index("severity")],
)
data class InspectorIssueEntity(
    @PrimaryKey val id: String,
    val reviewId: String, val severity: String, val description: String, val requiredAction: String,
    val resolved: Boolean, val createdAt: Long, val resolvedAt: Long?,
)
