package com.agentflow.domain.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelIdMigrationTest {
    @Test
    fun migratesDeprecatedGroqModels() {
        assertThat(
            ModelIdMigration.migrate(ProviderType.GROQ, "llama-3.1-8b-instant"),
        ).isEqualTo("openai/gpt-oss-20b")
        assertThat(
            ModelIdMigration.migrate(ProviderType.GROQ, "llama-3.3-70b-versatile"),
        ).isEqualTo("openai/gpt-oss-120b")
    }

    @Test
    fun leavesCurrentModelsUntouched() {
        assertThat(
            ModelIdMigration.migrate(ProviderType.GROQ, "openai/gpt-oss-20b"),
        ).isEqualTo("openai/gpt-oss-20b")
    }
}
