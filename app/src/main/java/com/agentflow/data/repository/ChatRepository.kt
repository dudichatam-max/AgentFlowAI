package com.agentflow.data.repository

import com.agentflow.data.dao.ChatAttachmentDao
import com.agentflow.data.dao.ChatMessageDao
import com.agentflow.data.dao.ChatSessionDao
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.chat.ChatLimits
import com.agentflow.domain.chat.ChatMessage
import com.agentflow.domain.chat.ChatMessageStatus
import com.agentflow.domain.chat.ChatRole
import com.agentflow.domain.chat.ChatSession
import com.agentflow.domain.chat.ChatSessionStatus
import com.agentflow.domain.model.Ids
import com.agentflow.domain.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val sessionDao: ChatSessionDao,
    private val messageDao: ChatMessageDao,
    private val attachmentDao: ChatAttachmentDao,
) : com.agentflow.domain.chat.ChatPersistence {
    constructor(db: AgentFlowDatabase) : this(db.chatSessionDao(), db.chatMessageDao(), db.chatAttachmentDao())

    suspend fun createSession(projectId: String, agentId: String, now: Long = System.currentTimeMillis()): ChatSession {
        val session = ChatSession(
            id = Ids.new(),
            projectId = projectId,
            agentId = agentId,
            title = ChatLimits.TITLE_DEFAULT,
            status = ChatSessionStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            lastMessageAt = now,
        )
        sessionDao.insert(session.toEntity())
        return session
    }

    override suspend fun getSession(id: String): ChatSession? = sessionDao.getById(id)?.toDomain()

    fun observeSession(id: String): Flow<ChatSession?> = sessionDao.observeById(id).map { it?.toDomain() }

    fun observeSessionsForAgent(agentId: String): Flow<List<ChatSession>> =
        sessionDao.observeForAgent(agentId).map { list -> list.map { it.toDomain() } }

    fun observeSessionsForProject(projectId: String): Flow<List<ChatSession>> =
        sessionDao.observeForProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun renameSession(id: String, title: String, now: Long) {
        sessionDao.updateTitle(id, title.trim().ifBlank { ChatLimits.TITLE_DEFAULT }, now)
    }

    suspend fun archiveSession(id: String, now: Long) {
        sessionDao.updateStatus(id, ChatSessionStatus.ARCHIVED.name, now)
    }

    suspend fun deleteSession(id: String) {
        messageDao.deleteForSession(id)
        sessionDao.delete(id)
    }

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.observeForSession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun listMessages(sessionId: String): List<ChatMessage> =
        messageDao.listForSession(sessionId).map { it.toDomain() }

    suspend fun getRecentMessages(sessionId: String, limit: Int): List<ChatMessage> =
        messageDao.getRecentMessages(sessionId, limit).map { it.toDomain() }.reversed()

    suspend fun insertMessage(message: ChatMessage): ChatMessage {
        messageDao.insert(message.toEntity())
        sessionDao.updateLastMessageAt(message.sessionId, message.createdAt)
        return message
    }

    override suspend fun sendUserMessage(sessionId: String, content: String, now: Long): ChatMessage {
        val message = ChatMessage(
            id = Ids.new(),
            sessionId = sessionId,
            role = ChatRole.USER,
            content = content,
            status = ChatMessageStatus.COMPLETED,
            createdAt = now,
            updatedAt = now,
        )
        return insertMessage(message)
    }

    override suspend fun startAssistantMessage(
        sessionId: String,
        generationGroupId: String,
        provider: ProviderType?,
        model: String?,
        now: Long,
    ): ChatMessage {
        val message = ChatMessage(
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
        return insertMessage(message)
    }

    override suspend fun appendAssistantChunk(messageId: String, content: String, now: Long) {
        messageDao.updateContent(messageId, content, now)
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
        val current = messageDao.getById(messageId) ?: return
        messageDao.update(
            current.copy(
                content = content,
                status = ChatMessageStatus.COMPLETED.name,
                provider = provider?.name,
                model = model,
                latencyMs = latencyMs,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                updatedAt = now,
            ),
        )
    }

    override suspend fun failAssistantMessage(messageId: String, content: String, errorCode: String?, now: Long) {
        val current = messageDao.getById(messageId) ?: return
        messageDao.update(
            current.copy(
                content = content,
                status = ChatMessageStatus.FAILED.name,
                errorCode = errorCode,
                updatedAt = now,
            ),
        )
    }

    override suspend fun cancelAssistantMessage(messageId: String, content: String, now: Long) {
        val current = messageDao.getById(messageId) ?: return
        messageDao.update(
            current.copy(content = content, status = ChatMessageStatus.CANCELLED.name, updatedAt = now),
        )
    }

    suspend fun setBookmarked(messageId: String, bookmarked: Boolean) {
        val current = messageDao.getById(messageId) ?: return
        messageDao.update(current.copy(bookmarked = bookmarked, updatedAt = System.currentTimeMillis()))
    }

    suspend fun getMessage(id: String): ChatMessage? = messageDao.getById(id)?.toDomain()
}
