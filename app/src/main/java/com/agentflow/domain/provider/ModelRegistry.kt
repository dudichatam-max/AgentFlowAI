package com.agentflow.domain.provider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runtime source of truth for models discovered from configured AI providers.
 * Persistence is intentionally outside this component and is added in P0.2.
 */
interface ModelRegistry {
    val models: StateFlow<Map<ProviderType, List<ProviderModel>>>

    fun modelsFor(provider: ProviderType): List<ProviderModel>

    fun allModels(): List<ProviderModel>

    suspend fun refresh(provider: ProviderType): ProviderResult<List<ProviderModel>>

    suspend fun refreshAll(): Map<ProviderType, ProviderResult<List<ProviderModel>>>
}

class InMemoryModelRegistry(
    private val providerManager: ProviderManager,
) : ModelRegistry {
    private val state = MutableStateFlow<Map<ProviderType, List<ProviderModel>>>(emptyMap())

    override val models: StateFlow<Map<ProviderType, List<ProviderModel>>> = state.asStateFlow()

    override fun modelsFor(provider: ProviderType): List<ProviderModel> =
        state.value[provider].orEmpty()

    override fun allModels(): List<ProviderModel> =
        state.value.values.flatten()

    override suspend fun refresh(provider: ProviderType): ProviderResult<List<ProviderModel>> =
        when (val result = providerManager.listModels(provider)) {
            is ProviderResult.Success -> {
                state.value = state.value + (provider to result.value.distinctBy { it.id.lowercase() })
                result
            }
            is ProviderResult.Failure -> result
        }

    override suspend fun refreshAll(): Map<ProviderType, ProviderResult<List<ProviderModel>>> {
        val results = linkedMapOf<ProviderType, ProviderResult<List<ProviderModel>>>()
        providerManager.registered().forEach { provider ->
            results[provider] = refresh(provider)
        }
        return results
    }
}
