package com.agentflow.data.network.ai.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    val text: String? = null,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val topP: Double? = null,
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val thinkingConfig: GeminiThinkingConfig? = null,
)

@Serializable
data class GeminiThinkingConfig(
    val thinkingLevel: String? = null,
    val thinkingBudget: Int? = null,
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsage? = null,
    @SerialName("responseId") val responseId: String? = null,
    val error: GeminiApiError? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
data class GeminiUsage(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
)

@Serializable
data class GeminiApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
)

@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModelInfo>? = null,
)

@Serializable
data class GeminiModelInfo(
    val name: String? = null,
    val displayName: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String>? = null,
)
