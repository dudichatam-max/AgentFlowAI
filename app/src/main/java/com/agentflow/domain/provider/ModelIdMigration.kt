package com.agentflow.domain.provider

object ModelIdMigration {
    private val migrations = mapOf(
        ProviderType.GROQ to mapOf(
            "llama-3.1-8b-instant" to "openai/gpt-oss-20b",
            "llama-3.3-70b-versatile" to "openai/gpt-oss-120b",
        ),
    )

    fun migrate(provider: ProviderType, modelId: String): String =
        migrations[provider]?.entries?.firstOrNull { it.key.equals(modelId, ignoreCase = true) }?.value
            ?: modelId
}
