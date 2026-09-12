package com.agentflow.domain.chat

import com.agentflow.domain.provider.ProviderError

sealed class ChatStreamEvent {
    data class Started(val messageId: String) : ChatStreamEvent()
    data class Chunk(val messageId: String, val text: String, val accumulated: String) : ChatStreamEvent()
    data class Completed(val messageId: String, val content: String) : ChatStreamEvent()
    data class Failed(val messageId: String, val error: ProviderError, val content: String) : ChatStreamEvent()
    data class Cancelled(val messageId: String, val content: String) : ChatStreamEvent()
}
