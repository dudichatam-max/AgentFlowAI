package com.agentflow.domain.chat

import com.agentflow.domain.agent.AgentPromptBuilder
import com.agentflow.domain.agent.toBrain
import com.agentflow.domain.agent.toModelConfig
import com.agentflow.domain.ai.AIRequest
import com.agentflow.domain.ai.AIRequestOptions
import com.agentflow.domain.ai.AIStreamEvent
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.Ids
import com.agentflow.domain.provider.ProviderError
import com.agentflow.domain.provider.ProviderErrorType
import com.agentflow.domain.provider.ProviderManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Direct Chat orchestrator. Does not create Missions.
 */
class ChatService(
    private val persistence: ChatPersistence,
    private val manager: ProviderManager,
    private val loadAgent: suspend (String) -> Agent?,
    private val loadRules: suspend (String) -> List<AgentRule>,
    private val contextBuilder: ChatContextBuilder = ChatContextBuilder(),
    private val projectContextBuilder: com.agentflow.domain.context.ContextBuilder? = null,
    private val loadReferences: suspend (String) -> List<com.agentflow.domain.model.Reference> = { emptyList() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun send(
        sessionId: String,
        userText: String,
        generationGroupId: String = Ids.new(),
    ): Flow<ChatStreamEvent> = flow {
        val session = persistence.getSession(sessionId)
            ?: error("Chat session $sessionId not found")
        val agent = loadAgent(session.agentId)
            ?: error("Agent ${session.agentId} not found")
        val brain = agent.toBrain(loadRules(agent.id))
        val system = AgentPromptBuilder.build(brain)
        val refs = projectContextBuilder?.build(
            com.agentflow.domain.context.ContextRequest(
                projectId = session.projectId,
                agentId = agent.id,
                userQuery = userText.trim(),
                chatSessionId = sessionId,
            ),
            loadReferences(session.projectId),
        )
        val systemWithRefs = if (refs == null || refs.selectedDocuments.isEmpty()) {
            system
        } else {
            system + "\n\nPROJECT CONTEXT\n" + refs.renderBlock()
        }
        persistence.sendUserMessage(sessionId, userText.trim(), now())
        val history = persistence.listMessages(sessionId)
        val prepared = contextBuilder.prepare(systemWithRefs, history, userText.trim())
        val model = agent.toModelConfig()
        val request = AIRequest(
            messages = prepared.messages,
            model = model.modelId,
            options = AIRequestOptions(
                temperature = model.temperature,
                maxOutputTokens = model.maxOutputTokens,
                reasoningLevel = model.reasoningLevel.name.lowercase(),
            ),
            systemInstruction = prepared.systemInstruction,
            preferredProvider = model.providerType,
        )
        val assistant = persistence.startAssistantMessage(
            sessionId = sessionId,
            generationGroupId = generationGroupId,
            provider = model.providerType,
            model = model.modelId,
            now = now(),
        )
        emit(ChatStreamEvent.Started(assistant.id))

        val fallbacks = listOfNotNull(model.fallbackProviderType)
            .filter { it != model.providerType }
        var accumulated = ""
        var lastPersist = now()
        var lastPersistLen = 0
        val startedAt = now()

        try {
            manager.stream(
                    request,
                    model.providerType,
                    fallbacks,
                    fallbackModels = buildMap {
                    val provider = model.fallbackProviderType
                    val modelId = model.fallbackModelId
                    if (provider != null && modelId != null) put(provider, modelId)
                },
                ).collect { event ->
                if (!currentCoroutineContext().isActive) throw CancellationException()
                when (event) {
                    is AIStreamEvent.Started -> Unit
                    is AIStreamEvent.Chunk -> {
                        accumulated += event.text
                        emit(ChatStreamEvent.Chunk(assistant.id, event.text, accumulated))
                        val elapsed = now() - lastPersist
                        if (elapsed >= ChatLimits.PERSIST_INTERVAL_MS ||
                            accumulated.length - lastPersistLen >= ChatLimits.PERSIST_CHAR_THRESHOLD
                        ) {
                            persistence.appendAssistantChunk(assistant.id, accumulated, now())
                            lastPersist = now()
                            lastPersistLen = accumulated.length
                        }
                    }
                    is AIStreamEvent.Completed -> {
                        accumulated = event.response.content.ifBlank { accumulated }
                        persistence.completeAssistantMessage(
                            messageId = assistant.id,
                            content = accumulated,
                            provider = event.response.provider,
                            model = event.response.model,
                            latencyMs = event.response.latencyMs,
                            inputTokens = event.response.usage.inputTokens,
                            outputTokens = event.response.usage.outputTokens,
                            now = now(),
                        )
                        emit(ChatStreamEvent.Completed(assistant.id, accumulated))
                    }
                    is AIStreamEvent.Failed -> {
                        persistence.failAssistantMessage(
                            assistant.id,
                            accumulated,
                            event.error.type.name,
                            now(),
                        )
                        emit(ChatStreamEvent.Failed(assistant.id, event.error, accumulated))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            persistence.cancelAssistantMessage(assistant.id, accumulated, now())
            emit(ChatStreamEvent.Cancelled(assistant.id, accumulated))
            throw cancelled
        } catch (t: Throwable) {
            val error = ProviderError.of(
                model.providerType,
                ProviderErrorType.UNKNOWN,
                t.message ?: "Chat failed",
            )
            persistence.failAssistantMessage(assistant.id, accumulated, error.type.name, now())
            emit(ChatStreamEvent.Failed(assistant.id, error, accumulated))
        }
        // latency is recorded on Completed from the provider; startedAt kept for future stats
    }

    fun userFacingError(error: ProviderError): String = when (error.type) {
        ProviderErrorType.AUTHENTICATION_ERROR -> "API key is missing."
        ProviderErrorType.AUTHORIZATION_ERROR -> "Provider rejected the request."
        ProviderErrorType.RATE_LIMITED, ProviderErrorType.QUOTA_EXCEEDED ->
            "Provider rate limit reached. Please try again later."
        ProviderErrorType.MODEL_NOT_FOUND -> "Model is unavailable."
        ProviderErrorType.NETWORK_ERROR -> "Network connection failed."
        ProviderErrorType.TIMEOUT -> "Generation timed out."
        ProviderErrorType.FREE_MODEL_REQUIRED -> "This model is not allowed under free-only."
        else -> error.message
    }
}
