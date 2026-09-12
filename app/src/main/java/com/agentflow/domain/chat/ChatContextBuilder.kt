package com.agentflow.domain.chat

import com.agentflow.domain.ai.AIMessage
import com.agentflow.domain.ai.AIMessageRole

/**
 * Bounds conversation history before it reaches a provider.
 * Phase 5 can add references/files without rewriting ChatService.
 */
class ChatContextBuilder(
    private val charBudget: Int = ChatLimits.CONTEXT_CHAR_BUDGET,
    private val maxMessages: Int = ChatLimits.CONTEXT_MAX_MESSAGES,
) {
    data class Prepared(
        val systemInstruction: String,
        val messages: List<AIMessage>,
    )

    fun prepare(
        systemInstruction: String,
        history: List<ChatMessage>,
        latestUserText: String,
    ): Prepared {
        val visible = history.filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
            .filter { it.status != ChatMessageStatus.PENDING }
            .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })

        val latest = AIMessage(AIMessageRole.USER, latestUserText)
        val selected = ArrayDeque<AIMessage>()
        var used = systemInstruction.length + latestUserText.length

        val older = visible.dropLastWhile { it.role == ChatRole.USER && it.content == latestUserText }
        for (msg in older.asReversed()) {
            if (selected.size >= maxMessages) break
            val role = when (msg.role) {
                ChatRole.ASSISTANT -> AIMessageRole.ASSISTANT
                ChatRole.SYSTEM -> AIMessageRole.SYSTEM
                ChatRole.TOOL -> AIMessageRole.TOOL
                ChatRole.USER -> AIMessageRole.USER
            }
            val piece = msg.content
            if (used + piece.length > charBudget) break
            selected.addFirst(AIMessage(role, piece))
            used += piece.length
        }
        selected.addLast(latest)
        return Prepared(systemInstruction = systemInstruction, messages = selected.toList())
    }
}
