package com.agentflow.domain.inspector

import com.agentflow.domain.model.ArtifactType
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.MissionArtifact
import com.agentflow.domain.reference.ContentHasher

interface ArtifactCatalog {
    suspend fun list(missionId: String): List<MissionArtifact>
    suspend fun insert(artifact: MissionArtifact)
    fun readContent(artifactId: String): String?
    fun writeContent(artifactId: String, content: String)
}

class InMemoryArtifactCatalog : DurableArtifactCatalog {
    val artifacts = mutableListOf<MissionArtifact>()
    val contents = mutableMapOf<String, String>()
    val pending = mutableMapOf<String, Pair<MissionArtifact, String>>()
    override suspend fun list(missionId: String) = artifacts.filter { it.missionId == missionId }
    override suspend fun insert(artifact: MissionArtifact) {
        artifacts += artifact
    }
    override fun readContent(artifactId: String) = contents[artifactId]
    override fun writeContent(artifactId: String, content: String) {
        contents[artifactId] = content
    }
    override suspend fun insertPending(artifact: MissionArtifact, content: String) {
        pending[artifact.id] = artifact to content
    }
    override suspend fun finalizePending(artifact: MissionArtifact) {
        artifacts.removeIf { it.id == artifact.id }
        artifacts += artifact
        pending.remove(artifact.id)
    }
    override suspend fun pending(): List<Pair<MissionArtifact, String>> = pending.values.toList()
}

class ArtifactPublicationException(cause: Throwable) : RuntimeException("Artifact publication failed", cause)

class ArtifactService(
    private val catalog: ArtifactCatalog,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun latest(missionId: String, type: ArtifactType = ArtifactType.IMPLEMENTATION_PLAN): MissionArtifact? =
        catalog.list(missionId).filter { it.type == type }.maxByOrNull { it.version }

    suspend fun versions(missionId: String, type: ArtifactType = ArtifactType.IMPLEMENTATION_PLAN): List<MissionArtifact> =
        catalog.list(missionId).filter { it.type == type }.sortedBy { it.version }

    suspend fun recoverPending(): Int {
        val durable = catalog as? DurableArtifactCatalog ?: return 0
        var recovered = 0
        durable.pending().forEach { (artifact, content) ->
            val current = durable.readContent(artifact.id)
            if (current != content) durable.writeContent(artifact.id, content)
            durable.finalizePending(artifact)
            recovered++
        }
        return recovered
    }

    fun catalogRead(artifactId: String): String? = catalog.readContent(artifactId)

    suspend fun publish(
        missionId: String,
        name: String,
        content: String,
        type: ArtifactType = ArtifactType.IMPLEMENTATION_PLAN,
    ): PublishResult {
        val hash = ContentHasher.sha256(content)
        val existing = latest(missionId, type)
        if (existing?.contentHash == hash) {
            return PublishResult(existing, unchanged = true)
        }
        val version = (existing?.version ?: 0) + 1
        val id = Ids.new()
        val artifact = MissionArtifact(
            id = id,
            missionId = missionId,
            type = type,
            name = name,
            path = "missions/$missionId/artifacts/$id",
            contentHash = hash,
            sizeBytes = content.toByteArray().size.toLong(),
            mimeType = "text/markdown",
            version = version,
            createdAt = now(),
            updatedAt = now(),
        )
        val durable = catalog as? DurableArtifactCatalog
        if (durable != null) {
            durable.insertPending(artifact, content)
            try {
                val existingContent = durable.readContent(artifact.id)
                if (existingContent != content) durable.writeContent(artifact.id, content)
                durable.finalizePending(artifact)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                throw ArtifactPublicationException(t)
            }
        } else {
            catalog.writeContent(id, content)
            catalog.insert(artifact)
        }
        return PublishResult(artifact, unchanged = false)
    }
}

data class PublishResult(val artifact: MissionArtifact, val unchanged: Boolean)
