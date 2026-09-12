package com.agentflow.data.repository
import com.agentflow.data.dao.AgentMessageDao
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.AgentMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepository(private val dao: AgentMessageDao) {
    suspend fun insert(message: AgentMessage) = dao.insert(message.toEntity())
    suspend fun pageByMission(missionId: String, limit: Int, offset: Int): List<AgentMessage> =
        dao.pageByMission(missionId, limit, offset).map { it.toDomain() }
    fun observeSession(sessionId: String): Flow<List<AgentMessage>> =
        dao.observeSession(sessionId).map { list -> list.map { it.toDomain() } }
    suspend fun get(id: String): AgentMessage? = dao.getById(id)?.toDomain()
}
