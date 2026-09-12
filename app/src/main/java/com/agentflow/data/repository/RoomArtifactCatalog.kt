package com.agentflow.data.repository

import androidx.room.withTransaction
import com.agentflow.data.dao.ArtifactDao
import com.agentflow.data.dao.MissionArtifactPendingDao
import com.agentflow.data.entity.MissionArtifactPendingEntity
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.data.storage.AtomicArtifactFileStore
import com.agentflow.domain.inspector.DurableArtifactCatalog
import com.agentflow.domain.model.MissionArtifact
import java.io.File

class RoomArtifactCatalog(
    private val db: com.agentflow.data.database.AgentFlowDatabase,
    private val dao: ArtifactDao = db.artifactDao(),
    private val pendingDao: MissionArtifactPendingDao = db.missionArtifactPendingDao(),
    root: File,
) : DurableArtifactCatalog {
    private val files = AtomicArtifactFileStore(root)

    override suspend fun list(missionId: String): List<MissionArtifact> = dao.listByMission(missionId).map { it.toDomain() }
    override suspend fun insert(artifact: MissionArtifact) = dao.insert(artifact.toEntity())
    override fun readContent(artifactId: String): String? = files.read(artifactId)
    override fun writeContent(artifactId: String, content: String) = files.writeAtomic(artifactId, content)

    override suspend fun insertPending(artifact: MissionArtifact, content: String) {
        pendingDao.insert(
            MissionArtifactPendingEntity(
                artifact.id, artifact.missionId, artifact.type.name, artifact.name, artifact.path,
                artifact.contentHash, artifact.sizeBytes, artifact.mimeType, artifact.version,
                artifact.createdAt, artifact.updatedAt, content,
            ),
        )
    }

    override suspend fun finalizePending(artifact: MissionArtifact) {
        db.withTransaction {
            if (dao.listByMission(artifact.missionId).none { it.id == artifact.id }) dao.insert(artifact.toEntity())
            pendingDao.delete(artifact.id)
        }
    }

    override suspend fun pending(): List<Pair<MissionArtifact, String>> = pendingDao.listAll().map {
        it.toDomainArtifact() to it.content
    }

    suspend fun cleanupOrphans() {
        val referencedIds = dao.listAll().mapTo(mutableSetOf()) { it.id }
        referencedIds += pendingDao.listAll().map { it.id }
        files.reconcile(referencedIds)
    }
}

private fun MissionArtifactPendingEntity.toDomainArtifact() = MissionArtifact(
    id, missionId, com.agentflow.domain.model.ArtifactType.valueOf(type), name, path, contentHash,
    sizeBytes, mimeType, version, createdAt, updatedAt,
)
