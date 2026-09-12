package com.agentflow.data.network.ai.openai

import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.ai.AIResponseFormatType
import com.agentflow.domain.ai.AIUsageStats
import com.agentflow.domain.provider.ProviderType

internal object OpenAiCompatMapper {

    fun toRequest(provider: ProviderType, request: AIRequest): OpenAiChatRequest {
        val messages = buildList {
            request.systemInstruction?.takeIf { it.isNotBlank() }?.let {
                add(OpenAiMessage(role = "system", content = it))
            }
            request.messages.forEach { msg ->
                add(
                    OpenAiMessage(
                        role = when (msg.role) {
                            AIMessageRole.SYSTEM -> "system"
                            AIMessageRole.USER -> "user"
                            AIMessageRole.ASSISTANT -> "assistant"
                            AIMessageRole.TOOL -> "user"
                        },
                        content = if (msg.role == AIMessageRole.TOOL) "[tool]\n${msg.content}" else msg.content,
                    ),
                )
            }
        }
        val format = when (request.responseFormat.type) {
            AIResponseFormatType.TEXT -> null
            AIResponseFormatType.JSON_OBJECT -> OpenAiResponseFormat(type = "json_object")
            AIResponseFormatType.JSON_SCHEMA -> OpenAiResponseFormat(
                type = "json_schema",
                jsonSchema = OpenAiJsonSchema(
                    name = request.responseFormat.schemaName ?: "response",
                    schema = request.responseFormat.schema,
                ),
            )
        }
        return OpenAiChatRequest(
            model = request.model,
            messages = messages,
            temperature = request.options.temperature,
            maxTokens = request.options.maxOutputTokens,
            topP = request.options.topP,
            responseFormat = format,
            reasoning = request.options.reasoningLevel
                ?.takeIf { provider != ProviderType.GROQ }
                ?.let { OpenAiReasoning(effort = it) },
            reasoningEffort = request.options.reasoningLevel
                ?.takeIf { provider == ProviderType.GROQ },
        )
    }

    fun toResponse(
        provider: ProviderType,
        fallbackModel: String,
        raw: OpenAiChatResponse,
        latencyMs: Long,
    ): AIResponse {
        val content = raw.choices.orEmpty().firstOrNull()?.message?.content.orEmpty()
        return AIResponse(
            provider = provider,
            model = raw.model ?: fallbackModel,
            content = content,
            finishReason = raw.choices?.firstOrNull()?.finishReason,
            usage = AIUsageStats(
                inputTokens = raw.usage?.promptTokens,
                outputTokens = raw.usage?.completionTokens,
                totalTokens = raw.usage?.totalTokens,
                latencyMs = latencyMs,
            ),
            latencyMs = latencyMs,
            requestId = raw.id,
        )
    }
}
