package com.agentflow.domain.provider

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelRegistryTest {

    @Test
    fun refreshStoresDiscoveredModelsByProvider() = runTest {
        val provider = ToggleModelProvider()
        val registry = InMemoryModelRegistry(ProviderManager(mapOf(ProviderType.GEMINI to provider)))

        val result = registry.refresh(ProviderType.GEMINI)

        assertThat(result).containsExactly(
            ProviderModel("gemini-live", "gemini-live", ProviderType.GEMINI, true),
        )
        assertThat(registry.modelsFor(ProviderType.GEMINI)).containsExactly(
            ProviderModel("gemini-live", "gemini-live", ProviderType.GEMINI, true),
        )
    }

    @Test
    fun refreshFailureDoesNotReplaceExistingModels() = runTest {
        val provider = ToggleModelProvider()
        val registry = InMemoryModelRegistry(ProviderManager(mapOf(ProviderType.GEMINI to provider)))

        registry.refresh(ProviderType.GEMINI)
        provider.fail = true

        val result = registry.refresh(ProviderType.GEMINI)

        assertThat(result).isInstanceOf(ProviderResult.Failure::class.java)
        assertThat(registry.modelsFor(ProviderType.GEMINI)).containsExactly(
            ProviderModel("gemini-live", "gemini-live", ProviderType.GEMINI, true),
        )
    }

    @Test
    fun refreshAllDiscoversEveryRegisteredProvider() = runTest {
        val gemini = ToggleModelProvider()
        val groq = ToggleModelProvider().apply { typeOverride = ProviderType.GROQ; modelId = "groq-model" }
        val registry = InMemoryModelRegistry(
            ProviderManager(
                mapOf(
                    ProviderType.GEMINI to gemini,
                    ProviderType.GROQ to groq,
                ),
            ),
        )

        val result = registry.refreshAll()

        assertThat(result).containsExactly(ProviderType.GEMINI, ProviderType.GROQ)
        assertThat(registry.modelsFor(ProviderType.GEMINI)).hasSize(1)
        assertThat(registry.modelsFor(ProviderType.GROQ)).hasSize(1)
    }

    private class ToggleModelProvider : AIProvider {
        var fail = false
        var typeOverride = ProviderType.GEMINI
        var modelId = "gemini-live"

        override fun providerType() = typeOverride

        override suspend fun generate(request: com.agentflow.domain.ai.AIRequest) =
            ProviderResult.Failure<com.agentflow.domain.ai.AIResponse>(
                ProviderError.of(typeOverride, ProviderErrorType.UNKNOWN, "not used"),
            )

        override suspend fun listModels(): ProviderResult<List<ProviderModel>> =
            if (fail) {
                ProviderResult.Failure(
                    ProviderError.of(typeOverride, ProviderErrorType.UNKNOWN, "discovery failed"),
                )
            } else {
                ProviderResult.Success(
                    listOf(ProviderModel(modelId, modelId, typeOverride, true)),
                )
            }

        override suspend fun testConnection(model: String?) =
            ProviderResult.Failure<ProviderHealth>(
                ProviderError.of(typeOverride, ProviderErrorType.UNKNOWN, "not used"),
            )
    }
}
