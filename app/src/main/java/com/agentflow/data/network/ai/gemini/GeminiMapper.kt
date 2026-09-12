package com.agentflow.data.network.ai.gemini

import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIResponse
import com.agentflow.domain.ai.AIResponseFormatType
import com.agentflow.domain.ai.AIUsageStats
import com.agentflow.domain.provider.FreeModelCatalog
import com.agentflow.domain.provider.ProviderModel
import com.agentflow.domain.provider.ProviderType

object GeminiMapper {

    fun toRequest(request: AIRequest): GeminiGenerateRequest {
        val systemFromField = request.systemInstruction
        val systemFromMessages = request.messages
            .filter { it.role == AIMessageRole.SYSTEM }
            .joinToString("\n") { it.content }
            .ifBlank { null }
        val system = listOfNotNull(systemFromField, systemFromMessages)
            .joinToString("\n")
            .ifBlank { null }

        val contents = request.messages
            .filter { it.role != AIMessageRole.SYSTEM }
            .map { msg ->
                GeminiContent(
                    role = when (msg.role) {
                        AIMessageRole.ASSISTANT -> "model"
                        else -> "user"
                    },
                    parts = listOf(GeminiPart(text = formatContent(msg))),
                )
            }

        val format = request.responseFormat
        val generation = GeminiGenerationConfig(
            temperature = request.options.temperature,
            maxOutputTokens = request.options.maxOutputTokens,
            topP = request.options.topP,
            responseMimeType = when (format.type) {
                AIResponseFormatType.TEXT -> null
                AIResponseFormatType.JSON_OBJECT,
                AIResponseFormatType.JSON_SCHEMA,
                -> "application/json"
            },
            responseSchema = if (format.type == AIResponseFormatType.JSON_SCHEMA) format.schema else null,
            thinkingConfig = thinkingConfigFor(
                model = request.model,
                reasoningLevel = request.options.reasoningLevel,
            ),
        )

        return GeminiGenerateRequest(
            contents = contents.ifEmpty {
                listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = ""))))
            },
            systemInstruction = system?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) },
            generationConfig = generation,
        )
    }

    fun toResponse(model: String, raw: GeminiGenerateResponse, latencyMs: Long): AIResponse {
        val text = raw.candidates.orEmpty()
            .flatMap { it.content?.parts.orEmpty() }
            .mapNotNull { it.text }
            .joinToString("")
        val usage = raw.usageMetadata
        return AIResponse(
            provider = ProviderType.GEMINI,
            model = model,
            content = text,
            finishReason = raw.candidates?.firstOrNull()?.finishReason,
            usage = AIUsageStats(
                inputTokens = usage?.promptTokenCount,
                outputTokens = usage?.candidatesTokenCount,
                totalTokens = usage?.totalTokenCount,
                latencyMs = latencyMs,
            ),
            latencyMs = latencyMs,
            requestId = raw.responseId,
        )
    }

    fun toModel(info: GeminiModelInfo): ProviderModel? {
        val rawName = info.name ?: return null
        val id = rawName.removePrefix("models/")
        return ProviderModel(
            id = id,
            displayName = info.displayName ?: id,
            provider = ProviderType.GEMINI,
            isFree = FreeModelCatalog.isKnownFree(ProviderType.GEMINI, id),
            contextWindow = info.inputTokenLimit,
            maxOutputTokens = info.outputTokenLimit,
            supportsStructuredOutput = true,
            supportsTools = info.supportedGenerationMethods?.contains("generateContent"),
        )
    }



    private fun thinkingConfigFor(
        model: String,
        reasoningLevel: String?,
    ): GeminiThinkingConfig? {
        val level = reasoningLevel?.lowercase()?.takeIf { it in setOf("minimal", "low", "medium", "high") }
            ?: return null
        return if (model.lowercase().startsWith("gemini-2.5")) {
            GeminiThinkingConfig(thinkingBudget = when (level) {
                "minimal", "low" -> 1_024
                "medium" -> 4_096
                "high" -> 8_192
                else -> return null
            })
        } else if (model.lowercase().startsWith("gemini-3")) {
            GeminiThinkingConfig(thinkingLevel = if (level == "minimal" && model.lowercase().contains("3.8")) "low" else level)
        } else {
            null
        }
    }

    private fun formatContent(message: AIMessage): String =
        if (message.role == AIMessageRole.TOOL) "[tool]\n${message.content}" else message.content
}
