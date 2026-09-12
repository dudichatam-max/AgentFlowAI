package com.agentflow.domain.chat

import com.agentflow.domain.provider.ProviderType

interface ChatPersistence {
    suspend fun getSession(id: String): ChatSession?
    suspend fun sendUserMessage(sessionId: String, content: String, now: Long = System.currentTimeMillis()): ChatMessage
    suspend fun startAssistantMessage(
        sessionId: String,
        generationGroupId: String,
        provider: ProviderType?,
        model: String?,
        now: Long = System.currentTimeMillis(),
    ): ChatMessage
    suspend fun appendAssistantChunk(messageId: String, content: String, now: Long = System.currentTimeMillis())
    suspend fun completeAssistantMessage(
        messageId: String,
        content: String,
        provider: ProviderType?,
        model: String?,
        latencyMs: Long?,
        inputTokens: Int?,
        outputTokens: Int?,
        now: Long = System.currentTimeMillis(),
    )
    suspend fun failAssistantMessage(messageId: String, content: String, errorCode: String?, now: Long = System.currentTimeMillis())
    suspend fun cancelAssistantMessage(messageId: String, content: String, now: Long = System.currentTimeMillis())
    suspend fun listMessages(sessionId: String): List<ChatMessage>
}
