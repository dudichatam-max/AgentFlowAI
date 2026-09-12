package com.agentflow.data.repository

import com.agentflow.data.dao.ReferenceDao
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Reference
import com.agentflow.domain.reference.ReferenceCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReferenceRepository(private val dao: ReferenceDao) : ReferenceCatalog {
    fun observeByProject(projectId: String): Flow<List<Reference>> =
        dao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeByAgent(agentId: String): Flow<List<Reference>> =
        dao.observeByAgent(agentId).map { list -> list.map { it.toDomain() } }

    override suspend fun insert(reference: Reference) = dao.insert(reference.toEntity())

    override suspend fun update(reference: Reference) = dao.update(reference.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun get(id: String): Reference? = dao.getById(id)?.toDomain()

    override suspend fun listByProject(projectId: String): List<Reference> =
        dao.listByProject(projectId).map { it.toDomain() }

    override suspend fun findByHash(hash: String): List<Reference> =
        dao.findByHash(hash).map { it.toDomain() }

    suspend fun updateInclusionMode(id: String, mode: InclusionMode, now: Long = System.currentTimeMillis()) {
        val current = get(id) ?: return
        update(current.copy(inclusionMode = mode, updatedAt = now))
    }
}
