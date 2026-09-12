package com.agentflow.domain.ai

import com.agentflow.domain.provider.ProviderType

data class AIRequest(
    val messages: List<AIMessage>,
    val model: String,
    val options: AIRequestOptions = AIRequestOptions(),
    val responseFormat: AIResponseFormat = AIResponseFormat.Text,
    val systemInstruction: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val preferredProvider: ProviderType? = null,
)
