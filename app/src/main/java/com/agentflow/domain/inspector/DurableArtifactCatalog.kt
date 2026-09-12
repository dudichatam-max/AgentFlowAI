package com.agentflow.domain.inspector

import com.agentflow.domain.model.MissionArtifact

/** Durable publication journal for artifacts spanning DB + filesystem. */
interface DurableArtifactCatalog : ArtifactCatalog {
    suspend fun insertPending(artifact: MissionArtifact, content: String)
    suspend fun finalizePending(artifact: MissionArtifact)
    suspend fun pending(): List<Pair<MissionArtifact, String>>
}
