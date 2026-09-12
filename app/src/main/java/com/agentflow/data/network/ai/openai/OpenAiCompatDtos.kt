package com.agentflow.data.network.ai.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat? = null,
    val reasoning: OpenAiReasoning? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    val stream: Boolean? = null,
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: OpenAiJsonSchema? = null,
)

@Serializable
data class OpenAiJsonSchema(
    val name: String,
    val schema: JsonObject? = null,
    val strict: Boolean? = true,
)

@Serializable
data class OpenAiReasoning(
    val effort: String? = null,
)

@Serializable
data class OpenAiChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<OpenAiChoice>? = null,
    val usage: OpenAiUsage? = null,
    val error: OpenAiErrorBody? = null,
)

@Serializable
data class OpenAiChoice(
    val index: Int? = null,
    val message: OpenAiMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

@Serializable
data class OpenAiErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: JsonElement? = null,
)

@Serializable
data class OpenAiErrorEnvelope(
    val error: OpenAiErrorBody? = null,
)

@Serializable
data class OpenAiModelList(
    val data: List<OpenAiModelCard>? = null,
)

@Serializable
data class OpenAiModelCard(
    val id: String? = null,
    val name: String? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    val pricing: OpenAiPricing? = null,
)

@Serializable
data class OpenAiPricing(
    val prompt: String? = null,
    val completion: String? = null,
)
