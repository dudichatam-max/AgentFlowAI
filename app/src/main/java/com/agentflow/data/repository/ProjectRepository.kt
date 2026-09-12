package com.agentflow.data.repository
import com.agentflow.data.dao.ProjectDao
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(private val dao: ProjectDao) {
    fun observeActive(): Flow<List<Project>> = dao.observeActive().map { list -> list.map { it.toDomain() } }
    suspend fun get(id: String): Project? = dao.getById(id)?.toDomain()
    suspend fun create(name: String, description: String, now: Long = System.currentTimeMillis()): Project {
        val project = Project(id = Ids.new(), name = name.trim(), description = description, createdAt = now, updatedAt = now)
        dao.insert(project.toEntity())
        return project
    }
    suspend fun update(project: Project, now: Long = System.currentTimeMillis()) {
        dao.update(project.copy(updatedAt = now).toEntity())
    }
    suspend fun archive(id: String, now: Long = System.currentTimeMillis()) {
        val current = dao.getById(id) ?: return
        dao.update(current.copy(isArchived = true, updatedAt = now))
    }
}
