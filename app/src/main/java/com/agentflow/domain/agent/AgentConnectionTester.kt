package com.agentflow.domain.agent

import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIRequestOptions
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType

data class AgentTestResult(
    val success: Boolean,
    val provider: ProviderType?,
    val model: String,
    val latencyMs: Long,
    val errorCategory: String? = null,
    val preview: String = "",
)

class AgentConnectionTester(
    private val manager: ProviderManager,
) {
    suspend fun test(agent: Agent, rules: List<AgentRule>): AgentTestResult {
        val brain = agent.toBrain(rules)
        val prompt = AgentPromptBuilder.build(brain)
        val model = agent.toModelConfig()
        val primary = model.providerType
        val request = AIRequest(
            messages = listOf(AIMessage(AIMessageRole.USER, "Respond with exactly: AGENTFLOW_OK")),
            model = model.modelId,
            options = AIRequestOptions(temperature = 0.0, maxOutputTokens = 16),
            systemInstruction = prompt,
            preferredProvider = primary,
        )
        val started = System.currentTimeMillis()
        return when (val result = manager.generate(
                request,
                primary,
                listOfNotNull(model.fallbackProviderType),
                fallbackModels = buildMap {
                    val provider = model.fallbackProviderType
                    val modelId = model.fallbackModelId
                    if (provider != null && modelId != null) put(provider, modelId)
                },
            )) {
            is ProviderResult.Success -> {
                val text = result.value.content
                AgentTestResult(
                    success = text.contains("AGENTFLOW_OK"),
                    provider = result.value.provider,
                    model = result.value.model,
                    latencyMs = result.value.latencyMs,
                    preview = text.take(80),
                    errorCategory = if (text.contains("AGENTFLOW_OK")) null else "UNEXPECTED_OUTPUT",
                )
            }
            is ProviderResult.Failure -> AgentTestResult(
                success = false,
                provider = primary,
                model = model.modelId,
                latencyMs = System.currentTimeMillis() - started,
                errorCategory = result.error.type.name,
                preview = result.error.message.take(80),
            )
        }
    }
}
