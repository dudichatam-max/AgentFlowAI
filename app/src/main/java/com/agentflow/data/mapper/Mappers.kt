package com.agentflow.data.mapper
import com.agentflow.data.entity.*
import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.agent.OutputStyle
import com.agentflow.domain.agent.ReasoningLevel
import com.agentflow.domain.agent.Verbosity
import com.agentflow.domain.model.*
import com.agentflow.domain.provider.ModelIdMigration
import com.agentflow.domain.provider.ProviderType

fun ProjectEntity.toDomain() = Project(id, name, description, createdAt, updatedAt, isArchived)
fun Project.toEntity() = ProjectEntity(id, name, description, createdAt, updatedAt, isArchived)

fun AgentEntity.toDomain() = Agent(
    id = id,
    projectId = projectId,
    name = name,
    role = role,
    description = description,
    providerId = ProviderId.valueOf(providerId),
    modelId = ModelIdMigration.migrate(
        ProviderType.valueOf(providerId),
        modelId,
    ),
    fallbackProviderId = fallbackProviderId?.let { ProviderId.valueOf(it) },
    fallbackModelId = fallbackModelId?.let { fallback ->
        fallbackProviderId?.let { provider ->
            ModelIdMigration.migrate(ProviderType.valueOf(provider), fallback)
        } ?: fallback
    },
    temperature = temperature,
    maxOutputTokens = maxOutputTokens,
    reasoningLevelEnum = runCatching { ReasoningLevel.valueOf(reasoningLevel) }.getOrDefault(ReasoningLevel.MEDIUM),
    freeOnly = freeOnly,
    status = migrateStatus(status),
    outputStyle = runCatching { OutputStyle.valueOf(outputStyle) }.getOrDefault(OutputStyle.BALANCED),
    verbosity = runCatching { Verbosity.valueOf(verbosity) }.getOrDefault(Verbosity.NORMAL),
    exposeUncertainty = exposeUncertainty,
    includeAssumptions = includeAssumptions,
    includeAlternatives = includeAlternatives,
    capabilities = capabilities.split(',').mapNotNull { raw ->
        raw.trim().takeIf { it.isNotEmpty() }?.let { runCatching { AgentCapability.valueOf(it) }.getOrNull() }
    }.toSet(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Agent.toEntity() = AgentEntity(
    id = id,
    projectId = projectId,
    name = name,
    role = role,
    description = description,
    providerId = providerId.name,
    modelId = modelId,
    fallbackProviderId = fallbackProviderId?.name,
    fallbackModelId = fallbackModelId,
    temperature = temperature,
    maxOutputTokens = maxOutputTokens,
    reasoningLevel = reasoningLevelEnum.name,
    freeOnly = freeOnly,
    status = status.name,
    outputStyle = outputStyle.name,
    verbosity = verbosity.name,
    exposeUncertainty = exposeUncertainty,
    includeAssumptions = includeAssumptions,
    includeAlternatives = includeAlternatives,
    capabilities = capabilities.sortedBy { it.name }.joinToString(","),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun migrateStatus(raw: String): AgentStatus = when (raw) {
    "ENABLED" -> AgentStatus.READY
    else -> runCatching { AgentStatus.valueOf(raw) }.getOrDefault(AgentStatus.READY)
}

fun AgentRuleEntity.toDomain() = AgentRule(id, agentId, name, instruction, RulePriority.valueOf(priority), enabled, createdAt, updatedAt)
fun AgentRule.toEntity() = AgentRuleEntity(id, agentId, name, instruction, priority.name, enabled, createdAt, updatedAt)

fun ReferenceEntity.toDomain() = Reference(
    id, projectId, agentId, name, ReferenceType.valueOf(type), path, uri, sizeBytes, mimeType,
    contentHash, InclusionMode.valueOf(inclusionMode), createdAt, updatedAt,
)
fun Reference.toEntity() = ReferenceEntity(
    id, projectId, agentId, name, type.name, path, uri, sizeBytes, mimeType, contentHash,
    inclusionMode.name, createdAt, updatedAt,
)

fun MissionEntity.toDomain() = Mission(id, projectId, title, description, MissionStatus.valueOf(status), createdAt, updatedAt, startedAt, completedAt, createdBy)
fun Mission.toEntity() = MissionEntity(id, projectId, title, description, status.name, createdAt, updatedAt, startedAt, completedAt, createdBy)

fun MissionConversationEntity.toDomain() = MissionConversation(id, missionId, agentId, createdAt, updatedAt)
fun MissionConversation.toEntity() = MissionConversationEntity(id, missionId, agentId, createdAt, updatedAt)

fun TaskEntity.toDomain() = Task(
    id, missionId, parentTaskId, CreatedByType.valueOf(createdByType), createdById, assignedAgentId,
    title, description, TaskStatus.valueOf(status), Priority.valueOf(priority), inputMessageId,
    startedAt, completedAt, retryCount, createdAt, updatedAt,
)
fun Task.toEntity() = TaskEntity(
    id, missionId, parentTaskId, createdByType.name, createdById, assignedAgentId, title, description,
    status.name, priority.name, inputMessageId, startedAt, completedAt, retryCount, createdAt, updatedAt,
)

fun TaskDependencyEntity.toDomain() = TaskDependency(id, missionId, taskId, dependsOnTaskId, DependencyType.valueOf(type), createdAt)
fun TaskDependency.toEntity() = TaskDependencyEntity(id, missionId, taskId, dependsOnTaskId, type.name, createdAt)

fun TaskResultEntity.toDomain() = TaskResult(id, taskId, missionId, content, contractVersion, confidence, createdAt)
fun TaskResult.toEntity() = TaskResultEntity(id, taskId, missionId, content, contractVersion, confidence, createdAt)

fun AgentMessageEntity.toDomain() = AgentMessage(
    id, missionId, taskId, sessionId, ActorType.valueOf(senderType), senderId,
    ActorType.valueOf(recipientType), recipientId, MessageType.valueOf(messageType), content,
    contractVersion, createdAt, providerId?.let { ProviderId.valueOf(it) }, modelId, latencyMs,
    inputTokens, outputTokens, success, errorCode,
)
fun AgentMessage.toEntity() = AgentMessageEntity(
    id, missionId, taskId, sessionId, senderType.name, senderId, recipientType.name, recipientId,
    messageType.name, content, contractVersion, createdAt, providerId?.name, modelId, latencyMs,
    inputTokens, outputTokens, success, errorCode,
)

fun InspectorReviewEntity.toDomain() = InspectorReview(id, missionId, taskId, inspectorAgentId, ReviewStatus.valueOf(status), summary, reason, Severity.valueOf(severity), createdAt)
fun InspectorReview.toEntity() = InspectorReviewEntity(id, missionId, taskId, inspectorAgentId, status.name, summary, reason, severity.name, createdAt)

fun InspectorIssueEntity.toDomain() = InspectorIssue(id, reviewId, Severity.valueOf(severity), description, requiredAction, resolved, createdAt, resolvedAt)
fun InspectorIssue.toEntity() = InspectorIssueEntity(id, reviewId, severity.name, description, requiredAction, resolved, createdAt, resolvedAt)

fun MissionArtifactEntity.toDomain() = MissionArtifact(id, missionId, ArtifactType.valueOf(type), name, path, contentHash, sizeBytes, mimeType, version, createdAt, updatedAt)
fun MissionArtifact.toEntity() = MissionArtifactEntity(id, missionId, type.name, name, path, contentHash, sizeBytes, mimeType, version, createdAt, updatedAt)

fun MissionEventEntity.toDomain() = MissionEvent(id, missionId, MissionEventType.valueOf(type), message, relatedTaskId, relatedMessageId, createdAt)
fun MissionEvent.toEntity() = MissionEventEntity(id, missionId, type.name, message, relatedTaskId, relatedMessageId, createdAt)

fun ProviderConfigEntity.toDomain() = ProviderConfig(id, ProviderId.valueOf(providerId), enabled, freeOnly, defaultModelId, createdAt, updatedAt)
fun ProviderConfig.toEntity() = ProviderConfigEntity(id, providerId.name, enabled, freeOnly, defaultModelId, createdAt, updatedAt)

fun ChatSessionEntity.toDomain() = com.agentflow.domain.chat.ChatSession(
    id, projectId, agentId, title,
    com.agentflow.domain.chat.ChatSessionStatus.valueOf(status),
    createdAt, updatedAt, lastMessageAt,
)
fun com.agentflow.domain.chat.ChatSession.toEntity() = ChatSessionEntity(
    id, projectId, agentId, title, status.name, createdAt, updatedAt, lastMessageAt,
)

fun ChatMessageEntity.toDomain() = com.agentflow.domain.chat.ChatMessage(
    id = id,
    sessionId = sessionId,
    role = com.agentflow.domain.chat.ChatRole.valueOf(role),
    content = content,
    status = com.agentflow.domain.chat.ChatMessageStatus.valueOf(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    provider = provider?.let { com.agentflow.domain.provider.ProviderType.valueOf(it) },
    model = model,
    latencyMs = latencyMs,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    errorCode = errorCode,
    generationGroupId = generationGroupId,
    bookmarked = bookmarked,
)
fun com.agentflow.domain.chat.ChatMessage.toEntity() = ChatMessageEntity(
    id, sessionId, role.name, content, status.name, createdAt, updatedAt,
    provider?.name, model, latencyMs, inputTokens, outputTokens, errorCode, generationGroupId, bookmarked,
)

fun ChatAttachmentEntity.toDomain() = com.agentflow.domain.chat.ChatAttachment(id, messageId, name, mimeType, sizeBytes, path)
fun com.agentflow.domain.chat.ChatAttachment.toEntity() = ChatAttachmentEntity(id, messageId, name, mimeType, sizeBytes, path)
