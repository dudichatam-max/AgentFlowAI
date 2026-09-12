package com.agentflow.domain.provider

/**
 * Enforced below the UI. A paid or unknown-cost model must never leave
 * this layer when [freeOnly] is on.
 */
object ProviderPolicy {

    fun validateModel(
        model: ProviderModel,
        freeOnly: Boolean,
    ): ProviderError? {
        if (!freeOnly) return null
        return when (model.isFree) {
            true -> null
            false -> ProviderError.of(
                provider = model.provider,
                type = ProviderErrorType.FREE_MODEL_REQUIRED,
                message = "Model ${model.id} is not a free model.",
            )
            null -> ProviderError.of(
                provider = model.provider,
                type = ProviderErrorType.FREE_MODEL_REQUIRED,
                message = "Model ${model.id} has unknown cost status; refused under free-only.",
            )
        }
    }

    fun validateModelId(
        provider: ProviderType,
        modelId: String,
        freeOnly: Boolean,
        catalog: List<ProviderModel>,
    ): ProviderError? {
        val known = catalog.firstOrNull { it.id.equals(modelId, ignoreCase = true) }
        val model = known ?: ProviderModel(
            id = modelId,
            displayName = modelId,
            provider = provider,
            isFree = FreeModelCatalog.isKnownFree(provider, modelId),
        )
        return validateModel(model, freeOnly)
    }
}

/**
 * Explicit allow-list. Unknown IDs stay unknown (`null`), never "probably free".
 */
object FreeModelCatalog {

    private val geminiFree = setOf(
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-3.8-flash",
    )

    fun isKnownFree(provider: ProviderType, modelId: String): Boolean? {
        val id = modelId.lowercase().removePrefix("models/")
        return when (provider) {
            ProviderType.GEMINI -> if (geminiFree.contains(id)) true else null
            ProviderType.GROQ -> null
            ProviderType.OPEN_ROUTER -> when {
                id.endsWith(":free") -> true
                id.contains(":free") -> true
                else -> null
            }
        }
    }
}
