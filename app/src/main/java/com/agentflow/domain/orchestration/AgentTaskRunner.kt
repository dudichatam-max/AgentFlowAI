package com.agentflow.domain.orchestration

import com.agentflow.domain.agent.AgentPromptBuilder
import com.agentflow.domain.agent.toBrain
import com.agentflow.domain.agent.toModelConfig
import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIRequestOptions
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.Task
import com.agentflow.domain.model.TaskResult
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.task.TaskWorkResult
import com.agentflow.domain.task.TaskFailureKind
import com.agentflow.domain.task.TaskWorker

class AgentTaskRunner(
    private val manager: ProviderManager,
    private val loadRules: suspend (String) -> List<AgentRule>,
) : TaskWorker {
    override suspend fun execute(
        task: Task,
        agent: Agent,
        dependencyResults: List<TaskResult>,
        contextBlock: String,
    ): TaskWorkResult {
        val brain = agent.toBrain(loadRules(agent.id))
        val system = AgentPromptBuilder.build(brain)
        val prior = dependencyResults.joinToString("\n\n") { "Result of prior task:\n${it.content.take(2_000)}" }
        val user = buildString {
            appendLine("TASK: ${task.title}")
            appendLine(task.description)
            if (prior.isNotBlank()) {
                appendLine()
                appendLine(prior)
            }
            if (contextBlock.isNotBlank()) {
                appendLine()
                appendLine(contextBlock.take(3_000))
            }
        }
        val model = agent.toModelConfig()
        val request = AIRequest(
            messages = listOf(AIMessage(AIMessageRole.USER, user)),
            model = model.modelId,
            options = AIRequestOptions(
                temperature = model.temperature,
                maxOutputTokens = model.maxOutputTokens,
                reasoningLevel = model.reasoningLevel.name.lowercase(),
            ),
            systemInstruction = system,
            preferredProvider = model.providerType,
        )
        return when (val result = manager.generate(
                request,
                model.providerType,
                listOfNotNull(model.fallbackProviderType),
                fallbackModels = buildMap {
                    val provider = model.fallbackProviderType
                    val modelId = model.fallbackModelId
                    if (provider != null && modelId != null) put(provider, modelId)
                },
            )) {
            is ProviderResult.Success -> TaskWorkResult(result.value.content, success = true)
            is ProviderResult.Failure -> TaskWorkResult(
                result.error.message,
                success = false,
                failureKind = if (result.error.retryable) TaskFailureKind.TRANSIENT else TaskFailureKind.PERMANENT,
            )
        }
    }
}
