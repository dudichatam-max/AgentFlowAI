package com.agentflow.data.repository
import com.agentflow.data.dao.ProviderConfigDao
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProviderConfigRepository(private val dao: ProviderConfigDao) {
    fun observeAll(): Flow<List<ProviderConfig>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getByProvider(providerId: String): ProviderConfig? = dao.getByProvider(providerId)?.toDomain()
    suspend fun upsert(config: ProviderConfig) = dao.upsert(config.toEntity())
}
