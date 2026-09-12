package com.agentflow.domain.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProviderPolicyTest {

    private fun model(id: String, free: Boolean?) = ProviderModel(
        id = id,
        displayName = id,
        provider = ProviderType.GROQ,
        isFree = free,
    )

    @Test
    fun freeAcceptedWhenFreeOnly() {
        assertThat(ProviderPolicy.validateModel(model("openai/gpt-oss-20b", true), freeOnly = true)).isNull()
    }

    @Test
    fun paidRejectedWhenFreeOnly() {
        val error = ProviderPolicy.validateModel(model("paid-model", false), freeOnly = true)
        assertThat(error).isNotNull()
        assertThat(error!!.type).isEqualTo(ProviderErrorType.FREE_MODEL_REQUIRED)
    }

    @Test
    fun unknownRejectedWhenFreeOnly() {
        val error = ProviderPolicy.validateModel(model("mystery", null), freeOnly = true)
        assertThat(error).isNotNull()
        assertThat(error!!.type).isEqualTo(ProviderErrorType.FREE_MODEL_REQUIRED)
    }

    @Test
    fun paidAllowedWhenFreeOnlyOff() {
        assertThat(ProviderPolicy.validateModel(model("paid-model", false), freeOnly = false)).isNull()
        assertThat(ProviderPolicy.validateModel(model("mystery", null), freeOnly = false)).isNull()
    }

    @Test
    fun groqGptOssIsNotKnownAsFree() {
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.GROQ, "openai/gpt-oss-20b")).isNull()
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.GROQ, "openai/gpt-oss-120b")).isNull()
    }

    @Test
    fun geminiFreeTierModelsAreKnown() {
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.GEMINI, "gemini-2.5-flash")).isTrue()
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.GEMINI, "gemini-2.5-flash-lite")).isTrue()
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.GEMINI, "gemini-3.8-flash")).isTrue()
    }

    @Test
    fun catalogKnowsOpenRouterFreeSuffix() {
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.OPEN_ROUTER, "meta-llama/llama-3.1-8b-instruct:free"))
            .isTrue()
        assertThat(FreeModelCatalog.isKnownFree(ProviderType.OPEN_ROUTER, "openai/gpt-4o")).isNull()
    }
}
