package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agents",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("status"), Index("name")],
)
data class AgentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val role: String,
    val description: String,
    val providerId: String,
    val modelId: String,
    val fallbackProviderId: String?,
    val fallbackModelId: String?,
    val temperature: Double,
    val maxOutputTokens: Int,
    val reasoningLevel: String,
    val freeOnly: Boolean,
    val status: String,
    val outputStyle: String,
    val verbosity: String,
    val exposeUncertainty: Boolean,
    val includeAssumptions: Boolean,
    val includeAlternatives: Boolean,
    val capabilities: String,
    val createdAt: Long,
    val updatedAt: Long,
)
