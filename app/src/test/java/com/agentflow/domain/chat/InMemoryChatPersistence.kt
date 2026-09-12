package com.agentflow.domain.chat

import com.agentflow.domain.model.Ids
import com.agentflow.domain.provider.ProviderType

class InMemoryChatPersistence : ChatPersistence {
    val sessions = linkedMapOf<String, ChatSession>()
    val messages = mutableListOf<ChatMessage>()

    fun put(session: ChatSession) {
        sessions[session.id] = session
    }

    override suspend fun getSession(id: String): ChatSession? = sessions[id]

    override suspend fun sendUserMessage(sessionId: String, content: String, now: Long): ChatMessage {
        val msg = ChatMessage(Ids.new(), sessionId, ChatRole.USER, content, ChatMessageStatus.COMPLETED, now, now)
        messages += msg
        return msg
    }

    override suspend fun startAssistantMessage(
        sessionId: String,
        generationGroupId: String,
        provider: ProviderType?,
        model: String?,
        now: Long,
    ): ChatMessage {
        val msg = ChatMessage(
            id = Ids.new(),
            sessionId = sessionId,
            role = ChatRole.ASSISTANT,
            content = "",
            status = ChatMessageStatus.STREAMING,
            createdAt = now,
            updatedAt = now,
            provider = provider,
            model = model,
            generationGroupId = generationGroupId,
        )
        messages += msg
        return msg
    }

    override suspend fun appendAssistantChunk(messageId: String, content: String, now: Long) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) messages[idx] = messages[idx].copy(content = content, updatedAt = now)
    }

    override suspend fun completeAssistantMessage(
        messageId: String,
        content: String,
        provider: ProviderType?,
        model: String?,
        latencyMs: Long?,
        inputTokens: Int?,
        outputTokens: Int?,
        now: Long,
    ) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(
                content = content,
                status = ChatMessageStatus.COMPLETED,
                provider = provider,
                model = model,
                latencyMs = latencyMs,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                updatedAt = now,
            )
        }
    }

    override suspend fun failAssistantMessage(messageId: String, content: String, errorCode: String?, now: Long) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) messages[idx] = messages[idx].copy(content = content, status = ChatMessageStatus.FAILED, errorCode = errorCode, updatedAt = now)
    }

    override suspend fun cancelAssistantMessage(messageId: String, content: String, now: Long) {
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx >= 0) messages[idx] = messages[idx].copy(content = content, status = ChatMessageStatus.CANCELLED, updatedAt = now)
    }

    override suspend fun listMessages(sessionId: String): List<ChatMessage> =
        messages.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.createdAt }, { it.id }))
}
