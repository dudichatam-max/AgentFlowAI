package com.agentflow.domain.model
data class AgentMessage(
    val id: String, val missionId: String? = null, val taskId: String? = null,
    val sessionId: String? = null, val senderType: ActorType, val senderId: String? = null,
    val recipientType: ActorType, val recipientId: String? = null, val messageType: MessageType,
    val content: String, val contractVersion: Int = 1, val createdAt: Long,
    val providerId: ProviderId? = null, val modelId: String? = null, val latencyMs: Long? = null,
    val inputTokens: Int? = null, val outputTokens: Int? = null, val success: Boolean = true,
    val errorCode: String? = null,
)
