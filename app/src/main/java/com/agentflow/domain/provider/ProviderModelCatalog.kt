package com.agentflow.domain.provider

/**
 * Catalog metadata separate from provider execution.
 * Free/paid flags are advisory; ProviderPolicy still enforces freeOnly.
 */
data class CatalogModel(
    val id: String,
    val displayName: String,
    val provider: ProviderType,
    val isFree: Boolean?,
    val contextTokens: Int? = null,
    val structuredOutput: Boolean = false,
    val vision: Boolean = false,
    val tools: Boolean = false,
    val reasoning: Boolean = false,
    val code: Boolean = false,
)

object ProviderModelCatalog {
    fun defaults(): List<CatalogModel> = listOf(
        CatalogModel("openrouter/free", "OpenRouter free router", ProviderType.OPEN_ROUTER, true, structuredOutput = true, reasoning = true, code = true),
        CatalogModel("meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (OpenRouter free)", ProviderType.OPEN_ROUTER, true, 8192, true, false, false, true, true),
        CatalogModel("openai/gpt-oss-20b", "GPT-OSS 20B", ProviderType.GROQ, true, 131_072, true, false, true, true, true),
        CatalogModel("openai/gpt-oss-120b", "GPT-OSS 120B", ProviderType.GROQ, true, 131_072, true, false, true, true, true),
        CatalogModel("gemini-2.5-flash", "Gemini 2.5 Flash", ProviderType.GEMINI, true, 1_000_000, true, true, false, true, true),
        CatalogModel("gemini-2.0-flash", "Gemini 2.0 Flash", ProviderType.GEMINI, true, 1_000_000, true, true, false, true, true),
    )

    fun recommended(provider: ProviderType): CatalogModel? = defaults().firstOrNull { it.provider == provider }

    fun find(provider: ProviderType, modelId: String): CatalogModel? =
        defaults().firstOrNull { it.provider == provider && it.id.equals(modelId, true) }

    fun compatible(model: CatalogModel, required: Set<String>): Boolean {
        if ("vision" in required && !model.vision) return false
        if ("structured" in required && !model.structuredOutput) return false
        return true
    }
}

object DefaultModelPolicy {
    val freeOnlyDefault: Boolean = true
    val providerPriority: List<ProviderType> = listOf(
        ProviderType.OPEN_ROUTER,
        ProviderType.GROQ,
        ProviderType.GEMINI,
    )
}
