package com.agentflow.domain.chat

import com.agentflow.domain.provider.ProviderType

enum class ChatSessionStatus { ACTIVE, ARCHIVED }

enum class ChatRole { USER, ASSISTANT, SYSTEM, TOOL }

enum class ChatMessageStatus { PENDING, STREAMING, COMPLETED, FAILED, CANCELLED }

data class ChatSession(
    val id: String,
    val projectId: String,
    val agentId: String,
    val title: String,
    val status: ChatSessionStatus = ChatSessionStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessageAt: Long,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatRole,
    val content: String,
    val status: ChatMessageStatus = ChatMessageStatus.COMPLETED,
    val createdAt: Long,
    val updatedAt: Long,
    val provider: ProviderType? = null,
    val model: String? = null,
    val latencyMs: Long? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val errorCode: String? = null,
    val generationGroupId: String? = null,
    val bookmarked: Boolean = false,
)

data class ChatAttachment(
    val id: String,
    val messageId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val path: String,
)

object ChatLimits {
    const val PERSIST_INTERVAL_MS = 300L
    const val PERSIST_CHAR_THRESHOLD = 80
    const val CONTEXT_CHAR_BUDGET = 12_000
    const val CONTEXT_MAX_MESSAGES = 24
    const val TITLE_DEFAULT = "New conversation"
}

object ChatMessageStateMachine {
    private val allowed = mapOf(
        ChatMessageStatus.PENDING to setOf(
            ChatMessageStatus.STREAMING, ChatMessageStatus.FAILED, ChatMessageStatus.CANCELLED,
        ),
        ChatMessageStatus.STREAMING to setOf(
            ChatMessageStatus.COMPLETED, ChatMessageStatus.FAILED, ChatMessageStatus.CANCELLED,
        ),
        ChatMessageStatus.COMPLETED to emptySet(),
        ChatMessageStatus.FAILED to emptySet(),
        ChatMessageStatus.CANCELLED to emptySet(),
    )

    fun canTransition(from: ChatMessageStatus, to: ChatMessageStatus): Boolean =
        from == to || allowed[from].orEmpty().contains(to)
}
