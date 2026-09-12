package com.agentflow.data.network

import com.agentflow.data.network.ai.gemini.GeminiGenerateResponse
import com.agentflow.data.network.ai.gemini.GeminiMapper
import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIRequestOptions
import com.agentflow.domain.ai.AIResponseFormat
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class GeminiMapperTest {
    @Test
    fun mapsRolesAndSystem() {
        val request = AIRequest(
            messages = listOf(
                AIMessage(AIMessageRole.SYSTEM, "be terse"),
                AIMessage(AIMessageRole.USER, "hello"),
                AIMessage(AIMessageRole.ASSISTANT, "hi"),
            ),
            model = "gemini-2.5-flash",
            systemInstruction = "extra",
            options = AIRequestOptions(temperature = 0.2, maxOutputTokens = 128),
            responseFormat = AIResponseFormat.jsonSchema("task", buildJsonObject { put("type", "object") }),
        )
        val body = GeminiMapper.toRequest(request)
        assertThat(body.systemInstruction?.parts?.first()?.text).contains("extra")
        assertThat(body.systemInstruction?.parts?.first()?.text).contains("be terse")
        assertThat(body.contents.map { it.role }).containsExactly("user", "model").inOrder()
        assertThat(body.generationConfig?.responseMimeType).isEqualTo("application/json")
        assertThat(body.generationConfig?.temperature).isEqualTo(0.2)
    }

    @Test
    fun usesThinkingBudgetForGemini25() {
        val request = AIRequest(
            messages = listOf(AIMessage(AIMessageRole.USER, "solve")),
            model = "gemini-2.5-flash",
            options = AIRequestOptions(reasoningLevel = "high"),
        )
        val body = GeminiMapper.toRequest(request)
        assertThat(body.generationConfig?.thinkingConfig?.thinkingBudget).isEqualTo(8192)
        assertThat(body.generationConfig?.thinkingConfig?.thinkingLevel).isNull()
    }

    @Test
    fun usesThinkingLevelForGemini3() {
        val request = AIRequest(
            messages = listOf(AIMessage(AIMessageRole.USER, "solve")),
            model = "gemini-3.8-flash",
            options = AIRequestOptions(reasoningLevel = "high"),
        )
        val body = GeminiMapper.toRequest(request)
        assertThat(body.generationConfig?.thinkingConfig?.thinkingLevel).isEqualTo("high")
        assertThat(body.generationConfig?.thinkingConfig?.thinkingBudget).isNull()
    }

    @Test
    fun omitsThinkingConfigForUnknownGeminiFamily() {
        val request = AIRequest(
            messages = listOf(AIMessage(AIMessageRole.USER, "solve")),
            model = "gemini-unknown",
            options = AIRequestOptions(reasoningLevel = "high"),
        )
        val body = GeminiMapper.toRequest(request)
        assertThat(body.generationConfig?.thinkingConfig).isNull()
    }

    @Test
    fun parsesMissingUsage() {
        val raw = GeminiGenerateResponse(
            candidates = emptyList(),
            usageMetadata = null,
        )
        val response = GeminiMapper.toResponse("gemini-2.5-flash", raw, 12)
        assertThat(response.content).isEmpty()
        assertThat(response.usage.inputTokens).isNull()
        assertThat(response.latencyMs).isEqualTo(12)
    }
}
