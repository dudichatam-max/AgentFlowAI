package com.agentflow.domain.model
data class MissionArtifact(
    val id: String, val missionId: String, val type: ArtifactType, val name: String,
    val path: String? = null, val contentHash: String? = null, val sizeBytes: Long = 0,
    val mimeType: String? = null, val version: Int = 1, val createdAt: Long, val updatedAt: Long,
)
