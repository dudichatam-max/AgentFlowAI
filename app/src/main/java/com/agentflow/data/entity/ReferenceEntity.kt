package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "references",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("agentId"), Index("type"), Index("inclusionMode")],
)
data class ReferenceEntity(
    @PrimaryKey val id: String,
    val projectId: String, val agentId: String?, val name: String, val type: String,
    val path: String?, val uri: String?, val sizeBytes: Long, val mimeType: String?,
    val contentHash: String?, val inclusionMode: String, val createdAt: Long, val updatedAt: Long,
)
