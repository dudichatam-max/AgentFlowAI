package com.agentflow.ui.chat

import com.agentflow.domain.chat.ChatMessage
import com.agentflow.domain.chat.ChatSession
import com.agentflow.domain.model.Agent

data class ChatUiState(
    val session: ChatSession? = null,
    val agent: Agent? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingMessageId: String? = null,
    val error: String? = null,
    val inputText: String = "",
    val canSend: Boolean = false,
    val offline: Boolean = false,
)
