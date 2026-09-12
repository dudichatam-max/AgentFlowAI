package com.agentflow.data.network.ai.openai

import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIRequestOptions
import com.agentflow.domain.provider.ProviderType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class OpenAiCompatMapperTest {
    private val request = AIRequest(
        messages = listOf(AIMessage(AIMessageRole.USER, "test")),
        model = "openai/gpt-oss-20b",
        options = AIRequestOptions(reasoningLevel = "high"),
    )

    private val json = Json { encodeDefaults = false }

    @Test
    fun groqUsesReasoningEffortInsteadOfUnsupportedReasoningObject() {
        val mapped = OpenAiCompatMapper.toRequest(ProviderType.GROQ, request)
        val encoded = json.encodeToString(OpenAiChatRequest.serializer(), mapped)
        assertThat(encoded).contains("\"reasoning_effort\":\"high\"")
        assertThat(encoded).doesNotContain("\"reasoning\":")
    }

    @Test
    fun openRouterKeepsReasoningObject() {
        val mapped = OpenAiCompatMapper.toRequest(ProviderType.OPEN_ROUTER, request)
        val encoded = json.encodeToString(OpenAiChatRequest.serializer(), mapped)
        assertThat(encoded).contains("\"reasoning\":{\"effort\":\"high\"}")
        assertThat(mapped.reasoningEffort).isNull()
    }
}
