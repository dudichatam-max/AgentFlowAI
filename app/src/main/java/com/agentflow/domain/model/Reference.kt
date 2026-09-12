package com.agentflow.domain.model
data class Reference(
    val id: String, val projectId: String, val agentId: String? = null,
    val name: String, val type: ReferenceType, val path: String? = null,
    val uri: String? = null, val sizeBytes: Long = 0, val mimeType: String? = null,
    val contentHash: String? = null, val inclusionMode: InclusionMode = InclusionMode.RELEVANT,
    val createdAt: Long, val updatedAt: Long,
)
